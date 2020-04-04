package fr.gaulupeau.apps.Poche.tts;

import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;
import android.widget.ScrollView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.StorageHelper;

/**
 * TextInterface to navigate in a WebView.
 */
class WebViewText implements TextInterface {

    private static final String TAG = WebViewText.class.getSimpleName();

    private static final String JS_PARSE_DOCUMENT_SCRIPT
            = StorageHelper.readRawString(R.raw.tts_parser);

    private final Handler handler;

    private TtsHost ttsHost;
    private final WebView webView;
    private final ScrollView scrollView;

    private final List<GenericItem> textList = new ArrayList<>();

    private volatile int current;

    private Integer storedScrollPosition;

    private Runnable readFinishedCallback;
    private Runnable parsingFinishedCallback;

    WebViewText(TtsHost ttsHost) {
        this.ttsHost = ttsHost;
        webView = ttsHost.getWebView();
        scrollView = ttsHost.getScrollView();

        handler = new Handler();
    }

    void setTtsHost(TtsHost ttsHost) {
        this.ttsHost = ttsHost;
    }

    void setReadFinishedCallback(Runnable readFinishedCallback) {
        this.readFinishedCallback = readFinishedCallback;
    }

    void parseWebViewDocument(Runnable callback) {
        Log.d(TAG, "parseWebViewDocument()");

        parsingFinishedCallback = callback;

        ttsHost.getJsTtsController().setWebViewText(this);
        webView.evaluateJavascript("javascript:" + JS_PARSE_DOCUMENT_SCRIPT
                + ";parseDocumentText();", null);
    }

    void onDocumentParseStart() {
        Log.d(TAG, "onDocumentParseStart()");
    }

    void onDocumentParseEnd() {
        Log.d(TAG, "onDocumentParseEnd()");

        if (parsingFinishedCallback != null) {
            parsingFinishedCallback.run();
        }
    }

    void onDocumentParseText(String text, float top, float bottom, String extras) {
        top = convertWebViewToScreenY(top);
        bottom = convertWebViewToScreenY(bottom);

        if (BuildConfig.DEBUG) {
            Log.v(TAG, String.format("onDocumentParseText(%s, %f, %f)", text, top, bottom));
        }

        addItem(new TextItem(text, top, bottom, parseTextItemExtras(extras)));
    }

    private List<TextItem.Extra> parseTextItemExtras(String extrasString) {
        if (TextUtils.isEmpty(extrasString)) return null;

        List<TextItem.Extra> result = new ArrayList<>();

        try {
            JSONArray jsonArray = new JSONArray(extrasString);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                TextItem.Extra.Type type = TextItem.Extra.Type.getType(
                        jsonObject.getString("type"));

                TextItem.Extra extra = new TextItem.Extra(type,
                        jsonObject.getInt("start"), jsonObject.getInt("end"));

                result.add(extra);
            }
        } catch (Exception e) {
            Log.w(TAG, "parseExtras()", e);
        }

