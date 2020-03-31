package fr.gaulupeau.apps.Poche.tts;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.ui.MainActivity;
import fr.gaulupeau.apps.Poche.ui.ReadArticleActivity;

/**
 * Text To Speech (TTS) User Interface.
 */
public class TtsFragment extends Fragment {

    /* AudioManager.VOLUME_CHANGED_ACTION */
    private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";

    private static final String TAG = TtsFragment.class.getSimpleName();

    private static final String PARAM_AUTOPLAY = "autoplay";

    private static final String METADATA_ALBUM = "wallabag";

    private static final float MAX_TTS_SPEED = 4.0F;
    private static final float MAX_TTS_PITCH = 2.0F;

    // TODO: fix: unreliable caching (especially initialization)
    private static final TtsData TTS_DATA = new TtsData();

    private Settings settings;
    private NumberFormat percentFormat;

    private BroadcastReceiver volumeChangeReceiver;

    private PendingIntent notificationPendingIntent;

    private View viewTtsOption;
    private ImageButton btnTtsPlayStop;
    private SeekBar seekBarTtsSpeed;
    private SeekBar seekBarTtsPitch;
    private SeekBar seekBarTtsVolume;
    private SeekBar seekBarTtsSleep;
    private TextView textViewTtsSpeed;
    private TextView textViewTtsPitch;
    private TextView textViewTtsVolume;
    private TextView textViewTtsSleep;
    private Spinner spinnerLanguage;
    private Spinner spinnerVoice;
    private ArrayAdapter<String> spinnerLanguageAdapter;
    private ArrayAdapter<String> spinnerVoiceAdapter;
    private AudioManager audioManager;
    private MediaControllerCompat.Callback mediaCallback;

    private boolean dontStopTtsService;

    private Activity activity;
    private TtsHost ttsHost;

    private TtsService ttsService;
    private ServiceConnection serviceConnection;

    private String artist = "";
    private String title = "";

    private String languageToSelect;

    private WebViewText webViewText;

    private TtsData.VoiceInfo selectedVoiceInfo;

    private boolean initialized;
    private boolean reinitDocument;
    private boolean documentLoaded;
    private boolean documentParsed;
    private boolean setToServicePending;

    public static TtsFragment newInstance(boolean autoplay) {
        TtsFragment fragment = new TtsFragment();
        Bundle args = new Bundle();
        args.putBoolean(PARAM_AUTOPLAY, autoplay);
        fragment.setArguments(args);
        return fragment;
    }

