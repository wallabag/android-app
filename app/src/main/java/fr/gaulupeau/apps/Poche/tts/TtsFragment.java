package fr.gaulupeau.apps.Poche.tts;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.ui.ReadArticleActivity;
import java.text.NumberFormat;

/**
 * Text To Speech (TTS) User Interface.
 */
public class TtsFragment
    extends Fragment
{
    private ReadArticleActivity readArticleActivity;
    private WebviewText webviewText;
    private Settings settings;
    private View viewTTSOption;
    private ImageButton btnTTSPlayStop;
    private Button btnTTSLanguage;
    private Button btnTTSVoice;
    private SeekBar seekBarTTSSpeed;
    private SeekBar seekBarTTSPitch;
    private SeekBar seekBarTTSVolume;
    private SeekBar seekBarTTSSleep;
    private TextView textViewTTSSpeed;
    private TextView textViewTTSPitch;
    private TextView textViewTTSVolume;
    private TextView textViewTTSSleep;

    private volatile TtsService ttsService;
    private volatile boolean documentParsed;
    private AudioManager audioManager;
    private BroadcastReceiver volumeChangeReceiver;
    private MediaControllerCompat.Callback mediaCallback;



    private static NumberFormat percentFormat;
    private static final String LOG_TAG = "TtsFragment";
    private static final float MAX_TTS_SPEED = 4.0F;
    private static final float MAX_TTS_PITCH = 2.0F;

    protected ServiceConnection serviceConnection;


    private static volatile TtsFragment instance;
    static TtsFragment getInstance() {
        return instance;
    }

    public static TtsFragment newInstance(boolean autoplay)
    {
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
    public void onAttach(Context context)
    {
        super.onAttach(context);
        this.readArticleActivity = ((ReadArticleActivity)getActivity());
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
    public void onDetach()
    {
        super.onDetach();
        readArticleActivity.unregisterReceiver(volumeChangeReceiver);
        readArticleActivity = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // Should use getArguments() to get Fragment instance arguments.
        this.settings = App.getInstance().getSettings();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }


    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        this.instance = this;
        View view = inflater.inflate(R.layout.fragment_tts, container, false);
        if (this.percentFormat == null)
        {
            this.percentFormat = NumberFormat.getPercentInstance();
            this.percentFormat.setMaximumFractionDigits(0);
        }
        this.viewTTSOption = view.findViewById(R.id.viewTTSOptions);
        if (!this.settings.getBoolean(Settings.TTS_OPTIONS_VISIBLE, false)) {
            this.viewTTSOption.setVisibility(View.GONE);
        }
        this.btnTTSPlayStop = ((ImageButton)view.findViewById(R.id.btnTTSPlayPause));
        this.btnTTSPlayStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ttsService != null) {
                    ttsService.playPauseCmd();
                }
            }
        });

        ImageButton btnTTSPrevious = (ImageButton)view.findViewById(R.id.btnTTSPrevious);
        btnTTSPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ttsService != null) {
                    ttsService.rewindCmd();
                }
            }
        });

        ImageButton btnTTSNext = (ImageButton)view.findViewById(R.id.btnTTSNext);
        btnTTSNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ttsService != null) {
                    ttsService.fastForwardCmd();
                }
            }

        });

        ImageButton btnTTSOptions = (ImageButton)view.findViewById(R.id.btnTTSOptions);
        btnTTSOptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTTSOptionsClicked((ImageButton) v);
            }
        });
        
        this.btnTTSLanguage = ((Button)view.findViewById(R.id.btnTTSLanguage));
        this.btnTTSLanguage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTTSLanguageClicked((Button) v);
            }
        });
        
        this.btnTTSVoice = ((Button)view.findViewById(R.id.btnTTSVoice));
        this.btnTTSVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTTSVoiceClicked((Button) v);
            }
        });

        CheckBox chkbxTTSContinue = (CheckBox)view.findViewById(R.id.chkbxTTSContinue);
        chkbxTTSContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToastMessage("Not implemented");
            }
        });

        this.textViewTTSSpeed = ((TextView)view.findViewById(R.id.textViewTTSSpeed));
        this.textViewTTSPitch = ((TextView)view.findViewById(R.id.textViewTTSPitch));
        this.textViewTTSVolume = ((TextView)view.findViewById(R.id.textViewTTSVolume));
        this.textViewTTSSleep = ((TextView)view.findViewById(R.id.textViewTTSSleep));

        this.seekBarTTSSpeed = ((SeekBar)view.findViewById(R.id.seekBarTTSSpeed));
        this.seekBarTTSSpeed.setMax(Integer.MAX_VALUE);
        this.seekBarTTSSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ttsSetSpeedFromSeekBar();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        this.seekBarTTSSpeed.setProgress((int) (this.settings.getFloat(Settings.TTS_SPEED, 1.0F) * this.seekBarTTSSpeed.getMax() / MAX_TTS_SPEED));
        
        this.seekBarTTSPitch = ((SeekBar)view.findViewById(R.id.seekBarTTSPitch));
        this.seekBarTTSPitch.setMax(Integer.MAX_VALUE);
        this.seekBarTTSPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ttsSetPitchFromSeekBar();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        this.seekBarTTSPitch.setProgress((int) (this.settings.getFloat(Settings.TTS_PITCH, 1.0F) * this.seekBarTTSPitch.getMax() / MAX_TTS_PITCH));

        this.seekBarTTSSleep = ((SeekBar)view.findViewById(R.id.seekBarTTSSleep));
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
        this.seekBarTTSVolume = ((SeekBar)view.findViewById(R.id.seekBarTTSVolume));
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

        //if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
        //{
        //    Locale locale = this.tts.getLanguage();
        //    btnTTSLanguage.setText(locale.getLanguage());
        //    btnTTSVoice.setText(locale.getCountry());
        //}
        //else
        //{
        //    Voice voice = tts.getVoice();
        //    Locale locale = voice.getLocale();
        //    btnTTSLanguage.setText(locale.getLanguage());
        //    btnTTSVoice.setText(voice.getName() + "," + locale.getCountry());
        //}

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
                        break;
                    case PlaybackStateCompat.STATE_ERROR:
                        showToastMessage("Text To Speech Error");
                        break;
                }
            }
        };

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                Log.d(LOG_TAG, "onServiceConnected");
                ttsService = ((TtsService.LocalBinder)binder).getService();
                ttsService.registerCallback(mediaCallback, new Handler());
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

        Intent intent = new Intent( getActivity(), TtsService.class );
        if (getArguments().getBoolean("autoplay")) {
            intent.setAction("PLAY");
        }
        getActivity().startService(intent);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Intent intent = new Intent( getContext(), TtsService.class );
        getActivity().stopService(intent);
        serviceConnection = null;
        this.instance = null;
    }

    @Override
    public void onStart() {
        Intent intent = new Intent( getActivity(), TtsService.class );
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        super.onStart();
    }

    @Override
    public void onStop() {
        getActivity().unbindService(serviceConnection);
        super.onStop();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (ttsService != null) {
            ttsService.setVisible(false);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        saveSettings();
        ttsService.setVisible(true);
    }


    private void saveSettings()
    {
        this.settings.setFloat(Settings.TTS_SPEED, getSeedBarValue());
        this.settings.setFloat(Settings.TTS_PITCH, getPitchBarValue());
        this.settings.setBoolean(Settings.TTS_OPTIONS_VISIBLE, this.viewTTSOption.getVisibility() == View.VISIBLE);
    }
    
    private float getSeedBarValue()
    {
        return MAX_TTS_SPEED * this.seekBarTTSSpeed.getProgress() / this.seekBarTTSSpeed.getMax();
    }
    
    private float getPitchBarValue()
    {
        return MAX_TTS_PITCH * this.seekBarTTSPitch.getProgress() / this.seekBarTTSPitch.getMax();
    }


    public void onTTSOptionsClicked(ImageButton btn)
    {
        if (viewTTSOption.getVisibility() == View.VISIBLE) {
            viewTTSOption.setVisibility(View.GONE);
        } else {
            viewTTSOption.setVisibility(View.VISIBLE);
        }
    }
    
    public void onTTSLanguageClicked(Button btn) {}
    
    public void onTTSVoiceClicked(Button btn) {}
    
    private void ttsSetSpeedFromSeekBar()
    {
        float value = getSeedBarValue();
        if (ttsService != null) {
            ttsService.setSpeed(value);
        }
        this.textViewTTSSpeed.setText(this.percentFormat.format(value));
    }
    
    private void ttsSetPitchFromSeekBar()
    {
        float value = getPitchBarValue();
        if (ttsService != null) {
            ttsService.setPitch(value);
        }
        this.textViewTTSPitch.setText(this.percentFormat.format(value));
    }

    public void onDocumentLoadStart(String domain, String title)
    {
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
        webviewText = new WebviewText(webView, scrollView);
        webviewText.parseWebviewDocument(new Runnable() {
            public void run() {
                documentParsed = true;
                if(ttsService!=null)
                {
                    ttsService.setTextInterface(webviewText);
                }
            }
        });
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


    private void showToastMessage(String text)
    {
        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
    }

}