        return result;
    }

    void onDocumentParseImage(String altText, String title, String src, float top, float bottom) {
        top = convertWebViewToScreenY(top);
        bottom = convertWebViewToScreenY(bottom);

        if (BuildConfig.DEBUG) {
            Log.v(TAG, String.format("onDocumentParseImage(%s, %s, %s, %f, %f)",
                    altText, title, src, top, bottom));
        }

        addItem(new ImageItem(altText, title, src, top, bottom));
    }

    private void addItem(GenericItem item) {
        GenericItem prevItem = !textList.isEmpty() ? textList.get(textList.size() - 1) : null;

        item.timePosition = item.approximateDuration()
                + (prevItem != null ? prevItem.timePosition : 0);

        textList.add(item);
    }

    @Override
    public GenericItem getItem(int relativeIndex) {
        //Log.d(TAG, "getText(" + relativeIndex + "), current=" + current);
        int i = current + relativeIndex;
        if (i >= 0 && i < textList.size()) {
            return textList.get(i);
        } else {
            return null;
        }
    }

    /**
     * Go to the next text item.
     *
     * @return true if current item changed (not already the end).
     */
    @Override
    public boolean next() {
        //Log.d(TAG, "next, current=" + current);
        boolean result;
        if (current < textList.size() - 1) {
            current++;
            result = true;
        } else {
            handler.post(readFinishedCallback);
            result = false;
        }
        ensureTextRangeVisibleOnScreen(false);
        return result;
    }

    /**
     * Fast forward to the next TextItem located below the current one (next line).
     *
     * @return true if current item changed (not already the beginning).
     */
    @Override
    public boolean fastForward() {
        //Log.d(TAG, "fastForward, current=" + current);
        boolean result;
        int newIndex = current + 1;
        if (newIndex > 0 && newIndex < textList.size()) {
            float originalBottom = textList.get(newIndex - 1).bottom;
            // Look for text's index that start on the next line (its top >= current bottom)
            while (newIndex < textList.size() - 1
                    && textList.get(newIndex).top < originalBottom) {
                newIndex++;
            }
            Log.d(TAG, "fastForward " + current + " => " + newIndex);
            current = newIndex;
            result = true;
        } else {
            result = false;
        }
        ensureTextRangeVisibleOnScreen(true);
        return result;
    }

    /**
     * Rewind to the previous TextItem located above the current one (previous line).
     *
     * @return true if current item changed (not already the end).
     */
    @Override
    public boolean rewind() {
        //Log.d(TAG, "rewind, current=" + current);
        boolean result;
        int newIndex = current - 1;
        if (newIndex >= 0 && newIndex + 1 < textList.size()) {
            float originalTop = textList.get(newIndex + 1).top;
            // Look for text's index that start on the previous line (its bottom < current top)
            while (newIndex > 0 && textList.get(newIndex).bottom >= originalTop) {
                newIndex--;
            }
            if (newIndex > 0) {
                // If there is many text on the previous line, we want
                // the first on the line, so we look again for the text's index
                // on the previous of the previous line and select the following index.
                // This way clicking "Next" and "Previous" will be coherent.
                int prevPrevIndex = newIndex;
                float newTop = textList.get(prevPrevIndex).top;
                while (prevPrevIndex > 0 && textList.get(prevPrevIndex).bottom >= newTop) {
                    prevPrevIndex--;
                }
                newIndex = prevPrevIndex + 1;
            }
            Log.d(TAG, "rewind " + current + " => " + newIndex);
            current = newIndex;
            result = true;
        } else {
            result = false;
        }
        ensureTextRangeVisibleOnScreen(true);
        return result;
    }

    @Override
    public boolean skipToNext() {
        return ttsHost != null && ttsHost.nextArticle();
    }

    @Override
    public boolean skipToPrevious() {
        return ttsHost != null && ttsHost.previousArticle();
    }

    @Override
    public void restoreFromStart() {
        Log.d(TAG, "restoreFromStart -> current = 0");
        current = 0;
    }

    @Override
    public void storeCurrent() {
        storedScrollPosition = scrollView.getScrollY();
    }

    @Override
    public void restoreCurrent() {
        if (storedScrollPosition != null) {
            int position = storedScrollPosition;
            storedScrollPosition = null;

            if (position == scrollView.getScrollY()) {
                // no scrolling has been done since pause, don't restore anything
                return;
            }
        }

        float currentTop = scrollView.getScrollY();
        float currentBottom = currentTop + scrollView.getHeight();
        int result = Math.min(current, textList.size() - 1);
        GenericItem textItem = textList.get(result);
        if (textItem.bottom <= currentTop || textItem.top >= currentBottom) {
            // current not displayed on screen, switch to the first text visible:
            result = textList.size() - 1;
            for (int i = 0; i < textList.size(); i++) {
                if (textList.get(i).top > currentTop) {
                    result = i;
                    break;
                }
            }
        }
        current = result;
        Log.d(TAG, "restoreCurrent -> current = " + current);
    }

    @Override
    public long getTime() {
        long result = -1;
        if (current > 0) {
            result = textList.get(current - 1).timePosition;
        }
        return result;
    }

    @Override
    public long getTotalDuration() {
        long result = -1;
        if (textList.size() > 0) {
            result = textList.get(textList.size() - 1).timePosition;
        }
        return result;
    }

    private void ensureTextRangeVisibleOnScreen(boolean canMoveBackward) {
        GenericItem item = textList.get(current);
        if (scrollView == null) return;
        if (item.bottom > scrollView.getScrollY() + scrollView.getHeight()
                || canMoveBackward && item.top < scrollView.getScrollY()) {
            handler.post(() -> scrollView.smoothScrollTo(0, (int) item.top));
        }
    }

    private float convertWebViewToScreenY(float y) {
        return y * this.webView.getHeight() / this.webView.getContentHeight();
    }

}