    public TtsFragment() {
        settings = App.getInstance().getSettings();

        percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(0);

        volumeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (VOLUME_CHANGED_ACTION.equals(intent.getAction())) {
                    updateVolumeDisplay();
                }
            }
        };
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach()");

        activity = (Activity) context;
        ttsHost = ((ReadArticleActivity) activity).getTtsHost();

        if (webViewText != null) webViewText.setTtsHost(ttsHost);

        activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        activity.registerReceiver(volumeChangeReceiver, new IntentFilter(VOLUME_CHANGED_ACTION));
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach()");

        activity.unregisterReceiver(volumeChangeReceiver);
        activity = null;

        ttsHost = null;
        if (webViewText != null) webViewText.setTtsHost(null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        Intent mainFocusIntent = new Intent(Intent.ACTION_MAIN);
        mainFocusIntent.setClass(activity, MainActivity.class);
        mainFocusIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationPendingIntent = PendingIntent.getActivity(activity, 0, mainFocusIntent, 0);

        TTS_DATA.init(activity, this::startActivityForResult);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView()");

        View view = inflater.inflate(R.layout.fragment_tts, container, false);

        viewTtsOption = view.findViewById(R.id.viewTTSOptions);
        if (!settings.isTtsOptionsVisible()) {
            viewTtsOption.setVisibility(View.GONE);
        }
        btnTtsPlayStop = view.findViewById(R.id.btnTTSPlayPause);
        btnTtsPlayStop.setOnClickListener(v -> {
            if (ttsService != null) {
                ttsService.playPauseCmd();
            }
        });

        ImageButton btnTtsFastRewind = view.findViewById(R.id.btnTTSFastRewind);
        btnTtsFastRewind.setOnClickListener(v -> {
            if (ttsService != null) {
                ttsService.rewindCmd();
            }
        });

        ImageButton btnTtsFastForward = view.findViewById(R.id.btnTTSFastForward);
        btnTtsFastForward.setOnClickListener(v -> {
            if (ttsService != null) {
                ttsService.fastForwardCmd();
            }
        });

        ImageButton btnTtsOptions = view.findViewById(R.id.btnTTSOptions);
        btnTtsOptions.setOnClickListener(v -> onTtsOptionsClicked());

        CheckBox checkboxTtsAutoplayNext = view.findViewById(R.id.chkbxTTSAutoplayNext);
        checkboxTtsAutoplayNext.setChecked(settings.isTtsAutoplayNext());
        checkboxTtsAutoplayNext.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setTtsAutoplayNext(isChecked);
            if (isChecked) {
                showToastMessage(R.string.ttsAutoplayNextArticle);
            }
        });

        ImageView imgViewTtsAutoplayNext = view.findViewById(R.id.imgviewTTSAutoplayNext);
        imgViewTtsAutoplayNext.setOnClickListener(v -> checkboxTtsAutoplayNext.toggle());

        textViewTtsSpeed = view.findViewById(R.id.textViewTTSSpeed);
        textViewTtsPitch = view.findViewById(R.id.textViewTTSPitch);
        textViewTtsVolume = view.findViewById(R.id.textViewTTSVolume);
        textViewTtsSleep = view.findViewById(R.id.textViewTTSSleep);

        seekBarTtsSpeed = view.findViewById(R.id.seekBarTTSSpeed);
        seekBarTtsSpeed.setMax(Integer.MAX_VALUE);
        seekBarTtsSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean isTouchTracking;
            int initialProgress;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textViewTtsSpeed.setText(percentFormat.format(getSpeedBarValue()));
                if (!isTouchTracking) {
                    ttsSetSpeedFromSeekBar();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                initialProgress = seekBarTtsSpeed.getProgress();
                isTouchTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTouchTracking = false;
                if (seekBarTtsSpeed.getProgress() != initialProgress) {
                    ttsSetSpeedFromSeekBar();
                }
            }
        });
        seekBarTtsSpeed.setProgress((int) (
                settings.getTtsSpeed() * seekBarTtsSpeed.getMax() / MAX_TTS_SPEED));

        seekBarTtsPitch = view.findViewById(R.id.seekBarTTSPitch);
        seekBarTtsPitch.setMax(Integer.MAX_VALUE);
        seekBarTtsPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean isTouchTracking;
            int initialProgress;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textViewTtsPitch.setText(percentFormat.format(getPitchBarValue()));
                if (!isTouchTracking) {
                    ttsSetPitchFromSeekBar();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                initialProgress = seekBarTtsPitch.getProgress();
                isTouchTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTouchTracking = false;
                if (seekBarTtsPitch.getProgress() != initialProgress) {
                    ttsSetPitchFromSeekBar();
                }
            }
        });
        seekBarTtsPitch.setProgress((int) (
                settings.getTtsPitch() * seekBarTtsPitch.getMax() / MAX_TTS_PITCH));

        seekBarTtsSleep = view.findViewById(R.id.seekBarTTSSleep);
        seekBarTtsSleep.setMax(Integer.MAX_VALUE);

        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);

        seekBarTtsVolume = view.findViewById(R.id.seekBarTTSVolume);
        seekBarTtsVolume.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        updateVolumeDisplay();

        seekBarTtsVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                    textViewTtsVolume.setText(percentFormat.format(
                            (1.0 * progress) / seekBar.getMax()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        spinnerLanguage = view.findViewById(R.id.spinnerLanguage);
        spinnerLanguageAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, new ArrayList<>());
        spinnerLanguageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(spinnerLanguageAdapter);

        spinnerVoice = view.findViewById(R.id.spinnerVoice);
        spinnerVoiceAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, new ArrayList<>());
        spinnerVoiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(spinnerVoiceAdapter);

        updateLanguageSpinnerData();

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView,
                                       int position, long id) {
                updateVoiceSpinnerData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {}
        });

        spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView,
                                       int position, long id) {
                onVoiceSelectionChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {}
        });

        mediaCallback = new MediaControllerCompat.Callback() {
            public void onPlaybackStateChanged(PlaybackStateCompat state) {
                switch (state.getState()) {
                    case PlaybackStateCompat.STATE_BUFFERING:
                        btnTtsPlayStop.setImageResource(R.drawable.ic_more_horiz_24dp);
                        break;

                    case PlaybackStateCompat.STATE_PLAYING:
                        btnTtsPlayStop.setImageResource(R.drawable.ic_stop_24dp);
                        break;

                    case PlaybackStateCompat.STATE_PAUSED:
                        btnTtsPlayStop.setImageResource(R.drawable.ic_play_arrow_24dp);
                        break;

                    case PlaybackStateCompat.STATE_STOPPED:
//                        Log.d(TAG, "onPlaybackStateChanged: STATE_STOPPED");
                        break;

                    case PlaybackStateCompat.STATE_ERROR:
//                        Log.d(TAG, "onPlaybackStateChanged: STATE_ERROR");
                        showToastMessage(R.string.ttsError);
                        break;
                }
            }
        };

        initService();

        initialized = true;

        postponedOnDocumentLoadFinished();

        return view;
    }

    private void initService() {
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                Log.d(TAG, "onServiceConnected()");

                ttsService = ((TtsService.LocalBinder) binder).getService();
                ttsService.registerMediaControllerCallback(mediaCallback);
                ttsService.setForeground(!isResumed(), notificationPendingIntent);
                ttsSetSpeedFromSeekBar();
                ttsSetPitchFromSeekBar();

                if (documentParsed) {
                    if (selectedVoiceInfo != null) {
                        setVoiceInfoToServer(selectedVoiceInfo);
                    }
                    setTextAndMetadataToService();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected()");

                ttsService.unregisterMediaControllerCallback(mediaCallback);
                ttsService = null;
            }
        };

        Intent intent = new Intent(activity, TtsService.class);
        if (requireArguments().getBoolean(PARAM_AUTOPLAY)) {
            intent.setAction(TtsService.ACTION_PLAY);
        }
        activity.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        activity.startService(intent);
    }

    private void postponedOnDocumentLoadFinished() {
        if (documentLoaded) {
            new Handler().post(this::onDocumentLoadFinished);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView()");

        initialized = false;

        if (ttsService != null) {
            ttsService.unregisterMediaControllerCallback(mediaCallback);
            activity.unbindService(serviceConnection);
            ttsService = null;
            if (!dontStopTtsService) {
                activity.stopService(new Intent(activity, TtsService.class));
            }
        }
        serviceConnection = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");

        if (ttsService != null) {
            ttsService.setForeground(false, notificationPendingIntent);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");

        saveSettings();

        if (ttsService != null) {
            ttsService.setForeground(true, notificationPendingIntent);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        //dontStopTtsService = true;
        //FIXME: do what is necessary to continue or restart TTS
        super.onConfigurationChanged(newConfig);
    }

    private void saveSettings() {
        settings.setTtsSpeed(getSpeedBarValue());
        settings.setTtsPitch(getPitchBarValue());
        settings.setTtsOptionsVisible(viewTtsOption.getVisibility() == View.VISIBLE);
    }

    private float getSpeedBarValue() {
        return MAX_TTS_SPEED * seekBarTtsSpeed.getProgress() / seekBarTtsSpeed.getMax();
    }

    private float getPitchBarValue() {
        return MAX_TTS_PITCH * seekBarTtsPitch.getProgress() / seekBarTtsPitch.getMax();
    }

    private void onTtsOptionsClicked() {
        if (viewTtsOption.getVisibility() == View.VISIBLE) {
            viewTtsOption.setVisibility(View.GONE);
        } else {
            viewTtsOption.setVisibility(View.VISIBLE);
        }
    }

    private void ttsSetSpeedFromSeekBar() {
        if (ttsService != null) {
            ttsService.setSpeed(getSpeedBarValue());
        }
    }

    private void ttsSetPitchFromSeekBar() {
        if (ttsService != null) {
            ttsService.setPitch(getPitchBarValue());
        }
    }

    public void initForArticle(String domain, String title, String language) {
        Log.d(TAG, "initForArticle()");

        documentLoaded = false;
        documentParsed = false;
        reinitDocument = true;

        this.artist = domain;
        this.title = title;
        languageToSelect = null;
        webViewText = null;

        selectedVoiceInfo = null;

        if (ttsService != null) {
            setTextAndMetadataToService();
            ttsService.pauseCmd();
        }

        selectLanguage(language);
    }

    public void onDocumentLoadFinished() {
        Log.d(TAG, "onDocumentLoadFinished()");

        if (!initialized) {
            Log.d(TAG, "onDocumentLoadFinished() not initialized yet");
            documentLoaded = true;
            return;
        }

        Log.v(TAG, "onDocumentLoadFinished() documentParsed="
                + documentParsed + ", reinitDocument=" + reinitDocument);

        if (documentParsed && !reinitDocument) return;

        reinitDocument = false;
        documentParsed = false;

        if (webViewText != null) {
            webViewText = null;
            setTextAndMetadataToService();
        }

        webViewText = new WebViewText(ttsHost);
        webViewText.setReadFinishedCallback(this::onReadFinished);

        webViewText.parseWebViewDocument(() -> {
            documentParsed = true;
            if (TTS_DATA.isInitialized()) {
                setTextAndMetadataToService();
            } else {
                setToServicePending = true;
            }
        });
    }

    private void setTextAndMetadataToService() {
        Log.v(TAG, "setTextAndMetadataToService()");

        if (ttsService != null) {
            ttsService.setTextInterface(webViewText, artist, title, METADATA_ALBUM);
        }
    }

    private void onReadFinished() {
        if (settings.isTtsAutoplayNext() && ttsHost.nextArticle() && ttsService != null) {
            ttsService.playNextFromStart();
            ttsService.autoplayNext();
            ttsService.playPageFlipSound();
        }
    }

    public void onOpenNewArticle() {
        dontStopTtsService = true;
        artist = "...";
        title = "...";
        webViewText = null;
        setTextAndMetadataToService();
    }

    private void updateVolumeDisplay() {
        if (seekBarTtsVolume != null && textViewTtsVolume != null && audioManager != null) {
            int progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            seekBarTtsVolume.setProgress(progress);
            textViewTtsVolume.setText(percentFormat.format(
                    (1.0 * progress) / seekBarTtsVolume.getMax()));
        }
    }

    private void showToastMessage(@StringRes int text) {
        if (activity != null) Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (TTS_DATA.onActivityResult(requestCode, resultCode, data)) {
            onTtsDataInitialized();
        }
    }

    private void onTtsDataInitialized() {
        updateLanguageSpinnerData();

        setLanguageIfNeeded();

        if (setToServicePending) {
            setToServicePending = false;

            setTextAndMetadataToService();
        }
    }

    private void setLanguageIfNeeded() {
        if (languageToSelect != null) {
            String lang = languageToSelect;
            selectLanguage(lang);
        }
    }

    private void selectLanguage(String language) {
        Log.d(TAG, "selectLanguage() " + language);

        if (TextUtils.isEmpty(language)) return;

        language = new Locale(language).getISO3Language();
        Log.v(TAG, "selectLanguage() ISO3: " + language);

        if (TextUtils.isEmpty(language)) return;

        if (!TTS_DATA.isInitialized()) {
            // The list of TTS engines has not yet be fully obtained,
            // when it is done, the method onActivityResult()
            // will call us back with this parameter:
            languageToSelect = language;
            return;
        }

        List<TtsData.VoiceInfo> voiceInfos = TTS_DATA.getVoicesByLanguage(language);
        if (voiceInfos != null) {
            String languageVoicePreference = settings.getTtsLanguageVoice(language);
            if (TextUtils.isEmpty(languageVoicePreference)) {
                languageVoicePreference = voiceInfos.get(0).displayName;
            }

            TtsData.VoiceInfo voiceInfo = null;
            for (TtsData.VoiceInfo info : voiceInfos) {
                if (info.displayName.equals(languageVoicePreference)) {
                    voiceInfo = info;
                    break;
                }
            }
            if (voiceInfo == null && !voiceInfos.isEmpty()) voiceInfo = voiceInfos.get(0);

            if (voiceInfo != null) {
                setVoiceInfo(voiceInfo);
            }

            updateLanguageSpinnerData();
        }
    }

    private void setVoiceInfo(TtsData.VoiceInfo voiceInfo) {
        Log.v(TAG, "setVoiceInfo() " + voiceInfo.engineInfo.name + ", " + voiceInfo.name);

        selectedVoiceInfo = voiceInfo;

        settings.setTtsEngine(voiceInfo.engineInfo.name);
        settings.setTtsVoice(voiceInfo.name);

        setVoiceInfoToServer(voiceInfo);
    }

    private void setVoiceInfoToServer(TtsData.VoiceInfo voiceInfo) {
        if (ttsService != null) {
            ttsService.setEngineAndVoice(voiceInfo.engineInfo.name, voiceInfo.name);
        }
    }

    private void updateLanguageSpinnerData() {
        Log.v(TAG, "updateLanguageSpinnerData()");

        if (spinnerLanguageAdapter == null || !TTS_DATA.isInitialized()) {
            Log.v(TAG, "updateLanguageSpinnerData() adapter or tts data is not initialized yet");
            return;
        }

        String voice = settings.getTtsVoice();
        String language = voice.contains("-") ?
                voice.substring(0, voice.indexOf("-")) : voice;

        spinnerLanguageAdapter.setNotifyOnChange(false);

        spinnerLanguageAdapter.clear();

        int languagePositionToSelect = 0;
        for (TtsData.DisplayName lang : TTS_DATA.getLanguages()) {
            spinnerLanguageAdapter.add(lang.displayName);
            if (lang.name.equals(language)) {
                languagePositionToSelect = spinnerLanguageAdapter.getCount() - 1;
            }
        }
        spinnerLanguage.setSelection(languagePositionToSelect);

        spinnerLanguageAdapter.setNotifyOnChange(true);
        spinnerLanguageAdapter.notifyDataSetChanged();

        updateVoiceSpinnerData();
    }

    private void updateVoiceSpinnerData() {
        Log.v(TAG, "updateVoiceSpinnerData()");

        if (spinnerVoiceAdapter == null || !TTS_DATA.isInitialized()) {
            Log.v(TAG, "updateVoiceSpinnerData() adapter or tts data is not initialized yet");
            return;
        }

        spinnerVoiceAdapter.setNotifyOnChange(false);

        spinnerVoiceAdapter.clear();

        int languagePosition = spinnerLanguage.getSelectedItemPosition();
        String language = TTS_DATA.getLanguages().get(languagePosition).name;
        String languageVoicePreference = settings.getTtsLanguageVoice(language);

        int voicePositionToSelect = 0;
        for (TtsData.VoiceInfo voiceInfo : TTS_DATA.getVoicesByLanguage(language)) {
            spinnerVoiceAdapter.add(voiceInfo.displayName);
            if (voiceInfo.displayName.equals(languageVoicePreference)) {
                voicePositionToSelect = spinnerVoiceAdapter.getCount() - 1;
            }
        }
        spinnerVoice.setSelection(voicePositionToSelect);

        spinnerVoiceAdapter.setNotifyOnChange(true);
        spinnerVoiceAdapter.notifyDataSetChanged();

        onVoiceSelectionChanged();
    }

    private void onVoiceSelectionChanged() {
        Log.v(TAG, "onVoiceSelectionChanged()");

        if (!TTS_DATA.isInitialized()) {
            Log.v(TAG, "onVoiceSelectionChanged() tts data is not initialized yet");
            return;
        }

        int voicePosition = spinnerVoice.getSelectedItemPosition();
        int languagePosition = spinnerLanguage.getSelectedItemPosition();
        if (voicePosition >= 0 && languagePosition >= 0) {
            String language = TTS_DATA.getLanguages().get(languagePosition).name;
            TtsData.VoiceInfo voiceInfo = TTS_DATA.getVoicesByLanguage(language).get(voicePosition);
            if (voiceInfo != null) {
                setVoiceInfo(voiceInfo);
                settings.setTtsLanguageVoice(voiceInfo.language, voiceInfo.displayName);
            }
        }
    }

}

