package fr.gaulupeau.apps.Poche.tts;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;

/**
 * Text To Speech (TTS) Service.
 *
 * Need a {@link TextInterface} to access the text to speech.
 *
 * Tried to follow the recommendations from this video:
 *      Media playback the right way (Big Android BBQ 2015)
 *      https://www.youtube.com/watch?v=XQwe30cZffg
 */
public class TtsService
        extends Service
        implements  AudioManager.OnAudioFocusChangeListener,
                    TextToSpeech.OnInitListener
{
    enum State {

        /**
         *  CREATED State.
         *  Initialize everything, but do not get audio focus or activate audio session yet.
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

        public final int playbackState;
        State(int playbackState) {
            this.playbackState = playbackState;
        }
    }

    private volatile State state;
    private volatile boolean isTTSInitialized;
    private volatile boolean isAudioFocusGranted;
    private volatile boolean isVisible;
    private volatile TextInterface textInterface;
    private String metaDataArtist = "";
    private String metaDataTitle = "";
    private String metaDataAlbum = "";
    private volatile String utteranceId = "1";
    private HashMap  utteranceParams = new HashMap();
    private boolean playFromStart;



    private TextToSpeech tts;
    private String ttsEngine;
    private String ttsVoice;
    private AudioManager audioManager;
    private MediaSessionCompat mediaSession;
    private MediaPlayer mediaPlayerPageFlip;
    private BroadcastReceiver noisyReceiver;
    private Settings settings;
    private float speed = 1.0f;
    private float pitch = 1.0f;
    private ExecutorService executor;
    private final Runnable ttsStop = new Runnable() {
        @Override
        public void run() {
            tts.stop();
        }
    };
    private final Runnable speak = new Runnable() {
        @Override
        public void run() {
            speak();
        }
    };
    private PendingIntent notificationPendingIntent;

    private static final String LOG_TAG="TtsService";
    private static final int TTS_SPEAK_QUEUE_SIZE = 2;


    private static volatile TtsService instance;
    public static TtsService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate");
        TtsService.instance = this;
        isTTSInitialized = false;
        state = State.CREATED;
        executor = Executors.newSingleThreadExecutor();
        settings = App.getInstance().getSettings();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mediaPlayerPageFlip = MediaPlayer.create(getApplicationContext(), R.raw.page_flip);
        ComponentName mediaButtonReceiverComponentName = new ComponentName(getPackageName(),
                MediaButtonReceiver.class.getName());
        mediaSession = new MediaSessionCompat(this, "Wallabag TTS", mediaButtonReceiverComponentName, null);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
                Log.d(LOG_TAG, "MediaSessionCompat.Callback.onMediaButtonEvent " + mediaButtonIntent);
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
        ttsVoice = this.settings.getString(Settings.TTS_VOICE, "");
        ttsEngine = this.settings.getString(Settings.TTS_ENGINE, "");
        if (ttsEngine.equals("") || Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            this.tts = new TextToSpeech(getApplicationContext(), this);
            this.ttsEngine = tts.getDefaultEngine();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) { // check done again to avoid error notification in Android Studio
                this.tts = new TextToSpeech(getApplicationContext(), this, ttsEngine);
            }
        }
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                pauseCmd();
            }
        };
        setMediaSessionPlaybackState();
        setForegroundAndNotification();
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        switch (state) {
            case CREATED:
            case PAUSED:
                // Do nothing
                break;
            case PLAYING:
                unregisterReceiver(noisyReceiver);
                break;
            case WANT_TO_PLAY:
            case STOPPED:
            case ERROR:
                // Do nothing
                break;
        }
        state = State.STOPPED; // needed before tts.stop() because of the onSpeakDone callback.
        isVisible = false;
        setMediaSessionPlaybackState();
        setForegroundAndNotification();
        mediaSession.setActive(false);
        audioManager.abandonAudioFocus(this);
        executor.shutdown();
        executor = null;
        tts.stop();
        mediaSession.release();
        mediaSession = null;
        mediaPlayerPageFlip.release();
        mediaPlayerPageFlip = null;
        tts.shutdown();
        tts = null;
        isTTSInitialized = false;
        audioManager.abandonAudioFocus(this);
        isAudioFocusGranted = false;
        instance = null;
        state = State.STOPPED;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "onStartCommand");
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent);
            String action = intent.getAction();
            if ("PLAY".equals(action)) {
                playCmd();
            } else if ("PAUSE".equals(action)) {
                pauseCmd();
            } else if ("PLAY_PAUSE".equals(action)) {
                playPauseCmd();
            } else if ("REWIND".equals(action)) {
                rewindCmd();
            } else if ("FAST_FORWARD".equals(action)) {
                fastForwardCmd();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //Log.d(LOG_TAG, "onBind");
        return new LocalBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        //Log.d(LOG_TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    public class LocalBinder extends Binder {
        public TtsService getService() {
            return TtsService.this;
        }
    }

    public void playFromStartCmd() {
        playFromStart = true;
        playCmd();
    }

    private void playCmd() {
        Log.d(LOG_TAG, "playCmd");
        switch (state) {
            case CREATED:
                if (audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                        == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                {
                    isAudioFocusGranted = true;
                    mediaSession.setActive(true);
                    setMediaSessionMetaData();
                    //NO BREAK, continue to next statements as if is state == WANT_TO_PLAY
                } else {
                    // Stay in the state CREATED
                    break;
                }
            case WANT_TO_PLAY:
            case PAUSED:
                if (isTTSInitialized && isAudioFocusGranted && (textInterface != null))
                {
                    state = State.PLAYING;
                    if (playFromStart) {
                        playFromStart = false;
                        textInterface.restoreFromStart();
                    } else {
                        textInterface.restoreCurrent();
                    }
                    setMediaSessionPlaybackState();
                    setForegroundAndNotification();
                    registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                    executor.execute(speak); // speak();
                } else {
                    state = State.WANT_TO_PLAY;
                    setMediaSessionPlaybackState();
                    setForegroundAndNotification();
                }
                break;
            case PLAYING:
            case STOPPED:
            case ERROR:
                // Do nothing
                break;
        }
    }

    public void pauseCmd() {
        pauseCmd(State.PAUSED);
    }

    private void pauseCmd(State newState) {
        Log.d(LOG_TAG, "pauseCmd " + newState);
        switch (state) {
            case PLAYING:
                state = newState; // needed before tts.stop() because of the onSpeakDone callback.
                executor.execute(ttsStop); // tts.stop();
                unregisterReceiver(noisyReceiver);
                //NO BREAK, continue to next statements to set state and notification
            case WANT_TO_PLAY:
            case PAUSED:
                state = newState;
                setMediaSessionPlaybackState();
                setForegroundAndNotification();
                stopForeground(false);
                break;
            case CREATED:
            case STOPPED:
            case ERROR:
                // Do nothing
                break;
        }
    }

    public void playPauseCmd() {
        Log.d(LOG_TAG, "playPauseCmd");
        switch (state) {
            case CREATED:
            case PAUSED:
                playCmd();
                break;
            case PLAYING:
            case WANT_TO_PLAY:
                pauseCmd();
                break;
            case STOPPED:
            case ERROR:
                // Do nothing
                break;
        }
    }


    public void rewindCmd() {
        Log.d(LOG_TAG, "rewindCmd");
        if (textInterface != null) {
            if (textInterface.rewind() && (state == State.PLAYING)) {
                executor.execute(speak); // speak();
            }
        }
    }

    public void fastForwardCmd() {
        Log.d(LOG_TAG, "fastForwardCmd");
        if (textInterface != null) {
            if (textInterface.fastForward() && (state == State.PLAYING)) {
                executor.execute(speak); // speak();
            }
        }
    }

    private void stopCmd() {
        Log.d(LOG_TAG, "stopCmd");
        stopSelf();
    }

    private void skipToNextCmd() {
        if (this.textInterface != null) {
            this.textInterface.skipToNext();
        }
    }

    private void skipToPreviousCmd() {
        if (this.textInterface != null) {
            this.textInterface.skipToPrevious();
        }
    }

    private void speak() {
        //Log.d(LOG_TAG, "speak");
        assert(state == State.PLAYING);
        assert(textInterface != null);
        // Change the utteranceId so that call to onSpeakDone
        // of the previous speak sequence will not interfere with the following one
        utteranceId = "" + (Integer.parseInt(utteranceId) + 1);
        ttsSpeak(textInterface.getText(0), TextToSpeech.QUEUE_FLUSH, utteranceId);
        for(int i = 1; i<= TTS_SPEAK_QUEUE_SIZE; i++) {
            ttsSpeak(textInterface.getText(i), TextToSpeech.QUEUE_ADD, utteranceId);
        }
    }

    private void ttsSpeak(String text, int queue, String utteranceId)
    {
        assert(state == State.PLAYING);
        assert(isTTSInitialized);
        if (text != null) {
            HashMap params = this.utteranceParams;
            if ( ! utteranceId.equals(params.get("utteranceId"))) {
                params = new HashMap();
                params.put("utteranceId", utteranceId);
                this.utteranceParams = params;
            }
            //Log.d(LOG_TAG, "speak " + utteranceId + ": " + text);
            this.tts.speak(text, queue, params);
        }
    }

    private void onSpeakDone(String doneUtteranceId) {
        {
            //Log.d(LOG_TAG, "onSpeakDone " + doneUtteranceId);
            if ((state == State.PLAYING) && utteranceId.equals(doneUtteranceId)) {
                if (textInterface.next()) {
                    ttsSpeak(textInterface.getText(TTS_SPEAK_QUEUE_SIZE), TextToSpeech.QUEUE_ADD, doneUtteranceId);
                } else {
                    pauseCmd();
                }
            }
        }
    }

    public void playPageFlipSound()
    {
        this.mediaPlayerPageFlip.start();
    }

    /**
     * AudioManager.OnAudioFocusChangeListener
     */
    @Override
    public void onAudioFocusChange(final int focusChange) {
        Log.d(LOG_TAG, "onAudioFocusChange " + focusChange);
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
     */
    @Override
    public void onInit(int status)
    {
        Log.d(LOG_TAG, "TextToSpeech.OnInitListener.onInit " + status);
        if (status == TextToSpeech.SUCCESS)
        {
            if (state == State.ERROR) {
                // We finally obtained a success status !
                // (probably after initializing a new TextToSpeech with different engine)
                state =  State.CREATED;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                this.ttsEngine = tts.getDefaultEngine();
            }
            this.tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    onSpeakDone(utteranceId);
                }
            });
            tts.setLanguage(convertVoiceNameToLocale(ttsVoice));
            isTTSInitialized = true;
            tts.setSpeechRate(speed);
            tts.setPitch(pitch);
            if (state == State.WANT_TO_PLAY) {
                playCmd();
            }
        } else {
            isTTSInitialized = false;
            state = State.ERROR;
            setMediaSessionPlaybackState();
            setForegroundAndNotification();
        }
    }


    public void setSpeed(float speed) {
        if (speed != this.speed) {
            this.speed = speed;
            if (isTTSInitialized) {
                this.tts.setSpeechRate(speed);
                if (state == State.PLAYING) {
                    executor.execute(speak); // speak();
                }
            }
        }
    }
    public float getSpeed() {
        return speed;
    }

    public void setPitch(float pitch) {
        if (pitch != this.pitch) {
            this.pitch = pitch;
            if (isTTSInitialized) {
                this.tts.setPitch(pitch);
                if (state == State.PLAYING) {
                    executor.execute(speak); // speak();
                }
            }
        }
    }

    public float getPitch() {
        return pitch;
    }

    public void setEngineAndVoice(String engine, String voice) {
        Log.d(LOG_TAG, "setEngineAndVoice " + engine + " " + voice);
        boolean resetEngine = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if ( ! engine.equals(this.ttsEngine)) {
                this.ttsEngine = engine;
                resetEngine = true;
            }
        } else {
            this.ttsEngine = engine;
            if ( ! tts.getDefaultEngine().equals(this.ttsEngine)) {
                resetEngine = true;
            }
        }
        if (resetEngine) {
            if (tts != null) {
                if (state == State.PLAYING) {
                    pauseCmd(State.WANT_TO_PLAY);
                }
                isTTSInitialized = false;
                final TextToSpeech ttsToShutdown = tts;
                new Thread() {
                    @Override
                    public void run() {
                        ttsToShutdown.setOnUtteranceCompletedListener(null);
                        ttsToShutdown.stop();
                        ttsToShutdown.shutdown();
                    }
                }.start();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    tts = new TextToSpeech(getApplicationContext(), this, engine);
                } else {
                    tts = new TextToSpeech(getApplicationContext(), this);
                }
            }
        }
        if ( ! voice.equals(this.ttsVoice)) {
            this.ttsVoice = voice;
            if (isTTSInitialized) {
                tts.setLanguage(convertVoiceNameToLocale(voice));
                if (state == State.PLAYING) {
                    executor.execute(speak); // speak();
                }
            }
        }
    }

    public String getEngine() {
        return this.ttsEngine;
    }

    public String getVoice() {
        return this.ttsVoice;
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


    public void setVisible(boolean visible, PendingIntent pendingIntent) {
        this.notificationPendingIntent = pendingIntent;
        this.isVisible = visible;
        setForegroundAndNotification();
    }

    public TextInterface getTextInterface() {
        return textInterface;
    }

    public void setTextInterface(TextInterface textInterface, String artist, String title, String album) {
        this.metaDataArtist = artist;
        this.metaDataTitle = title;
        this.metaDataAlbum = album;
        if (textInterface != this.textInterface) {
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
    }

    public void registerCallback(MediaControllerCompat.Callback callback, Handler handler) {
        this.mediaSession.getController().registerCallback(callback, handler);
    }

    public void unregisterCallback(MediaControllerCompat.Callback callback) {
        this.mediaSession.getController().unregisterCallback(callback);
    }

    private void setMediaSessionMetaData() {
        if (mediaSession == null) {
            return;
        }
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metaDataArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, metaDataAlbum)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metaDataTitle)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        BitmapFactory.decodeResource(getResources(), R.drawable.icon));
        if (textInterface != null) {
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, textInterface.getTotalDuration());
        }
        mediaSession.setMetadata(builder.build());
    }


    private void setMediaSessionPlaybackState() {
        if (mediaSession == null) {
            return;
        }
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (textInterface != null) {
            position = textInterface.getTime();
        }
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(
                        (this.state == State.PAUSED ?
                                PlaybackStateCompat.ACTION_PLAY : PlaybackStateCompat.ACTION_PAUSE)
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_REWIND
                        | PlaybackStateCompat.ACTION_FAST_FORWARD
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackStateCompat.ACTION_STOP
                )
                .setState(this.state.playbackState,
                        position,
                        speed,
                        SystemClock.elapsedRealtime())
                .build();
        mediaSession.setPlaybackState(playbackState);
    }


    private void setForegroundAndNotification() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        if (isVisible) {
            switch (state) {
                case CREATED:
                    break;
                case WANT_TO_PLAY:
                case PLAYING:
                    startForeground(1, generateNotification());
                    break;
                case PAUSED:
                    stopForeground(false);
                    notificationManager.notify(1, generateNotification());
                    break;
                case STOPPED:
                case ERROR:
                    stopForeground(true);
                    notificationManager.cancel(1);
                    break;
            }
        } else {
            stopForeground(true);
            notificationManager.cancel(1);
        }
    }

    private Notification generateNotification() {
        NotificationCompat.Builder builder = generateNotificationBuilderFrom(getApplicationContext(), mediaSession);
        builder.setContentIntent(notificationPendingIntent);
        builder.setWhen(0);
        builder.setSmallIcon(R.drawable.icon);
        if (state != State.ERROR) {
            //builder.addAction( generateAction( android.R.drawable.ic_media_previous, "Previous", KeyEvent.KEYCODE_MEDIA_PREVIOUS ) );
            builder.addAction(generateAction(android.R.drawable.ic_media_rew, "Rewind", KeyEvent.KEYCODE_MEDIA_REWIND));
            if ((state == State.WANT_TO_PLAY) || (state == State.PLAYING)) {
                builder.addAction(generateAction(android.R.drawable.ic_media_pause, "Pause", KeyEvent.KEYCODE_MEDIA_PAUSE));
            } else {
                builder.addAction(generateAction(android.R.drawable.ic_media_play, "Play", KeyEvent.KEYCODE_MEDIA_PLAY));
            }
            builder.addAction(generateAction(android.R.drawable.ic_media_ff, "Fast Forward", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD));
            builder.addAction(generateAction(android.R.drawable.ic_media_next, "Next", KeyEvent.KEYCODE_MEDIA_NEXT));
            builder.setStyle(new NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2, 3)
                    //FIXME: max number of setShowActionsInCompactView depends on Android VERSION
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(generateActionIntent(getApplicationContext(), KeyEvent.KEYCODE_MEDIA_STOP)));

        }
        return builder.build();
    }

    private static NotificationCompat.Builder generateNotificationBuilderFrom(Context context, MediaSessionCompat mediaSession) {
        MediaControllerCompat controller = mediaSession.getController();
        MediaMetadataCompat mediaMetadata = controller.getMetadata();
        MediaDescriptionCompat description = mediaMetadata.getDescription();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setSubText(description.getDescription())
                .setLargeIcon(description.getIconBitmap())
                .setContentIntent(controller.getSessionActivity()) // always null...
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(generateActionIntent(context, KeyEvent.KEYCODE_MEDIA_STOP));
        return builder;
    }

    private NotificationCompat.Action generateAction( int icon, String title, int mediaKeyEvent) {
        PendingIntent pendingIntent = generateActionIntent(getApplicationContext(), mediaKeyEvent);
        return new NotificationCompat.Action.Builder( icon, title, pendingIntent ).build();
    }


    private static PendingIntent generateActionIntent(Context context, int mediaKeyEvent)
    {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Intent.EXTRA_KEY_EVENT,
                new KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyEvent));
        return PendingIntent.getBroadcast(context, mediaKeyEvent, intent, 0);
    }
}
