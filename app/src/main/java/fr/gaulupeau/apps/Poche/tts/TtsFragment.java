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
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
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
import android.widget.Button;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

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
    private boolean isOpeningNewArticle;
    private int activityResultNumber;
    private int ttsEnginesNumber;

    private static ArrayList<EngineInfo> ttsEngines = null;
    private static ArrayList<String> ttsLanguages = new ArrayList();
    private static HashMap<String, List<VoiceInfo>> ttsVoiceByLanguage = new HashMap();
    private static NumberFormat percentFormat;
    private static final String LOG_TAG = "TtsFragment";
    private static final float MAX_TTS_SPEED = 4.0F;
    private static final float MAX_TTS_PITCH = 2.0F;

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
        Log.d(LOG_TAG, "onAttach");
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
        Log.d(LOG_TAG, "onDetach");
        super.onDetach();
        readArticleActivity.unregisterReceiver(volumeChangeReceiver);
        readArticleActivity = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        this.settings = App.getInstance().getSettings();
        Intent mainFocusIntent = new Intent(Intent.ACTION_MAIN);
        mainFocusIntent.setClass(getActivity(), ArticlesListActivity.class);
        mainFocusIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationPendingIntent = PendingIntent.getActivity(getActivity(), 0, mainFocusIntent, 0);
        if (ttsEngines == null) {
            getTtsEngines();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
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

        ImageButton btnTTSPrevious = (ImageButton) view.findViewById(R.id.btnTTSPrevious);
        btnTTSPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ttsService != null) {
                    ttsService.rewindCmd();
                }
            }
        });

        ImageButton btnTTSNext = (ImageButton) view.findViewById(R.id.btnTTSNext);
        btnTTSNext.setOnClickListener(new View.OnClickListener() {
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

        final CheckBox chkbxTTSContinue = (CheckBox) view.findViewById(R.id.chkbxTTSContinue);
        final ImageView imgviewTTSContinue = (ImageView) view.findViewById(R.id.imgviewTTSContinue);
        chkbxTTSContinue.setChecked(settings.getBoolean(Settings.TTS_AUTOPLAY_NEXT, false));
        View.OnClickListener ttsContinueClickListner = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.setBoolean(Settings.TTS_AUTOPLAY_NEXT, chkbxTTSContinue.isChecked());
                if (chkbxTTSContinue.isChecked()) {
                    showToastMessage("Autoplay next article");
                }
            }
        };
        chkbxTTSContinue.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setBoolean(Settings.TTS_AUTOPLAY_NEXT, isChecked);
                if (isChecked) {
                    showToastMessage("Autoplay next article");
                }
            }
        });
        imgviewTTSContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chkbxTTSContinue.toggle();
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
                showToastMessage("Not implemented");
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
                        Log.d(LOG_TAG, "onPlaybackStateChanged: STATE_STOPPED");
                        break;
                    case PlaybackStateCompat.STATE_ERROR:
                        Log.d(LOG_TAG, "onPlaybackStateChanged: STATE_ERROR");
                        showToastMessage("Text To Speech Error");
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
                    ttsService.setTextInterface(webviewText);
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
        getActivity().startService(intent);

        return view;
    }

    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView");
        super.onDestroyView();
        if ((ttsService != null) && (!isOpeningNewArticle)) {
            Intent intent = new Intent(getContext(), TtsService.class);
            getActivity().stopService(intent);
        }
        serviceConnection = null;
    }

    @Override
    public void onStart() {
        Log.d(LOG_TAG, "onStart");
        Intent intent = new Intent(getActivity(), TtsService.class);
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        getActivity().startService(intent);
        super.onStart();
    }

    @Override
    public void onStop() {
        Log.d(LOG_TAG, "onStop");
        getActivity().unbindService(serviceConnection);
        super.onStop();
    }

    @Override
    public void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
        if (ttsService != null) {
            ttsService.setVisible(false, notificationPendingIntent);
        }
    }

    @Override
    public void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
        saveSettings();
        if (ttsService != null) {
            ttsService.setVisible(true, notificationPendingIntent);
        }
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


    public void onTTSOptionsClicked(ImageButton btn) {
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
        Log.d(LOG_TAG, "onDocumentLoadStart");
        TtsService.metaDataArtist = domain;
        TtsService.metaDataTitle = title;
        if (ttsService != null) {
            ttsService.setTextInterface(null);
            ttsService.pauseCmd();
        }
    }

    public void onDocumentLoadFinished(WebView webView, ScrollView scrollView) {
        Log.d(LOG_TAG, "onDocumentLoadFinished");
        webviewText = new WebviewText(webView, scrollView, readArticleActivity);
        webviewText.setOnReadFinishedCallback(new Runnable() {
            @Override
            public void run() {
                onReadFinished();
            }
        });
        webviewText.parseWebviewDocument(new Runnable() {
            public void run() {
                documentParsed = true;
                if (ttsService != null) {
                    ttsService.setTextInterface(webviewText);
                }
            }
        });
    }

    public void onReadFinished() {
        if (settings.getBoolean(Settings.TTS_AUTOPLAY_NEXT, false)) {
            if (readArticleActivity.openNextArticle()) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (ttsService != null) {
                            ttsService.playCmd();
                        }
                    }
                }, 1500);
            }
        }
    }

    public void onOpenNewArticle() {
        this.isOpeningNewArticle = true;
        if ((ttsService != null)) {
            ttsService.setTextInterface(null);
        }
    }

    public void onWebviewConsoleMessage(ConsoleMessage cm) {
        if (webviewText != null) {
            webviewText.onWebviewConsoleMessage(cm);
        }
    }


    private void updateVolumeDisplay() {
        if (seekBarTTSVolume != null && textViewTTSVolume != null && audioManager != null) {
            int progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            seekBarTTSVolume.setProgress(progress);
            textViewTTSVolume.setText(percentFormat.format((1.0 * progress) / seekBarTTSVolume.getMax()));
        }
    }


    private void showToastMessage(String text) {
        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
    }


    static class EngineInfo {
        String label;
        String packageName;
    }
    
    static class VoiceInfo {
        String name;
        String lang;
        String countryDetails;
        String displayName;
        EngineInfo engine;
    }

    private void getTtsEngines() {
        Log.d(LOG_TAG, "getTtsEngines");
        if (ttsEngines == null) {
            final Intent ttsIntent = new Intent();
            ttsIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            final PackageManager pm = getActivity().getPackageManager();
            List<ResolveInfo> resolveInfoList = pm.queryIntentActivities(ttsIntent, PackageManager.GET_META_DATA);
            ttsEnginesNumber = resolveInfoList.size();
            ttsEngines = new ArrayList();
            for (ResolveInfo resolveInfo : resolveInfoList) {
                EngineInfo engineInfo = new EngineInfo();
                engineInfo.label = resolveInfo.loadLabel(pm).toString();
                engineInfo.packageName = resolveInfo.activityInfo.applicationInfo.packageName;
                Log.d(LOG_TAG, "getTtsEngines: " + engineInfo.packageName + ": " + engineInfo.label);
                ttsEngines.add(engineInfo);
                Intent getVoicesIntent = new Intent();
                getVoicesIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
                getVoicesIntent.setPackage(engineInfo.packageName);
                getVoicesIntent.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
                startActivityForResult(getVoicesIntent, ttsEngines.size() - 1);
            }
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        Log.d(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode);
        if ((resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)
                && (data != null) && data.hasExtra("availableVoices")
                && (requestCode >= 0) && (requestCode < ttsEngines.size()))
        {
            ArrayList<String> availableVoices = data.getStringArrayListExtra("availableVoices");
            EngineInfo engineInfo = ttsEngines.get(requestCode);
            Log.d(LOG_TAG, "onActivityResult: " + engineInfo.label + " voices = " + availableVoices.toString());
            for(String voiceName: availableVoices) {
                VoiceInfo voiceInfo = new VoiceInfo();
                voiceInfo.name = voiceName;
                voiceInfo.engine = engineInfo;
                int separatorIndex = voiceName.indexOf("-");
                if (separatorIndex >= 0) {
                    voiceInfo.lang = voiceName.substring(0, separatorIndex);
                    voiceInfo.countryDetails = voiceName.substring(separatorIndex + 1);
                    voiceInfo.displayName = engineInfo.label + ":" + voiceInfo.countryDetails;
                } else {
                    voiceInfo.lang = voiceName;
                    voiceInfo.countryDetails = "";
                    voiceInfo.displayName = engineInfo.label;
                }
                synchronized (ttsVoiceByLanguage) {
                    if ( ! ttsVoiceByLanguage.containsKey(voiceInfo.lang)) {
                        ttsVoiceByLanguage.put(voiceInfo.lang, new ArrayList());
                    }
                    ttsVoiceByLanguage.get(voiceInfo.lang).add(voiceInfo);
                }
            }
        }
        activityResultNumber++;
        if (activityResultNumber == ttsEnginesNumber) {
            // Sort language list:
            ttsLanguages = new ArrayList(ttsVoiceByLanguage.keySet());
            Collections.sort(ttsLanguages, String.CASE_INSENSITIVE_ORDER);
            // sort voice lists by displayName:
            for(List<VoiceInfo> voices: ttsVoiceByLanguage.values()) {
                Collections.sort(voices, new Comparator<VoiceInfo>() {
                    @Override
                    public int compare(VoiceInfo lhs, VoiceInfo rhs) {
                        return lhs.displayName.compareTo(rhs.displayName);
                    }
                });
            }
            updateLanguageSpinnerData();
        }
    }

    private synchronized void updateLanguageSpinnerData() {
        Log.d(LOG_TAG, "updateLanguageSpinnerData");
        if ((ttsEngines != null)
                && (activityResultNumber == ttsEnginesNumber)
                && (spinnerLanguageAdapter != null))
        {
            spinnerLanguageAdapter.setNotifyOnChange(false);
            spinnerLanguageAdapter.clear();
            for(String lang: ttsLanguages) {
                spinnerLanguageAdapter.add(lang);
            }
            String voice = settings.getString(Settings.TTS_VOICE, "");
            String language = voice.indexOf("-") >= 0 ?
                    voice.substring(0, voice.indexOf("-")) : voice;
            spinnerLanguage.setSelection(ttsLanguages.indexOf(language));
            spinnerLanguageAdapter.notifyDataSetChanged();
            spinnerLanguageAdapter.setNotifyOnChange(true);
        }
    }

    private void onLanguageSelectionChanged() {
        Log.d(LOG_TAG, "onLanguageSelectionChanged");
        spinnerVoiceAdapter.setNotifyOnChange(false);
        spinnerVoiceAdapter.clear();
        int languagePosition = spinnerLanguage.getSelectedItemPosition();
        String language = ttsLanguages.get(languagePosition);
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
        Log.d(LOG_TAG, "onVoiceSelectionChanged");
        int voicePosition = spinnerVoice.getSelectedItemPosition();
        int languagePosition = spinnerLanguage.getSelectedItemPosition();
        if ((voicePosition >=0) && (languagePosition >=0)) {
            String language = ttsLanguages.get(languagePosition);
            VoiceInfo voiceInfo = ttsVoiceByLanguage.get(language).get(voicePosition);
            if (voiceInfo != null) {
                if (ttsService != null) {
                    ttsService.setEngineAndVoice(voiceInfo.engine.packageName, voiceInfo.name);
                }
                settings.setString(Settings.TTS_ENGINE, voiceInfo.engine.packageName);
                settings.setString(Settings.TTS_VOICE, voiceInfo.name);
                settings.setString(Settings.TTS_LANGUAGE_VOICE + voiceInfo.lang, voiceInfo.displayName);
            }
        }
    }

}

