package fr.gaulupeau.apps.Poche.tts;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.ui.NotificationsHelper;

/**
 * Text To Speech (TTS) Service.
 * <p>
 * Needs a {@link TextInterface} to access the text to speech.
 * <p>
 * Tried to follow the recommendations from this video:
 * Media playback the right way (Big Android BBQ 2015)
 * https://www.youtube.com/watch?v=XQwe30cZffg
 */
public class TtsService extends Service {

    public class LocalBinder extends Binder {
        public TtsService getService() {
            return TtsService.this;
        }
    }

    enum State {

        /**
         * CREATED State.
         * Initialize everything, but do not get audio focus or activate audio session yet.
         */
        CREATED(PlaybackStateCompat.STATE_NONE),

        /**
         * WANT_TO_PLAY State.
         * Not yet ready to play (tts not initialized, document not parsed, or audio focus lost).
         * get audio focus and activate audio session.
         */
        WANT_TO_PLAY(PlaybackStateCompat.STATE_BUFFERING),

        /**
         * PLAYING State.
         */
        PLAYING(PlaybackStateCompat.STATE_PLAYING),

        /**
         * PAUSED State.
         */
        PAUSED(PlaybackStateCompat.STATE_PAUSED),

        /**
         * STOPPED State.
         * Abandon audio focus and deactivate audio session.
         * => destroy service
         */
        STOPPED(PlaybackStateCompat.STATE_STOPPED),

        /**
         * ERROR State.
         * Like TTS initialization failure.
         */
        ERROR(PlaybackStateCompat.STATE_ERROR);

        final int playbackState;

        State(int playbackState) {
            this.playbackState = playbackState;
        }

    }

    public static final String ACTION_PLAY = "PLAY";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_PLAY_PAUSE = "PLAY_PAUSE";
    public static final String ACTION_REWIND = "REWIND";
    public static final String ACTION_FAST_FORWARD = "FAST_FORWARD";

    private static final String TAG = TtsService.class.getSimpleName();

    private static final int NOTIFICATION_ID = 1;

    private static final int TTS_SPEAK_QUEUE_SIZE = 2;

    private ExecutorService executor;
    private AudioManager audioManager;
    private MediaPlayer mediaPlayerPageFlip;
    private MediaSessionCompat mediaSession;
    private BroadcastReceiver noisyReceiver;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    private TtsConverter ttsConverter;

    private Handler mainThreadHandler;

    private volatile State state;

    private boolean autoplayNext;
    private boolean playFromStart;

    private String ttsEngine;
    private String ttsVoice;
    private float speed = 1.0f;
    private float pitch = 1.0f;

    private volatile TextToSpeech tts; // TODO: fix: access is not properly synchronized
    private AudioFocusRequest audioFocusRequest;

    private volatile boolean isTtsInitialized;
    private boolean isAudioFocusGranted;

    private volatile TextInterface textInterface;
    private String metaDataArtist = "";
    private String metaDataTitle = "";
    private String metaDataAlbum = "";

    private boolean isForeground;
    private PendingIntent notificationPendingIntent;

    private volatile int utteranceId = 1;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");

        isTtsInitialized = false;
        state = State.CREATED;

        executor = Executors.newSingleThreadExecutor();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mediaPlayerPageFlip = MediaPlayer.create(getApplicationContext(), R.raw.page_flip);

