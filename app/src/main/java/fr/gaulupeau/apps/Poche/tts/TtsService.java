package fr.gaulupeau.apps.Poche.tts;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.util.Pair;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;
import androidx.media.session.MediaButtonReceiver;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.ui.NotificationsHelper;

import static fr.gaulupeau.apps.Poche.utils.TextTools.equalOrEmpty;

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
         * Same as PAUSED, except no notification.
         */
        STOPPED(PlaybackStateCompat.STATE_STOPPED),

        /**
         * DESTROYED State.
         * Abandon audio focus and deactivate audio session.
         * => destroy service
         */
        DESTROYED(PlaybackStateCompat.STATE_STOPPED),

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

    private static class UtteranceIdToIndexMap {

        private final int[] utteranceIds = new int[TTS_SPEAK_QUEUE_SIZE];
        private final int[] indexes = new int[TTS_SPEAK_QUEUE_SIZE];
        private int utteranceMapIndex;

        private synchronized void addToUtteranceIndexMap(int utteranceId, int itemIndex) {
            utteranceIds[utteranceMapIndex] = utteranceId;
            indexes[utteranceMapIndex] = itemIndex;

            utteranceMapIndex++;

            if (utteranceMapIndex >= TTS_SPEAK_QUEUE_SIZE) utteranceMapIndex = 0;
        }

        private synchronized int getIndexByUtteranceId(int utteranceId) {
            for (int i = 0; i < TTS_SPEAK_QUEUE_SIZE; i++) {
                if (utteranceId == utteranceIds[i]) {
                    return indexes[i];
                }
            }

            return -1;
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

    private static final long ALWAYS_AVAILABLE_PLAYBACK_ACTIONS
            = PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_REWIND
            | PlaybackStateCompat.ACTION_FAST_FORWARD
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_STOP;

    private ComponentName mediaActionComponentName;
    private Settings settings;
    private ExecutorService executor;
    private AudioManager audioManager;
    private MediaPlayer fakeSound;
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
    private AudioFocusRequestCompat audioFocusRequest;

    private volatile boolean isTtsInitialized;
    private boolean isAudioFocusGranted;

    private boolean mediaSessionActive;

    private PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();

    private volatile TextInterface textInterface;
    private Integer articleId;
    private String metaDataArtist = "";
    private String metaDataTitle = "";
    private String metaDataAlbum = "";
    private String metaDataAlbumArtUrl;
    private String metaDataAlbumArtEffectiveUri;
    private Bitmap metaDataAlbumArt;

    private AlbumArtLoaderTask albumArtLoaderTask;

    private boolean isForeground;

    private volatile int utteranceId = 1;

    private final UtteranceIdToIndexMap utteranceIdToIndexMap = new UtteranceIdToIndexMap();

    private final Object currentItemStatsLock = new Object();
    private String currentUtteranceId;
    private int currentUtteranceFragmentStart;
    private int currentUtteranceFragmentEnd;
    private long currentUtteranceStartTimestamp;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");

        isTtsInitialized = false;
        state = State.CREATED;

        mediaActionComponentName = new ComponentName(this, MediaButtonReceiver.class);

        settings = App.getSettings();
        executor = Executors.newSingleThreadExecutor();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        audioFocusChangeListener = this::onAudioFocusChange;

        audioFocusRequest = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setWillPauseWhenDucked(true)
                .setAudioAttributes(new AudioAttributesCompat.Builder()
                        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build();

        fakeSound = MediaPlayer.create(getApplicationContext(), R.raw.silence);
        mediaPlayerPageFlip = MediaPlayer.create(getApplicationContext(), R.raw.page_flip);

        mediaSession = new MediaSessionCompat(this, "wallabag TTS");
        mediaSession.setMediaButtonReceiver(null); // do not restart inactive media session with media buttons
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
                if (settings.isTtsNextButtonIsFastForward()) {
                    fastForwardCmd();
                } else {
                    skipToNextCmd();
                }
            }

            @Override
            public void onSkipToPrevious() {
                if (settings.isTtsPreviousButtonIsRewind()) {
                    rewindCmd();
                } else {
                    skipToPreviousCmd();
                }
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

        ttsConverter = new TtsConverter(App.getInstance());

        mainThreadHandler = new Handler();

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

        state = State.DESTROYED; // needed before tts.stop() because of the onSpeakDone callback.
        setMediaSessionPlaybackState();
        setForegroundAndNotification();
        mediaSession.setActive(false);
        abandonAudioFocus();
        executor.shutdown();
        executor = null;
        tts.stop();
        mediaSession.release();
        mediaSession = null;
        fakeSound.release();
        fakeSound = null;
        mediaPlayerPageFlip.release();
        mediaPlayerPageFlip = null;
        isTtsInitialized = false;
        tts.shutdown();
        tts = null;
        abandonAudioFocus();
        state = State.DESTROYED;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() " + intent);

        if (intent != null) {
            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                // `MediaButtonReceiver` may use `Context.startForegroundService(Intent)`,
                // so we *have* to call `startForeground(...)`
                // or the app will crash during service shutdown

                setForegroundAndNotification(true);
            }

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
            case STOPPED:
            case PAUSED:
            case WANT_TO_PLAY:
                if (!mediaSessionActive) {
                    mediaSessionActive = true;
                    mediaSession.setActive(true);
                    setMediaSessionMetaData();
                }

                if (requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    isAudioFocusGranted = true;
                } else {
                    Log.i(TAG, "playCmd() audio focus is not granted");
                }

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
                    playFakeSoundToMakeMediaSessionActive();
                    executeSpeak();
                } else {
                    state = State.WANT_TO_PLAY;
                    setMediaSessionPlaybackState();
                    setForegroundAndNotification();
                }
                break;
        }
    }

    private void playFakeSoundToMakeMediaSessionActive() {
        // a sound needs to be played for Android to consider a MediaSession active
        // (apparently, using TTS doesn't count),
        // that is needed to receive media button events while in background
        fakeSound.start();
    }

    private int requestAudioFocus() {
        return AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest);
    }

    private void abandonAudioFocus() {
        if (AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
                == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.d(TAG, "Failed to abandon audio focus");
        }
        isAudioFocusGranted = false;
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
                // no break

            case WANT_TO_PLAY:
            case PAUSED:
            case STOPPED:
                if (state == State.STOPPED) {
                    Log.w(TAG, "pauseCmd() pause shouldn't be called in stopped state");
                }
                state = newState;
                setMediaSessionPlaybackState();
                setForegroundAndNotification();
                break;
        }
    }

    public void playPauseCmd() {
        Log.d(TAG, "playPauseCmd()");

        switch (state) {
            case CREATED:
            case STOPPED:
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
            long time = TimeUnit.SECONDS.toMillis(settings.getTtsRewindTime());

            Pair<Integer, Long> positionInCurrentItem = getPositionInCurrentItem();
            @SuppressWarnings("ConstantConditions")
            int index = positionInCurrentItem.first;
            @SuppressWarnings("ConstantConditions")
            long position = positionInCurrentItem.second;

            if (textInterface.rewind(time, index, position) && state == State.PLAYING) {
                executeSpeak();
                setMediaSessionPlaybackState();
            }
        }
    }

    public void fastForwardCmd() {
        Log.d(TAG, "fastForwardCmd()");

        if (textInterface != null) {
            long time = TimeUnit.SECONDS.toMillis(settings.getTtsFastForwardTime());

            Pair<Integer, Long> positionInCurrentItem = getPositionInCurrentItem();
            @SuppressWarnings("ConstantConditions")
            int index = positionInCurrentItem.first;
            @SuppressWarnings("ConstantConditions")
            long position = positionInCurrentItem.second;

            if (textInterface.fastForward(time, index, position) && state == State.PLAYING) {
                executeSpeak();
                setMediaSessionPlaybackState();
            }
        }
    }

    private void stopCmd() {
        Log.d(TAG, "stopCmd()");

        pauseCmd(State.STOPPED);

        abandonAudioFocus();
        mediaSession.setActive(false);
        mediaSessionActive = false;

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

    private void setCurrentItemProgress(String utteranceId, int start, int end) {
        synchronized (currentItemStatsLock) {
            currentUtteranceId = utteranceId;
            currentUtteranceFragmentStart = start;
            currentUtteranceFragmentEnd = end;
            currentUtteranceStartTimestamp = SystemClock.elapsedRealtime();
        }
    }

    private void resetCurrentItemProgress() {
        setCurrentItemProgress(null, -1, -1);
    }

    private Pair<Integer, Long> getPositionInCurrentItem() {
        String currentUtterance;
        int start, end;
        long timestamp;

        synchronized (currentItemStatsLock) {
            currentUtterance = currentUtteranceId;
            start = currentUtteranceFragmentStart;
            end = currentUtteranceFragmentEnd;
            timestamp = currentUtteranceStartTimestamp;
        }

        int currentIndex = currentUtterance != null
                ? utteranceIdToIndexMap.getIndexByUtteranceId(Integer.parseInt(currentUtterance))
                : -1;

        GenericItem item = null;
        if (textInterface != null) {
            item = textInterface.getItem(currentIndex);
        }

        if (item == null) return new Pair<>(-1, 0L);

        long timePositionInItem;
        if (start == -1 && end == -1) {
            // fallback for situations where TTS engine does not report detailed progress
            timePositionInItem = SystemClock.elapsedRealtime() - timestamp;
            Log.v(TAG, "getPositionInCurrentItem() using fallback");
        } else {
            int indexInItem = (start + end) / 2;
            timePositionInItem = ttsConverter.getTimePositionInItem(item, indexInItem);
        }
        return new Pair<>(currentIndex, timePositionInItem);
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

        // a local variable so it doesn't get replaced in another thread
        TextInterface textInterface = this.textInterface;

        if (state != State.PLAYING || textInterface == null) {
            Log.w(TAG, "speak() state=" + state + ", textInterface=" + textInterface);
            return;
        }

        // Change the utteranceId so that call to onSpeakDone
        // of the previous speak sequence will not interfere with the following one
        //noinspection NonAtomicOperationOnVolatileField: modified only in one thread
        utteranceId += TTS_SPEAK_QUEUE_SIZE + 1;

        int currentIndex = textInterface.getCurrentIndex();

        ttsSpeak(textInterface, currentIndex, 0, TextToSpeech.QUEUE_FLUSH, utteranceId);
        for (int i = 1; i <= TTS_SPEAK_QUEUE_SIZE; i++) {
            ttsSpeak(textInterface, currentIndex, i, TextToSpeech.QUEUE_ADD, utteranceId);
        }
    }

    /**
     * Thread-safety note: executed in a background thread: {@link TtsService#executor}.
     */
    private void ttsEnqueueMoreIfUtteranceMatches(String doneUtteranceId) {
        Log.v(TAG, "ttsEnqueueMoreIfUtteranceMatches() doneUtteranceId: " + doneUtteranceId);

        if (!String.valueOf(utteranceId).equals(doneUtteranceId)) {
            Log.d(TAG, "ttsSpeakMoreIfUtteranceMatches() doneUtteranceId didn't match");
            return;
        }

        TextInterface textInterface = this.textInterface;

        if (textInterface == null) {
            Log.w(TAG, "ttsEnqueueMoreIfUtteranceMatches() textInterface is null");
            return;
        }

        //noinspection NonAtomicOperationOnVolatileField: modified only in one thread
        utteranceId++;

        int currentIndex = textInterface.getCurrentIndex();

        ttsSpeak(textInterface, currentIndex, TTS_SPEAK_QUEUE_SIZE,
                TextToSpeech.QUEUE_ADD, utteranceId);
    }

    /**
     * Thread-safety note: executed in a background thread: {@link TtsService#executor}.
     */
    private void ttsSpeak(TextInterface textInterface, int currentIndex, int offset,
                          int queueMode, int utteranceId) {
        int index = currentIndex + offset;
        int utterance = utteranceId + offset;

        ttsSpeak(getConvertedText(textInterface, index), queueMode, String.valueOf(utterance));
        utteranceIdToIndexMap.addToUtteranceIndexMap(utterance, index);
    }

    /**
     * Thread-safety note: executed in a background thread: {@link TtsService#executor}.
     */
    private CharSequence getConvertedText(TextInterface textInterface, int index) {
        GenericItem item = textInterface.getItem(index);
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

            tts.speak(text, queueMode, null, utteranceId);

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
                setMediaSessionPlaybackState();
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
                public void onStart(String utteranceId) {
//                    Log.v(TAG, "utteranceProgressListener.onStart()");

                    setCurrentItemProgress(utteranceId, -1, -1);
                }

                @Override
                public synchronized void onDone(String utteranceId) {
                    onSpeakDoneListener(utteranceId);
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    Log.w(TAG, "utteranceProgressListener.onError() " + utteranceId
                            + ", errorCode: " + errorCode);
                    super.onError(utteranceId, errorCode);
                }

                @Override
                public void onError(String utteranceId) {
                    Log.w(TAG, "utteranceProgressListener.onError() " + utteranceId);

                    resetCurrentItemProgress();

                    onSpeakDoneListener(utteranceId);
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    Log.d(TAG, "utteranceProgressListener.onStop() " + utteranceId
                            + ", " + interrupted);

                    resetCurrentItemProgress();

                    onSpeakDoneListener(utteranceId);
                }

                @Override
                public void onRangeStart(String utteranceId, int start, int end, int frame) {
                    super.onRangeStart(utteranceId, start, end, frame);

//                    Log.v(TAG, String.format("utteranceProgressListener.onRangeStart(%s, %d, %d, %d)",
//                            utteranceId, start, end, frame));

                    setCurrentItemProgress(utteranceId, start, end);
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

    public void setTextInterface(TextInterface textInterface, Integer articleId, String artist,
                                 String title, String album, String albumArtUrl) {
        Log.d(TAG, String.format("setTextInterface(%s, %s, %s, %s)",
                textInterface == null ? "null" : "not null", artist, title, album));

        boolean newContent = false;

        this.articleId = articleId;
        metaDataArtist = artist;
        metaDataTitle = title;
        metaDataAlbum = album;
        setAlbumArtUrl(albumArtUrl);

        if (textInterface != this.textInterface) {
            newContent = textInterface != null;

            switch (state) {
                case CREATED:
                case STOPPED:
                case PAUSED:
                case ERROR:
                case DESTROYED:
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
        setForegroundAndNotification();

        if (newContent) {
            onNewContent();
        }
    }

    private void setAlbumArtUrl(String albumArtUrl) {
        if (!equalOrEmpty(metaDataAlbumArtUrl, albumArtUrl)) {
            metaDataAlbumArtUrl = albumArtUrl;
            metaDataAlbumArt = null;
            metaDataAlbumArtEffectiveUri = null;

            if (albumArtLoaderTask != null) {
                albumArtLoaderTask.cancel(true);
                albumArtLoaderTask = null;
            }

            if (articleId != null && !TextUtils.isEmpty(metaDataAlbumArtUrl)
                    && settings.isTtsUsePreviewAsAlbumArt()) {
                albumArtLoaderTask = new AlbumArtLoaderTask(articleId,
                        metaDataAlbumArtUrl, this::albumArtLoaded);
                albumArtLoaderTask.execute();
            }
        }
    }

    private void albumArtLoaded(int articleId, String givenUrl, String effectiveUri, Bitmap image) {
        if (state == State.DESTROYED) return;
        if (!equalOrEmpty(givenUrl, metaDataAlbumArtUrl)) return;

        albumArtLoaderTask = null;

        metaDataAlbumArt = image;
        metaDataAlbumArtEffectiveUri = effectiveUri;

        Log.v(TAG, "albumArtLoaded() image=" + image + ", Uri=" + metaDataAlbumArtEffectiveUri);

        if (metaDataAlbumArt != null || !TextUtils.isEmpty(metaDataAlbumArtEffectiveUri)) {
            setMediaSessionMetaData();
            setForegroundAndNotification();
        }
    }

    private void onNewContent() {
        if (autoplayNext) {
            autoplayNext = false;

            playCmd();
        }
    }

    public MediaSessionCompat.Token getMediaSessionToken() {
        return mediaSession.getSessionToken();
    }

    public void setSessionActivity(PendingIntent pendingIntent) {
        mediaSession.setSessionActivity(pendingIntent);
    }

    private void setMediaSessionMetaData() {
        if (mediaSession == null) return;
        Log.v(TAG, "setMediaSessionMetaData() title: " + metaDataTitle);

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metaDataArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, metaDataAlbum)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metaDataTitle);

        if (metaDataAlbumArt != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, metaDataAlbumArt);
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, metaDataAlbumArt);
        }

        if (!TextUtils.isEmpty(metaDataAlbumArtEffectiveUri)) {
            builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI,
                    metaDataAlbumArtEffectiveUri);
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                    metaDataAlbumArtEffectiveUri);
        }

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

        long actions = ALWAYS_AVAILABLE_PLAYBACK_ACTIONS | (state != State.PLAYING ?
                PlaybackStateCompat.ACTION_PLAY : PlaybackStateCompat.ACTION_PAUSE);

        PlaybackStateCompat playbackState = playbackStateBuilder
                .setActions(actions)
                .setState(state.playbackState, position, state == State.PLAYING ? speed : 0)
                .build();

        mediaSession.setPlaybackState(playbackState);
    }

    private void setForegroundAndNotification() {
        setForegroundAndNotification(false);
    }

    private void setForegroundAndNotification(boolean forceForeground) {
        Log.d(TAG, "setForegroundAndNotification("
                + (forceForeground ? "forceForeground" : "") + ")");

        boolean foreground;
        boolean showNotification;

        switch (state) {
            case WANT_TO_PLAY:
            case PLAYING:
                foreground = true;
                showNotification = true;
                break;

            case PAUSED:
                foreground = false;
                showNotification = true;
                break;

            default:
                foreground = false;
                showNotification = false;
                break;
        }

        if (forceForeground) {
            foreground = true;
        }

        if (foreground) {
            if (!isForeground) {
                Log.v(TAG, "setForegroundAndNotification() startForeground()");
                startForeground(NOTIFICATION_ID, generateNotification());
                isForeground = true;
            }
        } else {
            if (isForeground) {
                boolean removeNotification = !showNotification;
                Log.v(TAG, "setForegroundAndNotification()" +
                        " stopForeground(remove notification: " + removeNotification + ")");
                stopForeground(removeNotification);
                isForeground = false;
            }
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat
                .from(getApplicationContext());

        if (showNotification) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            notificationManager.notify(NOTIFICATION_ID, generateNotification());
        } else {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private Notification generateNotification() {
        NotificationCompat.Builder builder = generateNotificationBuilderFrom(
                getApplicationContext(), mediaSession);

        builder.setShowWhen(false)
                .setSmallIcon(R.drawable.wallabag_silhouette);

        if (mediaSession != null && state != State.ERROR) {
//            builder.addAction(generateAction(android.R.drawable.ic_media_previous,
//                    "Previous", KeyEvent.KEYCODE_MEDIA_PREVIOUS));

            builder.addAction(generateAction(R.drawable.ic_fast_rewind_24dp,
                    R.string.notification_action_media_rewind,
                    PlaybackStateCompat.ACTION_REWIND));

            if (state == State.WANT_TO_PLAY || state == State.PLAYING) {
                builder.addAction(generateAction(R.drawable.ic_pause_24dp,
                        R.string.notification_action_media_pause,
                        PlaybackStateCompat.ACTION_PAUSE));
            } else {
                builder.addAction(generateAction(R.drawable.ic_play_arrow_24dp,
                        R.string.notification_action_media_play,
                        PlaybackStateCompat.ACTION_PLAY));
            }

            builder.addAction(generateAction(R.drawable.ic_fast_forward_24dp,
                    R.string.notification_action_media_fastforward,
                    PlaybackStateCompat.ACTION_FAST_FORWARD));

            builder.addAction(generateAction(R.drawable.ic_skip_next_24dp,
                    R.string.notification_action_media_next,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT));

            builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(generateActionIntent(PlaybackStateCompat.ACTION_STOP)));
        }

        return builder.build();
    }

    private NotificationCompat.Builder generateNotificationBuilderFrom(
            Context context, MediaSessionCompat mediaSession) {
        NotificationsHelper.initNotificationChannels();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, NotificationsHelper.CHANNEL_ID_TTS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(generateActionIntent(PlaybackStateCompat.ACTION_STOP));

        if (mediaSession != null) {
            MediaControllerCompat controller = mediaSession.getController();

            builder.setContentIntent(controller.getSessionActivity());

            MediaMetadataCompat mediaMetadata = controller.getMetadata();
            if (mediaMetadata != null) {
                MediaDescriptionCompat description = mediaMetadata.getDescription();

                builder.setContentTitle(description.getTitle())
                        .setContentText(description.getSubtitle())
                        .setSubText(description.getDescription())
                        .setLargeIcon(description.getIconBitmap());
            }
        }

        return builder;
    }

    private NotificationCompat.Action generateAction(int icon, int title, long action) {
        String titleString = getString(title);
        PendingIntent pendingIntent = generateActionIntent(action);
        return new NotificationCompat.Action(icon, titleString, pendingIntent);
    }

    private PendingIntent generateActionIntent(long action) {
        return MediaButtonReceiver.buildMediaButtonPendingIntent(
                getApplicationContext(), mediaActionComponentName, action);
    }

}
