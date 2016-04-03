package fr.gaulupeau.apps.Poche.tts;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
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
 * TTS User Interface.
 */
public class TtsFragment
  extends Fragment
  implements TextToSpeech.OnInitListener
{
  private ReadArticleActivity readArticleActivity;
  private Settings settings;
  private WebView webView;
  private ScrollView scrollView;
  private View viewTTSOption;
  private Button btnTTSPlayPause;
  private Button btnTTSLanguage;
  private Button btnTTSVoice;
  private TextToSpeech tts;
  private SeekBar seekBarTTSSpeed;
  private SeekBar seekBarTTSPitch;
  private SeekBar seekBarTTSVolume;
  private SeekBar seekBarTTSSleep;
  private TextView textViewTTSSpeed;
  private TextView textViewTTSPitch;
  private TextView textViewTTSVolume;
  private TextView textViewTTSSleep;
  private MediaPlayer mediaPlayerPageFlip;
  private boolean isTTSInitialized;
  private boolean isPlaying;
  private boolean isDocumentParsed;
  private boolean autoplay;
  private Vector<TextRange> textList;
  private volatile int textListSize;
  private volatile int textListCurrentIndex;
  private static final int TTS_SPEAK_QUEUE_SIZE = 2;
  private NumberFormat percentFormat;
  private static final String PLAY_TEXT = "▶";
  private static final String PAUSE_TEXT = " ▎▎";
  private static final String LOG_TAG = "TtsFragment";
  private static final String END_UTTERANCE_ID = "Wallabag TTS End";
  private static final float MAX_TTS_SPEED = 4.0F;
  private static final float MAX_TTS_PITCH = 2.0F;
  private final String TTS_WEBVIEW_LOG_CMD_HEADER = "TTS_CMD_" + getRandomText(4) + ":";
  private final String JAVASCRIPT_PARSE_DOCUMENT_TEXT = "" +
          "function nextDomElem(elem) {" +
          "  var result;" +
          "  if (elem.hasChildNodes() && elem.tagName != 'SCRIPT') {" +
          "    result = elem.firstChild;" +
          "  } else {" +
          "    result = elem.nextSibling;" +
          "    while((result == null) && (elem != null)) {" +
          "      elem = elem.parentNode;" +
          "      if (elem != null) {" +
          "        result = elem.nextSibling;" +
          "      }" +
          "    }" +
          "  }" +
          "  return result;" +
          "}" +
          "" +
          "function nextTextElem(elem) {" +
          "  while(elem = nextDomElem(elem)) {" +
          "    if ((elem.nodeType == 3) && (elem.textContent.trim().length > 0)) {" +
          "      break;" +
          "    }" +
          "  }" +
          "  return elem;" +
          "}" +
          "" +
          "function cmdStart() {" +
          "    console.log('" + TTS_WEBVIEW_LOG_CMD_HEADER + "start');" +
          "}" +
          "function cmdEnd() {" +
          "    console.log('" + TTS_WEBVIEW_LOG_CMD_HEADER + "end');" +
          "}" +
          "function cmdText(text, top, bottom) {" +
          "    console.log('" + TTS_WEBVIEW_LOG_CMD_HEADER + "' + top + ':' + bottom + ':' + text);" +
          "}" +
          "" +
          "function parseDocumentText() {" +
          "  var elem = document.getElementsByTagName('body')[0];" +
          "  var range = document.createRange();" +
          "  cmdStart();" +
          "  while(elem = nextTextElem(elem)) {" +
          "    range.selectNode(elem);" +
          "    var rect = range.getBoundingClientRect();" +
          "    var text = elem.textContent.trim();" +
          "    cmdText(text, rect.top, rect.bottom);" +
          "  }" +
          "  cmdEnd();" +
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
    this.autoplay = getArguments().getBoolean("autoplay");
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
  {
    View view = inflater.inflate(R.layout.fragment_tts, container, false);
    if (this.percentFormat == null)
    {
      this.percentFormat = NumberFormat.getPercentInstance();
      this.percentFormat.setMinimumFractionDigits(0);
      this.percentFormat.setMaximumFractionDigits(0);
      this.percentFormat.setMinimumIntegerDigits(3);
    }
    this.viewTTSOption = view.findViewById(R.id.viewTTSOptions);
    if (!this.settings.getBoolean(Settings.TTS_OPTIONS_VISIBLE, false)) {
      this.viewTTSOption.setVisibility(View.GONE);
    }
    this.btnTTSPlayPause = ((Button)view.findViewById(R.id.btnTTSPlayPause));
    this.btnTTSPlayPause.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            onTTSPlayPauseClicked();
          }
    });
    if (autoplay) {
      btnTTSPlayPause.setText(" ▎▎");
      isPlaying = true;
    }


    Button btnTTSStop = (Button)view.findViewById(R.id.btnTTSStop);
    btnTTSStop.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onTTSStopClicked();
      }
    });
    
    Button btnTTSPrevious = (Button)view.findViewById(R.id.btnTTSPrevious);
    btnTTSPrevious.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onTTSPreviousClicked();
      }
    });
    
    Button btnTTSNext = (Button)view.findViewById(R.id.btnTTSNext);
    btnTTSNext.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onTTSNextClicked();
      }
    });
    
    Button btnTTSOptions = (Button)view.findViewById(R.id.btnTTSOptions);
    btnTTSOptions.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onTTSOptionsClicked((Button) v);
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
    
    Switch switchTTSContinue = (Switch)view.findViewById(R.id.switchTTSContinue);
    switchTTSContinue.setOnClickListener(new View.OnClickListener() {
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
      public void onStartTrackingTouch(SeekBar seekBar) {

      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {

      }
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
      public void onStartTrackingTouch(SeekBar seekBar) {

      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {

      }
    });
    
    this.seekBarTTSPitch.setProgress((int) (this.settings.getFloat(Settings.TTS_PITCH, 1.0F) * this.seekBarTTSPitch.getMax() / MAX_TTS_PITCH));
    
    this.seekBarTTSVolume = ((SeekBar)view.findViewById(R.id.seekBarTTSVolume));
    this.seekBarTTSVolume.setMax(Integer.MAX_VALUE);
    this.seekBarTTSVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

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
    synchronized (this) {
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
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
        this.tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

          @Override
          public void onStart(String utteranceId) {
            onSpeakStart(utteranceId);
          }

          @Override
          public void onDone(String utteranceId) {
            onSpeakDone(utteranceId);
          }

          @Override
          public void onError(String utteranceId) {
            onSpeakError(utteranceId);
          }
        });
      }
    }
    if (this.mediaPlayerPageFlip == null) {
      this.mediaPlayerPageFlip = MediaPlayer.create(getActivity(), R.raw.page_flip);
    }
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
  public synchronized void onDestroy()
  {
    super.onDestroy();
    if (this.tts != null)
    {
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
    readArticleActivity = null;
  }
  
  public synchronized void onTTSPlayPauseClicked()
  {
    if (this.isPlaying)
    {
      btnTTSPlayPause.setText("▶");
      isPlaying = false;
      if (isTTSInitialized) {
        ttsPause();
      }
    }
    else
    {
      btnTTSPlayPause.setText(" ▎▎");
      isPlaying = true;
      if (isTTSInitialized && isDocumentParsed) {
        ttsPlay();
      }
    }
  }
  
  public synchronized void onTTSStopClicked()
  {
    stopTTS();
    if (this.readArticleActivity != null) {
      this.readArticleActivity.toggleTTS(false);
    }
  }
  
  public synchronized void stopTTS()
  {
    if (isPlaying)
    {
      btnTTSPlayPause.setText("▶");
      isPlaying = false;
      if(isTTSInitialized) {
        if (tts.stop() != TextToSpeech.SUCCESS) {}
      }
    }
  }
  
  public void onTTSPreviousClicked()
  {
    if (textListCurrentIndex > 0) {
      if (isPlaying && isTTSInitialized && isDocumentParsed) {
          ttsPause();
          ttsPlay(Math.max(0, textListCurrentIndex - 1));
      } else {
        textListCurrentIndex = Math.max(0, textListCurrentIndex - 1);
        if (isDocumentParsed) {
          ensureTextRangeVisibleOnScreen(textListCurrentIndex, true);
        }
      }
    }
  }
  
  public void onTTSNextClicked()
  {
    if (textListCurrentIndex < textListSize - 1) {
      if (isPlaying && isTTSInitialized && isDocumentParsed) {
        ttsPause();
        ttsPlay(Math.min(textListCurrentIndex + 1, textListSize - 1));
      } else {
        textListCurrentIndex = Math.min(textListCurrentIndex + 1, textListSize - 1);
        if (isDocumentParsed) {
          ensureTextRangeVisibleOnScreen(textListCurrentIndex, true);
        }
      }
    }
  }
  
  public void onTTSOptionsClicked(Button btn)
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
  public synchronized void onInit(int status)
  {
    if (status == TextToSpeech.SUCCESS)
    {
      isTTSInitialized = true;
      ttsSetSpeedFromSeekBar();
      ttsSetPitchFromSeekBar();
      if ((isPlaying) && (this.isDocumentParsed)) {
        ttsPlay();
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
    }
    else
    {
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

  private synchronized void ttsPlay() {
    ttsPlay(getFirstPlayIndex());
  }

  private synchronized void ttsPlay(int index) {
    Log.d(LOG_TAG, "ttsPlay " + index);
    textListCurrentIndex = index;
    ensureTextRangeVisibleOnScreen(textListCurrentIndex, true);
    ttsSpeak(textList.get(textListCurrentIndex).text, TextToSpeech.QUEUE_FLUSH, Integer.toString(textListCurrentIndex));
    for(int i=1; i<= TTS_SPEAK_QUEUE_SIZE; i++) {
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
  
  private void ttsPause()
  {
    if (tts.stop() == TextToSpeech.SUCCESS) {}
  }
  
  private void ttsSetSpeedFromSeekBar()
  {
    float value = ttsGetSpeed();
    if (isTTSInitialized) {
      tts.setSpeechRate(value);
      if (isPlaying && isTTSInitialized && isDocumentParsed) {
        ttsPause();
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
      if (isPlaying && isTTSInitialized && isDocumentParsed) {
        ttsPause();
        ttsPlay();
      }
    }
    this.textViewTTSPitch.setText(this.percentFormat.format(value));
  }

  private void ttsSpeakQueueAddTextIndex(int index) {
    if (index < textListSize) {
      ttsSpeak(textList.get(index).text, TextToSpeech.QUEUE_ADD, Integer.toString(index));
    }
  }

  private synchronized void ttsSpeak(String text, int queue, String uteranceId)
  {
    if (this.isTTSInitialized)
    {
      HashMap<String, String> params = null;
      if (uteranceId != null)
      {
        params = new HashMap();
        params.put("utteranceId", uteranceId);
      }
      this.tts.speak(text, queue, params);
      Log.d("TtsFragment.ttsSpeak", text);
    }
  }
  
  public void onSpeakStart(final String utteranceId)
  {
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        textListCurrentIndex = Integer.parseInt(utteranceId);
        ensureTextRangeVisibleOnScreen(textListCurrentIndex, false);
      }
    });
  }

  public void onSpeakError(String utteranceId) {}



  public void onSpeakDone(final String utteranceId)
  {
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Log.d(LOG_TAG, "onSpeakDone " + utteranceId);
        int index = Integer.parseInt(utteranceId);
        if (index == textListSize - 1) {
          stopTTS();
          playPageFlipSound();
        } else {
          ttsSpeakQueueAddTextIndex(index + 1 + TTS_SPEAK_QUEUE_SIZE);
        }
      }
    });
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
  

  public synchronized void onDocumentLoadStart()
  {
    stopTTS();
    this.isDocumentParsed= false;
    Log.d(LOG_TAG, "onDocumentLoadStart");
  }
  
  public synchronized void onDocumentLoadFinished() {
    Log.d(LOG_TAG, "onDocumentLoadFinished");
    this.ttsParse();
  }
  
  public void setWebView(WebView webView)
  {
    this.webView = webView;
  }
  
  public void setScrollView(ScrollView scrollView)
  {
    this.scrollView = scrollView;
  }


  private void ttsParse()
  {
    Log.d(LOG_TAG, "ttsParse");
    if (webView != null)
    {
      //new Thread() {
      //  @Override
      //  public void run() {
          webView.loadUrl("javascript:" + JAVASCRIPT_PARSE_DOCUMENT_TEXT);
          webView.loadUrl("javascript:parseDocumentText();");
          //The javascript will log the parse results then onWebviewConsoleMessage() will be called.
      //  }
      //}.start();
    }
    else
    {
      Log.e(LOG_TAG, "No WebView set");
    }
  }

  private void onTtsParseStart() {
    Log.d(LOG_TAG, "onTtsParseStart");
    textListSize = 0;
  }
  private void onTtsParseEnd() {
    Log.d(LOG_TAG, "onTtsParseEnd");
    while(textList.size() > textListSize) {
      textList.remove(textList.size() - 1);
    }
    this.isDocumentParsed= true;
    if (isPlaying && isTTSInitialized) {
      ttsPlay();
    }
  }
  private void onTtsParseText(String text, float top, float bottom) {
    top = convertWebviewToScreenY(top);
    bottom = convertWebviewToScreenY(bottom);
    Log.d(LOG_TAG, "onTtsParseText " + top + " " + bottom + " " + text);
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
          onTtsParseStart();
        } else if (content.equals("end")) {
          onTtsParseEnd();
        } else {
          int separator1 = content.indexOf(':');
          int separator2 = content.indexOf(':', separator1 + 1);
          float top = Float.parseFloat(content.substring(0, separator1));
          float bottom = Float.parseFloat(content.substring(separator1 + 1, separator2));
          String text= content.substring(separator2 + 1);
          onTtsParseText(text, top, bottom);
        }
      }
    }
  }

  private StringBuilder getRandomText(int length) {
    StringBuilder result = new StringBuilder(length);
    for(int i=0;i<length;i++) {
      result.append((char)(32 + Math.random() * (127-32)));
    }
    return result;
  }

  private void showToastMessage(String text)
  {
    Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
  }

}

