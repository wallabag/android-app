package fr.gaulupeau.apps.Poche.tts;

import android.os.Handler;
import android.util.Log;
import android.webkit.JavascriptInterface;

public class JsTtsController {

    private static final String TAG = JsTtsController.class.getSimpleName();

    private final Handler handler;

    private WebViewText webViewText;

    public JsTtsController() {
        this.handler = new Handler();
    }

    void setWebViewText(WebViewText webViewText) {
        this.webViewText = webViewText;
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onStart() {
        post(() -> webViewText.onDocumentParseStart());
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onEnd() {
        post(() -> webViewText.onDocumentParseEnd());
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onText(String text, String topString, String bottomString, String extras) {
        post(() -> webViewText.onDocumentParseText(text,
                Float.parseFloat(topString), Float.parseFloat(bottomString), extras));
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onImage(String altText, String title, String src,
                        String topString, String bottomString) {
        post(() -> webViewText.onDocumentParseImage(altText, title, src,
                Float.parseFloat(topString), Float.parseFloat(bottomString)));
    }

    private void post(Runnable runnable) {
        if (webViewText != null) {
            handler.post(runnable);
        } else {
            Log.w(TAG, "post() webViewText is null");
        }
    }

}
