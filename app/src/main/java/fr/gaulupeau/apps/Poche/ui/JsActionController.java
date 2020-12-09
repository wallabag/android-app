package fr.gaulupeau.apps.Poche.ui;

import android.webkit.JavascriptInterface;

public class JsActionController {

    public interface Callback {
        void selectedText(String text);
    }

    private final Callback callback;

    public JsActionController(Callback callback) {
        this.callback = callback;
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void selectedText(String text) {
        if (callback != null) {
            callback.selectedText(text);
        }
    }

}
