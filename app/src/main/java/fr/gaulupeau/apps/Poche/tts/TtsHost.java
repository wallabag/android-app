package fr.gaulupeau.apps.Poche.tts;

import android.webkit.WebView;

public interface TtsHost {

    JsTtsController getJsTtsController();

    WebView getWebView();

    int getScrollY();
    int getViewHeight();
    void scrollTo(int y);

    boolean previousArticle();

    boolean nextArticle();

}
