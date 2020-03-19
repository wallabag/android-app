package fr.gaulupeau.apps.Poche.tts;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
public class TtsFragment
        extends Fragment {

    private ReadArticleActivity readArticleActivity;
    private WebViewText webViewText;
    private Settings settings;
    private View viewTTSOption;
    private ImageButton btnTTSPlayStop;
    private SeekBar seekBarTTSSpeed;
    private SeekBar seekBarTTSPitch;
    private SeekBar seekBarTTSVolume;
    private SeekBar seekBarTTSSleep;
    private TextView textViewTTSSpeed;
    private TextView textViewTTSPitch;
    private TextView textViewTTSVolume;
    private TextView textViewTTSSleep;
    private Spinner spinnerLanguage;
    private Spinner spinnerVoice;
    private ArrayAdapter<String> spinnerLanguageAdapter;
    private ArrayAdapter<String> spinnerVoiceAdapter;

    private volatile TtsService ttsService;
    private volatile boolean documentParsed;
    private AudioManager audioManager;
    private BroadcastReceiver volumeChangeReceiver;
    private MediaControllerCompat.Callback mediaCallback;
    private PendingIntent notificationPendingIntent;
    private boolean dontStopTtsService;
    private int activityResultNumber;
    private int ttsEnginesNumber;
    private String artist = "";
    private String title = "";
    private String languageToSelect = "";

    private static ArrayList<DisplayName> ttsEngines = null;
    private static ArrayList<DisplayName> ttsLanguages = new ArrayList();
    private static HashMap<String, List<VoiceInfo>> ttsVoiceByLanguage = new HashMap();
    private static NumberFormat percentFormat;
    private static final String LOG_TAG = "TtsFragment";
    private static final float MAX_TTS_SPEED = 4.0F;
    private static final float MAX_TTS_PITCH = 2.0F;
    private static final int REQUEST_CODE_START_ACTIVITY_FOR_RESULT_TTS_SETTINGS = 60000;
    private static final String METADATA_ALBUM = "wallabag";

    protected ServiceConnection serviceConnection;

    public static TtsFragment newInstance(boolean autoplay) {
        TtsFragment fragment = new TtsFragment();
        Bundle args = new Bundle();
        args.putBoolean("autoplay", autoplay);
        fragment.setArguments(args);
        return fragment;
    }

    public TtsFragment() {
        //required no arg default constructor
    }


    @Override
    public void onAttach(Context context) {
        //Log.d(LOG_TAG, "onAttach");
        super.onAttach(context);
        this.readArticleActivity = ((ReadArticleActivity) getActivity());
        if ((this.readArticleActivity != null) && (webViewText != null)) {
            webViewText.readArticleActivity = this.readArticleActivity;
        }
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if (volumeChangeReceiver == null) {
            volumeChangeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION")) {
                        updateVolumeDisplay();
                    }
                }
            };
            context.registerReceiver(volumeChangeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        }
    }

    @Override
    public void onDetach() {
        //Log.d(LOG_TAG, "onDetach");
        super.onDetach();
        readArticleActivity.unregisterReceiver(volumeChangeReceiver);
        readArticleActivity = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //Log.d(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        this.settings = App.getInstance().getSettings();
        Intent mainFocusIntent = new Intent(Intent.ACTION_MAIN);
        mainFocusIntent.setClass(getActivity(), MainActivity.class);
        mainFocusIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationPendingIntent = PendingIntent.getActivity(getActivity(), 0, mainFocusIntent, 0);
        if (ttsEngines == null) {
            loadTtsEnginesList();
        }
    }

    @Override
    public void onDestroy() {
        //Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }


    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_tts, container, false);
        if (this.percentFormat == null) {
            this.percentFormat = NumberFormat.getPercentInstance();
            this.percentFormat.setMaximumFractionDigits(0);
        }
        this.viewTTSOption = view.findViewById(R.id.viewTTSOptions);
        if (!this.settings.isTtsOptionsVisible()) {
            this.viewTTSOption.setVisibility(View.GONE);
        }
        this.btnTTSPlayStop = ((ImageButton) view.findViewById(R.id.btnTTSPlayPause));
        this.btnTTSPlayStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ttsService != null) {
                    ttsService.playPauseCmd();
                }
            }
        });

        ImageButton btnTTSFastRewind = (ImageButton) view.findViewById(R.id.btnTTSFastRewind);
        btnTTSFastRewind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ttsService != null) {
                    ttsService.rewindCmd();
                }
            }
        });

        ImageButton btnTTSFastForward = (ImageButton) view.findViewById(R.id.btnTTSFastForward);
        btnTTSFastForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ttsService != null) {
                    ttsService.fastForwardCmd();
                }
            }

        });

        ImageButton btnTTSOptions = (ImageButton) view.findViewById(R.id.btnTTSOptions);
        btnTTSOptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTTSOptionsClicked((ImageButton) v);
            }
        });

        final CheckBox checkboxTTSAutoplayNext = (CheckBox) view.findViewById(R.id.chkbxTTSAutoplayNext);
        final ImageView imgViewTTSAutoplayNext = (ImageView) view.findViewById(R.id.imgviewTTSAutoplayNext);
        checkboxTTSAutoplayNext.setChecked(settings.isTtsAutoplayNext());
        View.OnClickListener ttsAutoplayNextClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.setTtsAutoplayNext(checkboxTTSAutoplayNext.isChecked());
                if (checkboxTTSAutoplayNext.isChecked()) {
                    showToastMessage(R.string.ttsAutoplayNextArticle);
                }
            }
        };
        checkboxTTSAutoplayNext.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setTtsAutoplayNext(isChecked);
                if (isChecked) {
                    showToastMessage(R.string.ttsAutoplayNextArticle);
                }
            }
        });
        imgViewTTSAutoplayNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkboxTTSAutoplayNext.toggle();
            }
        });

        this.textViewTTSSpeed = ((TextView) view.findViewById(R.id.textViewTTSSpeed));
        this.textViewTTSPitch = ((TextView) view.findViewById(R.id.textViewTTSPitch));
        this.textViewTTSVolume = ((TextView) view.findViewById(R.id.textViewTTSVolume));
        this.textViewTTSSleep = ((TextView) view.findViewById(R.id.textViewTTSSleep));

        this.seekBarTTSSpeed = ((SeekBar) view.findViewById(R.id.seekBarTTSSpeed));
        this.seekBarTTSSpeed.setMax(Integer.MAX_VALUE);
        this.seekBarTTSSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean isTouchTracking;
            int initialProgress;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textViewTTSSpeed.setText(percentFormat.format(getSpeedBarValue()));
                if (!isTouchTracking) {
                    ttsSetSpeedFromSeekBar();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                initialProgress = seekBarTTSSpeed.getProgress();
                isTouchTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTouchTracking = false;
                if (seekBarTTSSpeed.getProgress() != initialProgress) {
                    ttsSetSpeedFromSeekBar();
                }
            }
        });

        this.seekBarTTSSpeed.setProgress((int) (this.settings.getTtsSpeed() * this.seekBarTTSSpeed.getMax() / MAX_TTS_SPEED));

        this.seekBarTTSPitch = ((SeekBar) view.findViewById(R.id.seekBarTTSPitch));
        this.seekBarTTSPitch.setMax(Integer.MAX_VALUE);
        this.seekBarTTSPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean isTouchTracking;
            int initialProgress;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textViewTTSPitch.setText(percentFormat.format(getPitchBarValue()));
                if (!isTouchTracking) {
                    ttsSetPitchFromSeekBar();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                initialProgress = seekBarTTSPitch.getProgress();
                isTouchTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTouchTracking = false;
                if (seekBarTTSPitch.getProgress() != initialProgress) {
                    ttsSetPitchFromSeekBar();
                }
            }
        });

        this.seekBarTTSPitch.setProgress((int) (this.settings.getTtsPitch() * this.seekBarTTSPitch.getMax() / MAX_TTS_PITCH));

        this.seekBarTTSSleep = ((SeekBar) view.findViewById(R.id.seekBarTTSSleep));
        this.seekBarTTSSleep.setMax(Integer.MAX_VALUE);
        this.seekBarTTSSleep.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        this.seekBarTTSVolume = (SeekBar) view.findViewById(R.id.seekBarTTSVolume);
        this.seekBarTTSVolume.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        updateVolumeDisplay();

        this.seekBarTTSVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                    textViewTTSVolume.setText(percentFormat.format((1.0 * progress) / seekBar.getMax()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        spinnerLanguage = (Spinner) view.findViewById(R.id.spinnerLanguage);
        spinnerLanguageAdapter = new ArrayAdapter(this.getContext(),
                android.R.layout.simple_spinner_item, new ArrayList<String>());
        spinnerLanguageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(spinnerLanguageAdapter);

        spinnerVoice = (Spinner) view.findViewById(R.id.spinnerVoice);
        spinnerVoiceAdapter = new ArrayAdapter(this.getContext(),
                android.R.layout.simple_spinner_item, new ArrayList<String>());
        spinnerVoiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(spinnerVoiceAdapter);
        updateLanguageSpinnerData();

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                onLanguageSelectionChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {}
        });

        spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                onVoiceSelectionChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {}
        });

        mediaCallback = new MediaControllerCompat.Callback() {
            public void onPlaybackStateChanged(PlaybackStateCompat state) {
                switch (state.getState()) {
                    case PlaybackStateCompat.STATE_BUFFERING:
                        btnTTSPlayStop.setImageResource(R.drawable.ic_more_horiz_24dp);
                        break;
                    case PlaybackStateCompat.STATE_PLAYING:
                        btnTTSPlayStop.setImageResource(R.drawable.ic_stop_24dp);
                        break;
                    case PlaybackStateCompat.STATE_PAUSED:
                        btnTTSPlayStop.setImageResource(R.drawable.ic_play_arrow_24dp);
                        break;
                    case PlaybackStateCompat.STATE_STOPPED:
                        //Log.d(LOG_TAG, "onPlaybackStateChanged: STATE_STOPPED");
                        break;
                    case PlaybackStateCompat.STATE_ERROR:
                        //Log.d(LOG_TAG, "onPlaybackStateChanged: STATE_ERROR");
                        showToastMessage(R.string.ttsError);
                        break;
                }
            }
        };

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                Log.d(LOG_TAG, "onServiceConnected");
                ttsService = ((TtsService.LocalBinder) binder).getService();
                ttsService.registerCallback(mediaCallback, new Handler());
                ttsService.setVisible(!TtsFragment.this.isResumed(), notificationPendingIntent);
                ttsSetSpeedFromSeekBar();
                ttsSetPitchFromSeekBar();
                if (documentParsed) {
                    ttsService.setTextInterface(webViewText, artist, title, METADATA_ALBUM);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(LOG_TAG, "onServiceDisconnected");
                ttsService.unregisterCallback(mediaCallback);
                ttsService = null;
            }
        };
        documentParsed = false;

        Intent intent = new Intent(getActivity(), TtsService.class);
        if (getArguments().getBoolean("autoplay")) {
            intent.setAction("PLAY");
        }
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        getActivity().startService(intent);
        return view;
    }

    @Override
    public void onDestroyView() {
        //Log.d(LOG_TAG, "onDestroyView");
        super.onDestroyView();
        if (ttsService != null) {
            getActivity().unbindService(serviceConnection);
            if (!dontStopTtsService) {
                Intent intent = new Intent(getContext(), TtsService.class);
                getActivity().stopService(intent);
            }
        }
        serviceConnection = null;
    }

    @Override
    public void onStart() {
        //Log.d(LOG_TAG, "onStart");
        super.onStart();
    }

    @Override
    public void onStop() {
        //Log.d(LOG_TAG, "onStop");
        super.onStop();
    }

    @Override
    public void onResume() {
        //Log.d(LOG_TAG, "onResume");
        super.onResume();
        if (ttsService != null) {
            ttsService.setVisible(false, notificationPendingIntent);
        }
    }

    @Override
    public void onPause() {
        //Log.d(LOG_TAG, "onPause");
        super.onPause();
        saveSettings();
        if (ttsService != null) {
            ttsService.setVisible(true, notificationPendingIntent);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        //dontStopTtsService = true;
        //FIXME: do what is necessary to continue or restart TTS
        super.onConfigurationChanged(newConfig);
    }


    private void saveSettings() {
        this.settings.setTtsSpeed(getSpeedBarValue());
        this.settings.setTtsPitch(getPitchBarValue());
        this.settings.setTtsOptionsVisible(this.viewTTSOption.getVisibility() == View.VISIBLE);
    }

    private float getSpeedBarValue() {
        return MAX_TTS_SPEED * this.seekBarTTSSpeed.getProgress() / this.seekBarTTSSpeed.getMax();
    }

    private float getPitchBarValue() {
        return MAX_TTS_PITCH * this.seekBarTTSPitch.getProgress() / this.seekBarTTSPitch.getMax();
    }


    private void onTTSOptionsClicked(ImageButton btn) {
        if (viewTTSOption.getVisibility() == View.VISIBLE) {
            viewTTSOption.setVisibility(View.GONE);
        } else {
            viewTTSOption.setVisibility(View.VISIBLE);
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

    public void onDocumentLoadStart(String domain, String title, String language) {
        //Log.d(LOG_TAG, "onDocumentLoadStart");
        this.artist = domain;
        this.title = title;
        if (ttsService != null) {
            ttsService.setTextInterface(null, artist, title, METADATA_ALBUM);
            ttsService.pauseCmd();
        }
        this.selectLanguage(language);
    }

    public void onDocumentLoadFinished(WebView webView, ScrollView scrollView) {
        //Log.d(LOG_TAG, "onDocumentLoadFinished");
        if (webViewText == null) {
            if (readArticleActivity == null) {
                Log.e(LOG_TAG, "onDocumentLoadFinished(): readArticleActivity is null");
            }
            webViewText = new WebViewText(webView, scrollView, readArticleActivity);
            webViewText.setOnReadFinishedCallback(new Runnable() {
                @Override
                public void run() {
                    onReadFinished();
                }
            });
        }
        webViewText.parseWebViewDocument(new Runnable() {
            public void run() {
                documentParsed = true;
                if (ttsService != null) {
                    ttsService.setTextInterface(webViewText, artist, title, METADATA_ALBUM);
                }
            }
        });
    }

    private void onReadFinished() {
        if (settings.isTtsAutoplayNext()) {
            if (ttsService != null) {
                ttsService.playPageFlipSound();
            }
            if (readArticleActivity.openNextArticle()) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (ttsService != null) {
                            ttsService.playFromStartCmd();
                        }
                    }
                }, 1500);
            }
        }
    }

    public void onOpenNewArticle() {
        this.dontStopTtsService = true;
        this.artist = "...";
        this.title = "...";
        if ((ttsService != null)) {
            ttsService.setTextInterface(null, artist, title, METADATA_ALBUM);
        }
    }

    public boolean onWebViewConsoleMessage(ConsoleMessage cm) {
        boolean result = false;
        if (webViewText != null) {
            result = webViewText.onWebViewConsoleMessage(cm);
        }
        return result;
    }


    private void updateVolumeDisplay() {
        if (seekBarTTSVolume != null && textViewTTSVolume != null && audioManager != null) {
            int progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            seekBarTTSVolume.setProgress(progress);
            textViewTTSVolume.setText(percentFormat.format((1.0 * progress) / seekBarTTSVolume.getMax()));
        }
    }


    private void showToastMessage(@StringRes int text) {
        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
    }


    static class DisplayName implements Comparable<DisplayName> {
        String name;
        String displayName;

        DisplayName() {}

        DisplayName(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }

        @Override
        public int compareTo(@NonNull DisplayName another) {
            return this.displayName.compareTo(another.displayName);
        }
    }

    static class VoiceInfo extends DisplayName {
        String language;
        String countryDetails;
        DisplayName engineInfo;
    }

    private void loadTtsEnginesList() {
        if (ttsEngines == null) {
            Log.d(LOG_TAG, "loadTtsEnginesList");
            final Intent ttsIntent = new Intent();
            ttsIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            final PackageManager pm = getActivity().getPackageManager();
            List<ResolveInfo> resolveInfoList = pm.queryIntentActivities(ttsIntent, PackageManager.GET_META_DATA);
            ttsEnginesNumber = resolveInfoList.size();
            ttsEngines = new ArrayList();
            for (ResolveInfo resolveInfo : resolveInfoList) {
                DisplayName engineInfo = new DisplayName(
                        resolveInfo.activityInfo.applicationInfo.packageName,
                        resolveInfo.loadLabel(pm).toString());
                Log.d(LOG_TAG, "loadTtsEnginesList: " + engineInfo.name + ": " + engineInfo.displayName);
                ttsEngines.add(engineInfo);
                Intent getVoicesIntent = new Intent();
                getVoicesIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
                getVoicesIntent.setPackage(engineInfo.name);
                getVoicesIntent.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
                startActivityForResult(getVoicesIntent, ttsEngines.size() - 1);
            }
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        Log.d(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode);
        if (requestCode == REQUEST_CODE_START_ACTIVITY_FOR_RESULT_TTS_SETTINGS) {
            if (ttsService != null) {
                ttsService.setEngineAndVoice(ttsService.getEngine(), ttsService.getVoice());
            }
        } else if ((requestCode >= 0) && (requestCode < ttsEngines.size())) {
            if ((resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)
                    && (data != null) && data.hasExtra("availableVoices")) {
                ArrayList<String> availableVoices = data.getStringArrayListExtra("availableVoices");
                DisplayName engineInfo = ttsEngines.get(requestCode);
                Log.d(LOG_TAG, "onActivityResult: " + engineInfo.displayName + " voices = " + availableVoices.toString());
                for (String voiceName : availableVoices) {
                    VoiceInfo voiceInfo = new VoiceInfo();
                    voiceInfo.name = voiceName;
                    voiceInfo.engineInfo = engineInfo;
                    int separatorIndex = voiceName.indexOf("-");
                    if (separatorIndex >= 0) {
                        voiceInfo.language = voiceName.substring(0, separatorIndex);
                        voiceInfo.countryDetails = voiceName.substring(separatorIndex + 1);
                        voiceInfo.displayName = engineInfo.displayName + ":" + voiceInfo.countryDetails;
                    } else {
                        voiceInfo.language = voiceName;
                        voiceInfo.countryDetails = "";
                        voiceInfo.displayName = engineInfo.displayName;
                    }
                    synchronized (ttsVoiceByLanguage) {
                        if (!ttsVoiceByLanguage.containsKey(voiceInfo.language)) {
                            ttsVoiceByLanguage.put(voiceInfo.language, new ArrayList());
                        }
                        ttsVoiceByLanguage.get(voiceInfo.language).add(voiceInfo);
                    }
                }
            }
            synchronized (this) {
                activityResultNumber++;
                if (activityResultNumber == ttsEnginesNumber) {
                    // Sort languages by displayName:
                    ttsLanguages = new ArrayList(ttsVoiceByLanguage.size());
                    for (String language : ttsVoiceByLanguage.keySet()) {
                        ttsLanguages.add(new DisplayName(
                                language,
                                new Locale(language).getDisplayLanguage()));
                    }
                    Collections.sort(ttsLanguages);
                    // sort voice lists by displayName:
                    for (List<VoiceInfo> voices : ttsVoiceByLanguage.values()) {
                        Collections.sort(voices);
                    }
                    if (languageToSelect.length() > 0) {
                        selectLanguage(languageToSelect);
                    }
                    updateLanguageSpinnerData();
                }
            }
        }
    }

    private synchronized void updateLanguageSpinnerData() {
        //Log.d(LOG_TAG, "updateLanguageSpinnerData");
        if ((ttsEngines != null)
                && (activityResultNumber == ttsEnginesNumber)
                && (spinnerLanguageAdapter != null)) {
            String voice = settings.getTtsVoice();
            String language = voice.indexOf("-") >= 0 ?
                    voice.substring(0, voice.indexOf("-")) : voice;
            spinnerLanguageAdapter.setNotifyOnChange(false);
            spinnerLanguageAdapter.clear();
            int languagePositionToSelect = 0;
            for (DisplayName lang : ttsLanguages) {
                spinnerLanguageAdapter.add(lang.displayName);
                if (lang.name.equals(language)) {
                    languagePositionToSelect = spinnerLanguageAdapter.getCount() - 1;
                }
            }
            spinnerLanguage.setSelection(languagePositionToSelect);
            spinnerLanguageAdapter.notifyDataSetChanged();
            spinnerLanguageAdapter.setNotifyOnChange(true);
        }
    }

    private synchronized void selectLanguage(String language) {
        //Log.d(LOG_TAG, "selectLanguage " + language);
        if ((language == null) || (language.length() == 0)) {
            return;
        }
        language = new Locale(language).getISO3Language();
        if ((language == null) || (language.length() == 0)) {
            return;
        }
        if ((ttsEngines == null)
                || (activityResultNumber != ttsEnginesNumber)) {
            // The list of TTS engines has not yet be fully obtained,
            // when it is done, the method onActivityResult()
            // will call us back with this parameter:
            languageToSelect = language;
        } else {
            if (ttsVoiceByLanguage.get(language) != null) {
                if (this.settings == null) {
                    this.settings = App.getInstance().getSettings();
                }
                String voiceDisplayName = settings.getTtsLanguageVoice(language);
                if ((voiceDisplayName == null) || (voiceDisplayName.length() == 0)) {
                    voiceDisplayName = ttsVoiceByLanguage.get(language).get(0).displayName;
                }
                String engineName = null;
                String voiceName = null;
                for (VoiceInfo voiceInfo : ttsVoiceByLanguage.get(language)) {
                    engineName = voiceInfo.engineInfo.name;
                    voiceName = voiceInfo.name;
                    if (voiceInfo.displayName.equals(voiceDisplayName)) {
                        break;
                    }
                }
                settings.setTtsVoice(voiceName);
                settings.setTtsEngine(engineName);
                if (ttsService != null) {
                    ttsService.setEngineAndVoice(engineName, voiceName);
                }
                if (spinnerLanguage != null) {
                    updateLanguageSpinnerData();
                }
            }
        }
    }

    private void onLanguageSelectionChanged() {
        //Log.d(LOG_TAG, "onLanguageSelectionChanged");
        spinnerVoiceAdapter.setNotifyOnChange(false);
        spinnerVoiceAdapter.clear();
        int languagePosition = spinnerLanguage.getSelectedItemPosition();
        String language = ttsLanguages.get(languagePosition).name;
        String languageVoicePreference = settings.getTtsLanguageVoice(language);
        int voicePositionToSelect = 0;
        for (VoiceInfo voiceInfo : ttsVoiceByLanguage.get(language)) {
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
        //Log.d(LOG_TAG, "onVoiceSelectionChanged");
        int voicePosition = spinnerVoice.getSelectedItemPosition();
        int languagePosition = spinnerLanguage.getSelectedItemPosition();
        if ((voicePosition >= 0) && (languagePosition >= 0)) {
            String language = ttsLanguages.get(languagePosition).name;
            VoiceInfo voiceInfo = ttsVoiceByLanguage.get(language).get(voicePosition);
            if (voiceInfo != null) {
                if (ttsService != null) {
                    ttsService.setEngineAndVoice(voiceInfo.engineInfo.name, voiceInfo.name);
                }
                settings.setTtsEngine(voiceInfo.engineInfo.name);
                settings.setTtsVoice(voiceInfo.name);
                settings.setTtsLanguageVoice(voiceInfo.language, voiceInfo.displayName);
            }
        }
    }

}

