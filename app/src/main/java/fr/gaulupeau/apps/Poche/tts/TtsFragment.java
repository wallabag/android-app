package fr.gaulupeau.apps.Poche.tts;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.support.v4.app.Fragment;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;

/**
 * Text To Speech (TTS) User Interface.
 */
public class TtsFragment
    extends Fragment
    implements TextToSpeech.OnInitListener,
        AudioManager.OnAudioFocusChangeListener
{
    private TextToSpeech tts;
    private AudioManager audioManager;
    private ReadArticleActivity readArticleActivity;
    private Settings settings;
    private WebView webView;
    private ScrollView scrollView;
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
    private MediaPlayer mediaPlayerPageFlip;
    private BroadcastReceiver broadcastReceiver;

    private boolean isTTSInitialized;
    private boolean isDocumentParsed;
    private boolean isAudioFocusLost;
    private volatile State state = State.STOPED;
    static enum State {
        STOPED,
        WANT_TO_PLAY, // tts not initialized, document not parsed yet, or lost audio focus
        PLAYING,
    }
    private Vector<TextRange> textList;
    private volatile int textListSize;
    private volatile int textListCurrentIndex;
    private volatile char utteranceSequenceId;

    private static final int TTS_SPEAK_QUEUE_SIZE = 2;
    private static NumberFormat percentFormat;
    private static final String PLAY_TEXT = "▶";
    private static final String PAUSE_TEXT = " ▎▎";
    private static final String LOG_TAG = "TtsFragment";
    private static final String END_UTTERANCE_ID = "Wallabag TTS End";
    private static final float MAX_TTS_SPEED = 4.0F;
    private static final float MAX_TTS_PITCH = 2.0F;
    private final String TTS_WEBVIEW_LOG_CMD_HEADER = "TTS_CMD_" + getRandomText(4) + ":";
    private final String JAVASCRIPT_PARSE_DOCUMENT_TEXT = "" +
            "function nextDomElem(elem) {" +
            "    var result;" +
            "    if (elem.hasChildNodes() && elem.tagName != 'SCRIPT') {" +
            "        result = elem.firstChild;" +
            "    } else {" +
            "        result = elem.nextSibling;" +
            "        while((result == null) && (elem != null)) {" +
            "            elem = elem.parentNode;" +
            "            if (elem != null) {" +
            "                result = elem.nextSibling;" +
            "            }" +
            "        }" +
            "    }" +
            "    return result;" +
            "}" +
            "" +
            "function nextTextElem(elem) {" +
            "    while(elem = nextDomElem(elem)) {" +
            "        if ((elem.nodeType == 3) && (elem.textContent.trim().length > 0)) {" +
            "            break;" +
            "        }" +
            "    }" +
            "    return elem;" +
            "}" +
            "" +
            "function cmdStart() {" +
            "        console.log('" + TTS_WEBVIEW_LOG_CMD_HEADER + "start');" +
            "}" +
            "function cmdEnd() {" +
            "        console.log('" + TTS_WEBVIEW_LOG_CMD_HEADER + "end');" +
            "}" +
            "function cmdText(text, top, bottom) {" +
            "        console.log('" + TTS_WEBVIEW_LOG_CMD_HEADER + "' + top + ':' + bottom + ':' + text);" +
            "}" +
            "" +
            "function parseDocumentText() {" +
            "    var elem = document.getElementsByTagName('body')[0];" +
            "    var range = document.createRange();" +
            "    cmdStart();" +
            "    while(elem = nextTextElem(elem)) {" +
            "        range.selectNode(elem);" +
            "        var rect = range.getBoundingClientRect();" +
            "        var text = elem.textContent.trim();" +
            "        cmdText(text, rect.top, rect.bottom);" +
            "    }" +
            "    cmdEnd();" +
            "}";


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
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // Should use getArguments() to get Fragment instance arguments.
        this.settings = App.getInstance().getSettings();
        textList = new Vector<>();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
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
                onTTSPlayStopClicked();
            }
        });

        ImageButton btnTTSPrevious = (ImageButton)view.findViewById(R.id.btnTTSPrevious);
        btnTTSPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTTSPreviousClicked();
            }
        });

        ImageButton btnTTSNext = (ImageButton)view.findViewById(R.id.btnTTSNext);
        btnTTSNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTTSNextClicked();
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

        if (this.tts != null) {
            this.tts.stop();
            this.tts.shutdown();
        }
        this.isTTSInitialized = false;
        String engineName = this.settings.getString(Settings.TTS_ENGINE, "");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            this.tts = new TextToSpeech(view.getContext(), this);
        } else {
            this.tts = new TextToSpeech(view.getContext(), this, this.settings.getString(Settings.TTS_ENGINE, ""));
        }
        if (this.mediaPlayerPageFlip == null) {
            this.mediaPlayerPageFlip = MediaPlayer.create(getActivity(), R.raw.page_flip);
        }

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
        return view;
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        this.readArticleActivity = ((ReadArticleActivity)context);
        if (broadcastReceiver == null) {
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION")) {
                        updateVolumeDisplay();
                    }
                }
            };
            context.registerReceiver(broadcastReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        saveSettings();
    }
    
    private void saveSettings()
    {
        this.settings.setFloat(Settings.TTS_SPEED, ttsGetSpeed());
        this.settings.setFloat(Settings.TTS_PITCH, ttsGetPitch());
        this.settings.setBoolean(Settings.TTS_OPTIONS_VISIBLE, this.viewTTSOption.getVisibility() == View.VISIBLE);
    }
    
    private float ttsGetSpeed()
    {
        return MAX_TTS_SPEED * this.seekBarTTSSpeed.getProgress() / this.seekBarTTSSpeed.getMax();
    }
    
    private float ttsGetPitch()
    {
        return MAX_TTS_PITCH * this.seekBarTTSPitch.getProgress() / this.seekBarTTSPitch.getMax();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (this.tts != null)
        {
            isTTSInitialized = false;
            this.tts.stop();
            this.tts.shutdown();
            this.tts = null;
        }
        if (this.mediaPlayerPageFlip != null)
        {
            this.mediaPlayerPageFlip.release();
            this.mediaPlayerPageFlip = null;
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        readArticleActivity.unregisterReceiver(broadcastReceiver);
        readArticleActivity = null;
    }
    
    public void onTTSPlayStopClicked()
    {
        if (state == State.STOPED) {
            ttsTryToPlay();
        } else {
            ttsStop();
        }
    }
    
    private void ttsTryToPlay() {
        audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,  AudioManager.AUDIOFOCUS_GAIN);
        if (isTTSInitialized && isDocumentParsed && !isAudioFocusLost) {
            if (textListSize == 0) {
                showToastMessage("text parsing EMTPY !");
            }
            btnTTSPlayStop.setImageResource(R.drawable.ic_stop_24dp);
            state = State.PLAYING;
            ttsPlay();
        } else {
            state = State.WANT_TO_PLAY;
            btnTTSPlayStop.setImageResource(R.drawable.ic_more_horiz_24dp);
            if (!isDocumentParsed) {
                showToastMessage("Document not parsed");
            } else if (!isTTSInitialized) {
                showToastMessage("TTS not initialized");
            } else if (!isAudioFocusLost) {
                showToastMessage("Audio focus lost");
            }
        }
    }

    private void ttsSuspend()
    {
        if (state == State.PLAYING)
        {
            state = State.WANT_TO_PLAY;
            btnTTSPlayStop.setImageResource(R.drawable.ic_more_horiz_24dp);
            if(isTTSInitialized) {
                if (tts.stop() != TextToSpeech.SUCCESS) {}
            }
        }
    }

    public void ttsStop()
    {
        if (state != State.STOPED)
        {
            state = State.STOPED;
            if (btnTTSPlayStop != null) {
                btnTTSPlayStop.setImageResource(R.drawable.ic_play_arrow_24dp);
            }
            if(isTTSInitialized) {
                new Thread() {
                    @Override
                    public void run() {
                        if (tts.stop() != TextToSpeech.SUCCESS) {}
                        if (audioManager != null) {
                            audioManager.abandonAudioFocus(TtsFragment.this);
                        }
                    }
                }.start();
            }
        }
    }
    
    public void onTTSPreviousClicked()
    {
        Log.d(LOG_TAG, "onTTSPreviousClicked, textListCurrentIndex=" + textListCurrentIndex);
        //for(int i=textListCurrentIndex; (i>=0) && i>(textListCurrentIndex-8); i--) {
        //    TextRange t = textList.get(i);
        //    Log.d(LOG_TAG, " - " + i + " top=" + t.top + " bottom=" + t.bottom + "  " + t.text);
        //}
        int newIndex = textListCurrentIndex - 1;
        if ((newIndex >= 0) && (newIndex < textListSize)) {
            float originalTop = textList.get(newIndex+1).top;
            // Look for text's index that start on the previous line (its bottom < current top)
            while((newIndex > 0)
                    && (textList.get(newIndex).bottom >= originalTop))
            {
                newIndex = newIndex - 1;
            }
            Log.d(LOG_TAG, " => " + newIndex);
            if (newIndex > 0) {
                // If there is many text on the previous line, we want
                // the first on the line, so we look again for the text's index
                // on the previous of the previous line and select the following index.
                // This way clicking "Next" and "Previous" will be coherent.
                int prevPrevIndex = newIndex;
                float newTop = textList.get(prevPrevIndex).top;
                while((prevPrevIndex > 0)
                        && (textList.get(prevPrevIndex).bottom >= newTop))
                {
                    prevPrevIndex = prevPrevIndex - 1;
                    newIndex = prevPrevIndex + 1;
                    Log.d(LOG_TAG, " => " + newIndex);
                }
            }
            if (state == State.PLAYING) {
                ttsPlay(newIndex);
            } else {
                textListCurrentIndex = newIndex;
                ensureTextRangeVisibleOnScreen(newIndex, true);
            }
        } else {
            ensureTextRangeVisibleOnScreen(textListCurrentIndex, true);
        }
    }
    
    public void onTTSNextClicked()
    {
        Log.d(LOG_TAG, "onTTSNextClicked, textListCurrentIndex=" + textListCurrentIndex);
        //for(int i=textListCurrentIndex; i<(textListSize-1) && i<(textListCurrentIndex+6); i++) {
        //    TextRange t = textList.get(i);
        //    Log.d(LOG_TAG, " - " + i + " top=" + t.top + " bottom=" + t.bottom + "  " + t.text);
        //}
        int newIndex = textListCurrentIndex + 1;
        if ((newIndex >= 0) && (newIndex < textListSize)) {
            float originalBottom = textList.get(newIndex-1).bottom;
            // Look for text's index that start on the next line (its top >= current bottom)
            while((newIndex < (textListSize-1))
                    && (textList.get(newIndex).top < originalBottom))
            {
                newIndex = newIndex + 1;
            }
            Log.d(LOG_TAG, " => " + newIndex);
            if (state == State.PLAYING) {
                ttsPlay(newIndex);
            } else {
                ensureTextRangeVisibleOnScreen(newIndex, true);
                textListCurrentIndex = newIndex;
            }
        } else {
            ensureTextRangeVisibleOnScreen(textListCurrentIndex, true);
        }
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


    /**
     * TextToSpeech.OnInitListener.
     */
    @Override
    public void onInit(int status)
    {
        if (status == TextToSpeech.SUCCESS)
        {
            this.tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    onSpeakDone(utteranceId);
                }
            });
            isTTSInitialized = true;
            ttsSetSpeedFromSeekBar();
            ttsSetPitchFromSeekBar();
            if (state != State.STOPED) {
                ttsTryToPlay();
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            {
                Locale locale = this.tts.getLanguage();
                btnTTSLanguage.setText(locale.getLanguage());
                btnTTSVoice.setText(locale.getCountry());
            }
            else
            {
                Voice voice = tts.getVoice();
                Locale locale = voice.getLocale();
                btnTTSLanguage.setText(locale.getLanguage());
                btnTTSVoice.setText(voice.getName() + "," + locale.getCountry());
            }
        } else {
            this.isTTSInitialized = false;
            showToastMessage("TTS initialization Failed!");
        }
    }

    private int getFirstPlayIndex() {
        float currentTop = scrollView.getScrollY();
        float currentBottom = currentTop + scrollView.getHeight();
        int result = Math.min(textListCurrentIndex, textListSize - 1);
        TextRange textRange = textList.get(result);
        if ((textRange.bottom <= currentTop) || (textRange.top >= currentBottom)) {
            // current not displayed on screen, switch to the first text visible:
            result = textListSize - 1;
            for(int i=0; i<textListSize; i++) {
                if (textList.get(i).top > currentTop) {
                    result = i;
                    break;
                }
            }
        }
        return result;
    }

    private void ttsPlay() {
        ttsPlay(getFirstPlayIndex());
    }

    @Override
    public void onAudioFocusChange(final int focusChange) {
        if ((focusChange == AudioManager.AUDIOFOCUS_LOSS)
                || (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        {
            isAudioFocusLost = true;
            if (state == State.PLAYING) {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ttsSuspend();
                        }
                    });
                }
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            isAudioFocusLost = false;
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ttsTryToPlay();
                    }
                });
            }
        }
    }

    private void ttsPlay(int index) {
        Log.d(LOG_TAG, "ttsPlay " + index);
        // Increment the utteranceSequenceId so that call to onSpeakDone
        // of the previous play sequence will not interfere with the following one
        utteranceSequenceId = (char)((utteranceSequenceId + 1) % 256);
        textListCurrentIndex = index;
        ensureTextRangeVisibleOnScreen(textListCurrentIndex, true);
        state = State.PLAYING;
        ttsSpeak(textList.get(textListCurrentIndex).text, TextToSpeech.QUEUE_FLUSH, utteranceSequenceId + Integer.toString(textListCurrentIndex));
        for(int i = 1; i<= TTS_SPEAK_QUEUE_SIZE; i++) {
            ttsSpeakQueueAddTextIndex(textListCurrentIndex + i);
        }
    }


    private void ensureTextRangeVisibleOnScreen(int index, boolean canMoveBackward) {
        TextRange textRange = textList.get(index);
        if ((scrollView != null) &&
                        ((textRange.bottom > scrollView.getScrollY() + scrollView.getHeight())
                                || (canMoveBackward && (textRange.top < scrollView.getScrollY())))) {
            scrollView.smoothScrollTo(0, (int) textRange.top);
        }
    }
    
    private void ttsSetSpeedFromSeekBar()
    {
        float value = ttsGetSpeed();
        if (isTTSInitialized) {
            tts.setSpeechRate(value);
            if (state == State.PLAYING) {
                ttsPlay();
            }
        }
        this.textViewTTSSpeed.setText(this.percentFormat.format(value));
    }
    
    private void ttsSetPitchFromSeekBar()
    {
        float value = ttsGetPitch();
        if (isTTSInitialized) {
            tts.setPitch(value);
            if (state == State.PLAYING) {
                ttsPlay();
            }
        }
        this.textViewTTSPitch.setText(this.percentFormat.format(value));
    }

    private void ttsSpeakQueueAddTextIndex(final int index) {
        if (index < textListSize) {
            ttsSpeak(textList.get(index).text, TextToSpeech.QUEUE_ADD, utteranceSequenceId + Integer.toString(index));
        }
    }

    private void ttsSpeak(String text, int queue, String utteranceId)
    {
        if (this.isTTSInitialized)
        {
            HashMap<String, String> params = null;
            if (utteranceId != null)
            {
                params = new HashMap();
                params.put("utteranceId", utteranceId);
            }
            Log.d("TtsFragment.ttsSpeak", utteranceId + ":" + text);
            this.tts.speak(text, queue, params);
        }
    }
    
    public void onSpeakDone(String utteranceId)
    {
        Log.d(LOG_TAG, "onSpeakDone " + utteranceId);
        if ((state == State.PLAYING)
                && (utteranceId.length() > 0)
                && (utteranceId.charAt(0) == utteranceSequenceId))
        {
            final int index = Integer.parseInt(utteranceId.substring(1));
            textListCurrentIndex = index + 1;
            if (index == textListSize - 1) {
                // this is the end
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ttsStop();
                    }
                });
                playPageFlipSound();
            } else {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ensureTextRangeVisibleOnScreen(index + 1, false);
                    }
                });
                ttsSpeakQueueAddTextIndex(index + 1 + TTS_SPEAK_QUEUE_SIZE);
            }
        }
    }



    private float convertWebviewToScreenY(float y)
    {
        return y * this.webView.getHeight() / this.webView.getContentHeight();
    }
    
    private float convertScreenToWebviewY(float y)
    {
        return y * this.webView.getContentHeight() / this.webView.getHeight();
    }
    

    private void playPageFlipSound()
    {
        this.mediaPlayerPageFlip.start();
    }


    public void onDocumentLoadStart()
    {
        ttsStop();
        this.isDocumentParsed= false;
        Log.d(LOG_TAG, "onDocumentLoadStart");
    }
    
    public void onDocumentLoadFinished() {
        Log.d(LOG_TAG, "onDocumentLoadFinished");
        this.parseWebviewDocument();
    }
    
    public void setWebView(WebView webView)
    {
        this.webView = webView;
    }
    
    public void setScrollView(ScrollView scrollView)
    {
        this.scrollView = scrollView;
    }


    private void parseWebviewDocument()
    {
        Log.d(LOG_TAG, "parseWebviewDocument");
        if (webView != null)
        {
            //new Thread() {
            //    @Override
            //    public void run() {
                    webView.loadUrl("javascript:" + JAVASCRIPT_PARSE_DOCUMENT_TEXT);
                    webView.loadUrl("javascript:parseDocumentText();");
                    //The javascript will log the parse results then onWebviewConsoleMessage() will be called.
            //    }
            //}.start();
        }
        else
        {
            Log.e(LOG_TAG, "No WebView set");
        }
    }

    private void onDocumentParseStart() {
        Log.d(LOG_TAG, "onDocumentParseStart");
        textListSize = 0;
    }
    private void onDocumentParseEnd() {
        Log.d(LOG_TAG, "onDocumentParseEnd");
        while(textList.size() > textListSize) {
            textList.remove(textList.size() - 1);
        }
        this.isDocumentParsed= true;
        if ((state != State.STOPED) || getArguments().getBoolean("autoplay")) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ttsTryToPlay();
                }
            });
        }
    }

    private void onDocumentParseItem(String text, float top, float bottom) {
        top = convertWebviewToScreenY(top);
        bottom = convertWebviewToScreenY(bottom);
        Log.d(LOG_TAG, "onDocumentParseItem " + top + " " + bottom + " " + text);
        TextRange textRange;
        if (textList.size() > textListSize) {
            textRange = textList.get(textListSize);
            textRange.text = text;
            textRange.top = top;
            textRange.bottom = bottom;
        } else {
            textRange = new TextRange(text, top, bottom);
            textList.add(textRange);
        }
        textListSize = textListSize + 1;
    }
    
    public void onWebviewConsoleMessage(ConsoleMessage cm)
    {
        // It is insecure to use WebView.addJavascriptInterface with older
        // version of Android, so we use console.log() instead.
        // We catch the command send through the log done by the code
        // JAVASCRIPT_PARSE_DOCUMENT_TEXT
        if (cm.messageLevel() == ConsoleMessage.MessageLevel.LOG)
        {
            String message = cm.message();
            if (message.startsWith(TTS_WEBVIEW_LOG_CMD_HEADER))
            {
                String content = message.substring(TTS_WEBVIEW_LOG_CMD_HEADER.length());
                if (content.equals("start")) {
                    onDocumentParseStart();
                } else if (content.equals("end")) {
                    onDocumentParseEnd();
                } else {
                    int separator1 = content.indexOf(':');
                    int separator2 = content.indexOf(':', separator1 + 1);
                    float top = Float.parseFloat(content.substring(0, separator1));
                    float bottom = Float.parseFloat(content.substring(separator1 + 1, separator2));
                    String text= content.substring(separator2 + 1);
                    onDocumentParseItem(text, top, bottom);
                }
            }
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

    private static StringBuilder getRandomText(int length) {
        StringBuilder result = new StringBuilder(length);
        for(int i=0;i<length;i++) {
            result.append((char)(32 + Math.random() * (127-32)));
        }
        return result;
    }

}

