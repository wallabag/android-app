package fr.gaulupeau.apps.Poche.tts;

import android.webkit.WebView;
import android.widget.ScrollView;

public interface TtsHost {

    JsTtsController getJsTtsController();

    WebView getWebView();

    ScrollView getScrollView();

    boolean previousArticle();

    boolean nextArticle();

}