        ComponentName mediaButtonReceiverComponentName = new ComponentName(getPackageName(),
                MediaButtonReceiver.class.getName());
        mediaSession = new MediaSessionCompat(this, "wallabag TTS",
                mediaButtonReceiverComponentName, null);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
                Log.d(TAG, "MediaSessionCompat.Callback.onMediaButtonEvent() "
                        + mediaButtonIntent);
                return super.onMediaButtonEvent(mediaButtonIntent);
            }

            @Override
            public void onPlay() {
                playCmd();
            }

            @Override
            public void onPause() {
                pauseCmd();
            }

            @Override
            public void onRewind() {
                rewindCmd();
            }

            @Override
            public void onFastForward() {
                fastForwardCmd();
            }

            @Override
            public void onSkipToNext() {
                skipToNextCmd();
            }

            @Override
            public void onSkipToPrevious() {
                skipToPreviousCmd();
            }

            @Override
            public void onStop() {
                stopCmd();
            }
        });

        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "noisyReceiver.onReceive()");
                pauseCmd();
            }
        };

        audioFocusChangeListener = this::onAudioFocusChange;

        ttsConverter = new TtsConverter(App.getInstance());

        mainThreadHandler = new Handler();

        Settings settings = App.getInstance().getSettings();
        ttsEngine = settings.getTtsEngine();
        ttsVoice = settings.getTtsVoice();

        if (ttsEngine.equals("")) {
            tts = new TextToSpeech(getApplicationContext(), this::onTtsInitListener);
            ttsEngine = tts.getDefaultEngine();
        } else {
            tts = new TextToSpeech(getApplicationContext(), this::onTtsInitListener, ttsEngine);
        }

        setMediaSessionPlaybackState();
        setForegroundAndNotification();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");

        if (state == State.PLAYING) {
            unregisterReceiver(noisyReceiver);
        }

        state = State.STOPPED; // needed before tts.stop() because of the onSpeakDone callback.
        isForeground = false;
        setMediaSessionPlaybackState();
        setForegroundAndNotification();
        mediaSession.setActive(false);
        abandonAudioFocus();
        executor.shutdown();
        executor = null;
        tts.stop();
        mediaSession.release();
        mediaSession = null;
        mediaPlayerPageFlip.release();
        mediaPlayerPageFlip = null;
        isTtsInitialized = false;
        tts.shutdown();
        tts = null;
        abandonAudioFocus();
        isAudioFocusGranted = false;
        state = State.STOPPED;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");

        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent);

            String action = intent.getAction();
            if (ACTION_PLAY.equals(action)) {
                playCmd();
            } else if (ACTION_PAUSE.equals(action)) {
                pauseCmd();
            } else if (ACTION_PLAY_PAUSE.equals(action)) {
                playPauseCmd();
            } else if (ACTION_REWIND.equals(action)) {
                rewindCmd();
            } else if (ACTION_FAST_FORWARD.equals(action)) {
                fastForwardCmd();
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public void autoplayNext() {
        autoplayNext = true;
    }

    public void playNextFromStart() {
        playFromStart = true;
    }

    private void playCmd() {
        Log.d(TAG, "playCmd()");

        switch (state) {
            case CREATED:
                if (requestAudioFocus() != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.w(TAG, "playCmd() audio focus is not granted; staying in CREATED");
                    break; // Stay in the state CREATED
                }
                isAudioFocusGranted = true;
                mediaSession.setActive(true);
                setMediaSessionMetaData();
                // NO BREAK, continue to next statements as if is state == WANT_TO_PLAY

            case WANT_TO_PLAY:
            case PAUSED:
                if (isTtsInitialized && isAudioFocusGranted && textInterface != null) {
                    state = State.PLAYING;
                    if (playFromStart) {
                        playFromStart = false;
                        textInterface.restoreFromStart();
                    } else {
                        textInterface.restoreCurrent();
                    }
                    setMediaSessionPlaybackState();
                    setForegroundAndNotification();
                    registerReceiver(noisyReceiver,
                            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                    executeSpeak();
                } else {
                    state = State.WANT_TO_PLAY;
                    setMediaSessionPlaybackState();
                    setForegroundAndNotification();
                }
                break;
        }
    }

    private int requestAudioFocus() {
        int audioFocusResult;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            audioFocusResult = audioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } else {
            if (audioFocusRequest == null) {
                audioFocusRequest =
                        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                                .setWillPauseWhenDucked(true)
                                .setAudioAttributes(
                                        new AudioAttributes.Builder()
                                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                .build()
                                )
                                .build();
            }
            audioFocusResult = audioManager.requestAudioFocus(audioFocusRequest);
        }
        return audioFocusResult;
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        } else {
            if (audioFocusRequest != null && audioManager.abandonAudioFocusRequest(audioFocusRequest)
                    != AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                audioFocusRequest = null;
            }
        }
    }

    public void pauseCmd() {
        pauseCmd(State.PAUSED);
    }

    private void pauseCmd(State newState) {
        Log.d(TAG, "pauseCmd() " + newState);

        switch (state) {
            case PLAYING:
                state = newState; // needed before tts.stop() because of the onSpeakDone callback.
                if (textInterface != null) textInterface.storeCurrent();
                executeOnBackgroundThread(tts::stop);
                unregisterReceiver(noisyReceiver);
                // NO BREAK, continue to next statements to set state and notification

            case WANT_TO_PLAY:
            case PAUSED:
                state = newState;
                setMediaSessionPlaybackState();
                setForegroundAndNotification();
                stopForeground(false);
                break;
        }
    }

    public void playPauseCmd() {
        Log.d(TAG, "playPauseCmd()");

        switch (state) {
            case CREATED:
            case PAUSED:
                playCmd();
                break;

            case PLAYING:
            case WANT_TO_PLAY:
                pauseCmd();
                break;
        }
    }

    public void rewindCmd() {
        Log.d(TAG, "rewindCmd()");

        if (textInterface != null) {
            if (textInterface.rewind() && state == State.PLAYING) {
                executeSpeak();
            }
        }
    }

    public void fastForwardCmd() {
        Log.d(TAG, "fastForwardCmd()");

        if (textInterface != null) {
            if (textInterface.fastForward() && state == State.PLAYING) {
                executeSpeak();
            }
        }
    }

    private void stopCmd() {
        Log.d(TAG, "stopCmd()");

        stopSelf();
    }

    private void skipToNextCmd() {
        if (textInterface != null) {
            textInterface.skipToNext();
        }
    }

    private void skipToPreviousCmd() {
        if (textInterface != null) {
            textInterface.skipToPrevious();
        }
    }

    private void executeOnMainThread(Runnable runnable) {
        mainThreadHandler.post(runnable);
    }

    private void executeOnBackgroundThread(Runnable runnable) {
        executor.execute(runnable);
    }

    private void executeSpeak() {
        // tts.speak(...) is supposed to be asynchronous, so I'm not sure executor is required
        executeOnBackgroundThread(this::speak);
    }

    /**
     * Thread-safety note: executed in a background thread: {@link TtsService#executor}.
     */
    private void speak() {
        Log.d(TAG, "speak()");

        // a local variable so it's not gets replaced in another thread
        TextInterface textInterface = this.textInterface;

        if (state != State.PLAYING || textInterface == null) {
            Log.w(TAG, "speak() state=" + state + ", textInterface=" + textInterface);
            return;
        }

        // Change the utteranceId so that call to onSpeakDone
        // of the previous speak sequence will not interfere with the following one
        utteranceId++;

        String utteranceString = String.valueOf(utteranceId);
        ttsSpeak(getConvertedText(0), TextToSpeech.QUEUE_FLUSH, utteranceString);
        for (int i = 1; i <= TTS_SPEAK_QUEUE_SIZE; i++) {
            ttsSpeak(getConvertedText(i), TextToSpeech.QUEUE_ADD, utteranceString);
        }
    }

    /**
     * Thread-safety note: executed in a background thread: {@link TtsService#executor}.
     */
    private void ttsEnqueueMoreIfUtteranceMatches(String utteranceId) {
        if (!String.valueOf(this.utteranceId).equals(utteranceId)) {
            Log.d(TAG, "ttsSpeakMoreIfUtteranceMatches() utteranceId didn't match");
            return;
        }

        TextInterface textInterface = this.textInterface;

        if (textInterface == null) {
            Log.w(TAG, "ttsEnqueueMoreIfUtteranceMatches() textInterface is null");
            return;
        }

        ttsSpeak(getConvertedText(TTS_SPEAK_QUEUE_SIZE), TextToSpeech.QUEUE_ADD, utteranceId);
    }

    /**
     * Thread-safety note: executed in a background thread: {@link TtsService#executor}.
     */
    private CharSequence getConvertedText(int relativeIndex) {
        GenericItem item = textInterface.getItem(relativeIndex);
        if (item == null) return null;

        CharSequence result = ttsConverter.convert(item);

        if (result == null) {
            // always return something so `onSpeakDone` is called the same amount of times
            // as `tts.speak()`
            result = "";
        }

        return result;
    }

    /**
     * Thread-safety note: executed in a background thread: {@link TtsService#executor}.
     */
    private void ttsSpeak(CharSequence text, int queueMode, String utteranceId) {
        if (state != State.PLAYING || !isTtsInitialized) {
            Log.w(TAG, "ttsSpeak() state=" + state + ", isTtsInitialized=" + isTtsInitialized);
            return;
        }

        if (text != null) {
            // TODO: check tts.getMaxSpeechInputLength()?

            Log.v(TAG, "ttsSpeak() speaking " + utteranceId + ": " + text);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(text, queueMode, null, utteranceId);
            } else {
                HashMap<String, String> params = new HashMap<>(2);
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                //noinspection deprecation
                tts.speak(text.toString(), queueMode, params);
            }

            Log.v(TAG, "ttsSpeak() call returned");
        }
    }

    /**
     * Thread-safety note: may be executed from multiple background threads
     * ({@link UtteranceProgressListener}).
     */
    private void onSpeakDoneListener(String doneUtteranceId) {
        Log.v(TAG, "onSpeakDoneListener() " + doneUtteranceId);

        executeOnMainThread(() -> onSpeakDone(doneUtteranceId));
    }

    private void onSpeakDone(String doneUtteranceId) {
        Log.v(TAG, "onSpeakDone() " + doneUtteranceId);

        if (state == State.PLAYING && String.valueOf(utteranceId).equals(doneUtteranceId)) {
            if (textInterface.next()) {
                executeOnBackgroundThread(() -> ttsEnqueueMoreIfUtteranceMatches(doneUtteranceId));
            } else {
                pauseCmd();
            }
        }
    }

    public void playPageFlipSound() {
        mediaPlayerPageFlip.start();
    }

    // AudioManager.OnAudioFocusChangeListener
    private void onAudioFocusChange(int focusChange) {
        Log.d(TAG, "onAudioFocusChange() " + focusChange);

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                isAudioFocusGranted = true;
                if (state == State.WANT_TO_PLAY) {
                    playCmd();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                isAudioFocusGranted = false;
                if (state == State.PLAYING || state == State.WANT_TO_PLAY) {
                    pauseCmd();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                isAudioFocusGranted = false;
                if (state == State.PLAYING) {
                    pauseCmd(State.WANT_TO_PLAY);
                }
                break;
        }
    }

    /**
     * TextToSpeech.OnInitListener.
     * Thread-safety note: apparently executed on the main thread, but I'm not sure.
     */
    private void onTtsInitListener(int status) {
        // better safe than sorry
        executeOnMainThread(() -> onTtsInit(status));
    }

    private void onTtsInit(int status) {
        Log.d(TAG, "onTtsInit() " + status);

        if (status == TextToSpeech.SUCCESS) {
            if (state == State.ERROR) {
                // We finally obtained a success status !
                // (probably after initializing a new TextToSpeech with different engine)
                state = State.CREATED;
            }

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {}

                @Override
                public synchronized void onDone(String utteranceId) {
                    onSpeakDoneListener(utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    Log.w(TAG, "utteranceProgressListener.onError() " + utteranceId);
                    onSpeakDoneListener(utteranceId);
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    Log.d(TAG, "utteranceProgressListener.onStop() " + utteranceId
                            + ", " + interrupted);
                    onSpeakDoneListener(utteranceId);
                }
            });

            tts.setLanguage(convertVoiceNameToLocale(ttsVoice));

            isTtsInitialized = true;

            tts.setSpeechRate(speed);
            tts.setPitch(pitch);

            if (state == State.WANT_TO_PLAY) {
                playCmd();
            }
        } else {
            isTtsInitialized = false;
            state = State.ERROR;
            setMediaSessionPlaybackState();
            setForegroundAndNotification();
        }
    }

    public void setSpeed(float speed) {
        if (speed != this.speed) {
            this.speed = speed;
            if (isTtsInitialized) {
                tts.setSpeechRate(speed);
                if (state == State.PLAYING) {
                    executeSpeak();
                }
            }
        }
    }

    public void setPitch(float pitch) {
        if (pitch != this.pitch) {
            this.pitch = pitch;
            if (isTtsInitialized) {
                tts.setPitch(pitch);
                if (state == State.PLAYING) {
                    executeSpeak();
                }
            }
        }
    }

    public void setEngineAndVoice(String engine, String voice) {
        Log.d(TAG, "setEngineAndVoice() " + engine + " " + voice);

        boolean resetEngine = false;
        if (!engine.equals(ttsEngine)) {
            ttsEngine = engine;
            resetEngine = true;
        }
        if (resetEngine) {
            if (tts != null) {
                if (state == State.PLAYING) {
                    pauseCmd(State.WANT_TO_PLAY);
                }
                isTtsInitialized = false;

                final TextToSpeech ttsToShutdown = tts;
                new Thread(() -> {
                    ttsToShutdown.setOnUtteranceProgressListener(null);
                    ttsToShutdown.stop();
                    ttsToShutdown.shutdown();
                }).start();

                tts = new TextToSpeech(getApplicationContext(), this::onTtsInitListener, engine);
            }
        }
        if (!voice.equals(ttsVoice)) {
            ttsVoice = voice;
            if (isTtsInitialized) {
                tts.setLanguage(convertVoiceNameToLocale(voice));
                if (state == State.PLAYING) {
                    executeSpeak();
                }
            }
        }
    }

    private Locale convertVoiceNameToLocale(String voiceName) {
        String language;
        String country = "";
        String variant = "";
        int separatorIndex = voiceName.indexOf("-");
        if (separatorIndex >= 0) {
            language = voiceName.substring(0, separatorIndex);
            country = voiceName.substring(separatorIndex + 1);
            separatorIndex = country.indexOf("-");
            if (separatorIndex >= 0) {
                variant = country.substring(separatorIndex + 1);
                country = country.substring(0, separatorIndex);
            }
        } else {
            language = voiceName;
        }
        return new Locale(language, country, variant);
    }

    public void setForeground(boolean foreground, PendingIntent pendingIntent) {
        isForeground = foreground;
        notificationPendingIntent = pendingIntent;
        setForegroundAndNotification();
    }

    public void setTextInterface(TextInterface textInterface, String artist,
                                 String title, String album) {
        Log.d(TAG, "setTextInterface() textInterface is "
                + (textInterface == null ? "null" : "not null"));

        boolean newContent = false;

        metaDataArtist = artist;
        metaDataTitle = title;
        metaDataAlbum = album;
        if (textInterface != this.textInterface) {
            newContent = textInterface != null;

            switch (state) {
                case CREATED:
                case PAUSED:
                case ERROR:
                case STOPPED:
                    this.textInterface = textInterface;
                    break;

                case WANT_TO_PLAY:
                case PLAYING:
                    pauseCmd();
                    this.textInterface = textInterface;
                    playCmd();
                    break;
            }
        }
        setMediaSessionMetaData();

        if (newContent) {
            onNewContent();
        }
    }

    private void onNewContent() {
        if (autoplayNext) {
            autoplayNext = false;

            playCmd();
        }
    }

    public void registerMediaControllerCallback(MediaControllerCompat.Callback callback) {
        mediaSession.getController().registerCallback(callback);
    }

    public void unregisterMediaControllerCallback(MediaControllerCompat.Callback callback) {
        mediaSession.getController().unregisterCallback(callback);
    }

    private void setMediaSessionMetaData() {
        if (mediaSession == null) return;

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metaDataArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, metaDataAlbum)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metaDataTitle);

        if (textInterface != null) {
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                    textInterface.getTotalDuration());
        }

        mediaSession.setMetadata(builder.build());
    }

    private void setMediaSessionPlaybackState() {
        if (mediaSession == null) return;

        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (textInterface != null) {
            position = textInterface.getTime();
        }

        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(
                        (state == State.PAUSED ?
                                PlaybackStateCompat.ACTION_PLAY : PlaybackStateCompat.ACTION_PAUSE)
                                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                                | PlaybackStateCompat.ACTION_REWIND
                                | PlaybackStateCompat.ACTION_FAST_FORWARD
                                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                                | PlaybackStateCompat.ACTION_STOP
                )
                .setState(state.playbackState,
                        position,
                        speed)
                .build();

        mediaSession.setPlaybackState(playbackState);
    }

    private void setForegroundAndNotification() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat
                .from(getApplicationContext());

        if (isForeground) {
            switch (state) {
                case WANT_TO_PLAY:
                case PLAYING:
                    startForeground(NOTIFICATION_ID, generateNotification());
                    break;

                case PAUSED:
                    stopForeground(false);
                    notificationManager.notify(NOTIFICATION_ID, generateNotification());
                    break;

                case STOPPED:
                case ERROR:
                    stopForeground(true);
                    notificationManager.cancel(NOTIFICATION_ID);
                    break;
            }
        } else {
            stopForeground(true);
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private Notification generateNotification() {
        NotificationCompat.Builder builder = generateNotificationBuilderFrom(
                getApplicationContext(), mediaSession);

        builder.setContentIntent(notificationPendingIntent)
                .setShowWhen(false)
                .setSmallIcon(R.drawable.wallabag_silhouette);

        if (state != State.ERROR) {
            // TODO: localize action titles

//            builder.addAction(generateAction(android.R.drawable.ic_media_previous,
//                    "Previous", KeyEvent.KEYCODE_MEDIA_PREVIOUS));

            builder.addAction(generateAction(android.R.drawable.ic_media_rew,
                    "Rewind", KeyEvent.KEYCODE_MEDIA_REWIND));

            if (state == State.WANT_TO_PLAY || state == State.PLAYING) {
                builder.addAction(generateAction(android.R.drawable.ic_media_pause,
                        "Pause", KeyEvent.KEYCODE_MEDIA_PAUSE));
            } else {
                builder.addAction(generateAction(android.R.drawable.ic_media_play,
                        "Play", KeyEvent.KEYCODE_MEDIA_PLAY));
            }

            builder.addAction(generateAction(android.R.drawable.ic_media_ff,
                    "Fast Forward", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD));

            builder.addAction(generateAction(android.R.drawable.ic_media_next,
                    "Next", KeyEvent.KEYCODE_MEDIA_NEXT));

            builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(generateActionIntent(
                            getApplicationContext(), KeyEvent.KEYCODE_MEDIA_STOP)));
        }

        return builder.build();
    }

    private static NotificationCompat.Builder generateNotificationBuilderFrom(
            Context context, MediaSessionCompat mediaSession) {
        MediaControllerCompat controller = mediaSession.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat description = mediaMetadata.getDescription();
        return new NotificationCompat.Builder(context, NotificationsHelper.CHANNEL_ID_TTS)
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setSubText(description.getDescription())
                .setLargeIcon(description.getIconBitmap())
                .setContentIntent(controller.getSessionActivity()) // always null...
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(generateActionIntent(context, KeyEvent.KEYCODE_MEDIA_STOP));
    }

    private NotificationCompat.Action generateAction(int icon, String title, int mediaKeyEvent) {
        PendingIntent pendingIntent = generateActionIntent(getApplicationContext(), mediaKeyEvent);
        return new NotificationCompat.Action.Builder(icon, title, pendingIntent).build();
    }

    private static PendingIntent generateActionIntent(Context context, int mediaKeyEvent) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Intent.EXTRA_KEY_EVENT,
                new KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyEvent));
        return PendingIntent.getBroadcast(context, mediaKeyEvent, intent, 0);
    }

}
