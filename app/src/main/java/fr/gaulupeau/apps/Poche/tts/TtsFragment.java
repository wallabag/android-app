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
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
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

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.ui.ArticlesListActivity;
import fr.gaulupeau.apps.Poche.ui.ReadArticleActivity;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Text To Speech (TTS) User Interface.
 */
public class TtsFragment
    extends Fragment {

    private ReadArticleActivity readArticleActivity;
    private WebviewText webviewText;
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

    private static ArrayList<DisplayName> ttsEngines = null;
    private static ArrayList<DisplayName> ttsLanguages = new ArrayList();
    private static HashMap<String, List<VoiceInfo>> ttsVoiceByLanguage = new HashMap();
    private static NumberFormat percentFormat;
    private static final String LOG_TAG = "TtsFragment";
    private static final float MAX_TTS_SPEED = 4.0F;
    private static final float MAX_TTS_PITCH = 2.0F;
    private static final int REQUESTCODE_START_ACTIVITY_FOR_RESULT_TTS_SETTINGS = 60000;
    private static final String METADATA_ALBUM = "Wallabag";

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
        mainFocusIntent.setClass(getActivity(), ArticlesListActivity.class);
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
        if (!this.settings.getBoolean(Settings.TTS_OPTIONS_VISIBLE, false)) {
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

        final CheckBox chkbxTTSAutoplayNext = (CheckBox) view.findViewById(R.id.chkbxTTSAutoplayNext);
        final ImageView imgviewTTSAutolpayNext = (ImageView) view.findViewById(R.id.imgviewTTSAutoplayNext);
        chkbxTTSAutoplayNext.setChecked(settings.getBoolean(Settings.TTS_AUTOPLAY_NEXT, false));
        View.OnClickListener ttsAutoplayNextClickListner = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.setBoolean(Settings.TTS_AUTOPLAY_NEXT, chkbxTTSAutoplayNext.isChecked());
                if (chkbxTTSAutoplayNext.isChecked()) {
                    showToastMessage(R.string.ttsAutoplayNextArticle);
                }
            }
        };
        chkbxTTSAutoplayNext.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setBoolean(Settings.TTS_AUTOPLAY_NEXT, isChecked);
                if (isChecked) {
                    showToastMessage(R.string.ttsAutoplayNextArticle);
                }
            }
        });
        imgviewTTSAutolpayNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chkbxTTSAutoplayNext.toggle();
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

        this.seekBarTTSSpeed.setProgress((int) (this.settings.getFloat(Settings.TTS_SPEED, 1.0F) * this.seekBarTTSSpeed.getMax() / MAX_TTS_SPEED));

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

        this.seekBarTTSPitch.setProgress((int) (this.settings.getFloat(Settings.TTS_PITCH, 1.0F) * this.seekBarTTSPitch.getMax() / MAX_TTS_PITCH));

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
                    ttsService.setTextInterface(webviewText, artist, title, METADATA_ALBUM);
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
        this.settings.setFloat(Settings.TTS_SPEED, getSpeedBarValue());
        this.settings.setFloat(Settings.TTS_PITCH, getPitchBarValue());
        this.settings.setBoolean(Settings.TTS_OPTIONS_VISIBLE, this.viewTTSOption.getVisibility() == View.VISIBLE);
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

    public void onDocumentLoadStart(String domain, String title) {
        //Log.d(LOG_TAG, "onDocumentLoadStart");
        this.artist = domain;
        this.title = title;
        if (ttsService != null) {
            ttsService.setTextInterface(null, artist, title, METADATA_ALBUM);
            ttsService.pauseCmd();
        }
    }

    public void onDocumentLoadFinished(WebView webView, ScrollView scrollView) {
        //Log.d(LOG_TAG, "onDocumentLoadFinished");
        if (webviewText == null) {
            webviewText = new WebviewText(webView, scrollView, readArticleActivity);
            webviewText.setOnReadFinishedCallback(new Runnable() {
                @Override
                public void run() {
                    onReadFinished();
                }
            });
        }
        webviewText.parseWebviewDocument(new Runnable() {
            public void run() {
                documentParsed = true;
                if (ttsService != null) {
                    ttsService.setTextInterface(webviewText, artist, title, METADATA_ALBUM);
                }
            }
        });
    }

    private void onReadFinished() {
        if (settings.getBoolean(Settings.TTS_AUTOPLAY_NEXT, false)) {
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

    public boolean onWebviewConsoleMessage(ConsoleMessage cm) {
        boolean result = false;
        if (webviewText != null) {
            result = webviewText.onWebviewConsoleMessage(cm);
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
        public int compareTo(DisplayName another) {
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
        if (requestCode == REQUESTCODE_START_ACTIVITY_FOR_RESULT_TTS_SETTINGS) {
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
                updateLanguageSpinnerData();
            }
        }
    }

    private synchronized void updateLanguageSpinnerData() {
        //Log.d(LOG_TAG, "updateLanguageSpinnerData");
        if ((ttsEngines != null)
                && (activityResultNumber == ttsEnginesNumber)
                && (spinnerLanguageAdapter != null))
        {
            String voice = settings.getString(Settings.TTS_VOICE, "");
            String language = voice.indexOf("-") >= 0 ?
                    voice.substring(0, voice.indexOf("-")) : voice;
            spinnerLanguageAdapter.setNotifyOnChange(false);
            spinnerLanguageAdapter.clear();
            int langugagePositionToSelect = 0;
            for(DisplayName lang: ttsLanguages) {
                spinnerLanguageAdapter.add(lang.displayName);
                if (lang.name.equals(language)) {
                    langugagePositionToSelect = spinnerLanguageAdapter.getCount() - 1;
                }
            }
            spinnerLanguage.setSelection(langugagePositionToSelect);
            spinnerLanguageAdapter.notifyDataSetChanged();
            spinnerLanguageAdapter.setNotifyOnChange(true);
        }
    }

    private void onLanguageSelectionChanged() {
        //Log.d(LOG_TAG, "onLanguageSelectionChanged");
        spinnerVoiceAdapter.setNotifyOnChange(false);
        spinnerVoiceAdapter.clear();
        int languagePosition = spinnerLanguage.getSelectedItemPosition();
        String language = ttsLanguages.get(languagePosition).name;
        String languageVoicePreference = settings.getString(Settings.TTS_LANGUAGE_VOICE + language, "");
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
        if ((voicePosition >=0) && (languagePosition >=0)) {
            String language = ttsLanguages.get(languagePosition).name;
            VoiceInfo voiceInfo = ttsVoiceByLanguage.get(language).get(voicePosition);
            if (voiceInfo != null) {
                if (ttsService != null) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        // Cannot programmatically change TTS Engine with older Android,
                        // We redirect the user to TTS Settings:
                        String curTtsEngine = ttsService.getEngine();
                        if ( ! ((curTtsEngine == null)
                                ||"".equals(curTtsEngine)
                                || voiceInfo.engineInfo.name.equals(curTtsEngine)))
                        {
                            Intent intent = new Intent();
                            intent.setAction("com.android.settings.TTS_SETTINGS");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            this.startActivity(intent);
                            startActivityForResult(intent, REQUESTCODE_START_ACTIVITY_FOR_RESULT_TTS_SETTINGS);
                        } else {
                            ttsService.setEngineAndVoice(voiceInfo.engineInfo.name, voiceInfo.name);
                        }
                    } else {
                        ttsService.setEngineAndVoice(voiceInfo.engineInfo.name, voiceInfo.name);
                    }
                }
                settings.setString(Settings.TTS_ENGINE, voiceInfo.engineInfo.name);
                settings.setString(Settings.TTS_VOICE, voiceInfo.name);
                settings.setString(Settings.TTS_LANGUAGE_VOICE + voiceInfo.language, voiceInfo.displayName);
            }
        }
    }

}

