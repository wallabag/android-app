package fr.gaulupeau.apps.Poche.tts;

import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;

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

    private final Handler handler = new Handler();

    private TtsConverter ttsConverter;

    private TtsHost ttsHost;

    private final List<GenericItem> textList = new ArrayList<>();

    private int current;

    private Integer storedScrollPosition;

    private Runnable readFinishedCallback;
    private Runnable parsingFinishedCallback;

    WebViewText(TtsConverter ttsConverter, TtsHost ttsHost) {
        this.ttsConverter = ttsConverter;
        this.ttsHost = ttsHost;
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
        ttsHost.getWebView().loadUrl("javascript:" + JS_PARSE_DOCUMENT_SCRIPT);
        ttsHost.getWebView().loadUrl("javascript:parseDocumentText()");
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

        item.timePosition = ttsConverter.approximateDuration(item)
                + (prevItem != null ? prevItem.timePosition : 0);

        textList.add(item);
    }

    @Override
    public synchronized int getCurrentIndex() {
        return current;
    }

    @Override
    public GenericItem getItem(int index) {
        if (index >= 0 && index < textList.size()) {
            return textList.get(index);
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
    public synchronized boolean next() {
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

    @Override
    public synchronized boolean rewind(long desiredTimeToRewind,
                                       int currentIndex, long progressInCurrentItem) {
        int startIndex = current;
        if (currentIndex != startIndex) progressInCurrentItem = 0;

/*
        if (startIndex == 0 && progressInCurrentItem < desiredTimeToRewind / 10) {
            Log.d(TAG, "rewind() no time to rewind");
            return false;
        }
*/

        int index = startIndex;
        long timeToRewind = desiredTimeToRewind - progressInCurrentItem;

        while (timeToRewind > 0 && index > 0) {
            int newIndex = index - 1;
            long newTimeToRewind = timeToRewind - itemDuration(newIndex);
            long alreadyRewound = desiredTimeToRewind - timeToRewind;

            if (newTimeToRewind > 0 || alreadyRewound < desiredTimeToRewind / 2) {
                index = newIndex;
                timeToRewind = newTimeToRewind;
            } else {
                break;
            }
        }

        Log.d(TAG, "rewind() " + startIndex + " => " + index);
        current = index;

        ensureTextRangeVisibleOnScreen(true);

        return true;
    }

    @Override
    public synchronized boolean fastForward(long desiredTimeToSkip,
                                            int currentIndex, long progressInCurrentItem) {
        int startIndex = current;
        if (currentIndex != startIndex) progressInCurrentItem = 0;

        if (startIndex == textList.size() - 1) {
            Log.d(TAG, "fastForward() no time to skip");
            return false;
        }

        int index = startIndex + 1;
        long timeToSkip = desiredTimeToSkip - (itemDuration(startIndex) - progressInCurrentItem);

        while (timeToSkip > 0 && index < textList.size() - 1) {
            int newIndex = index + 1;
            long newTimeToSkip = timeToSkip - itemDuration(index);
            long alreadySkipped = desiredTimeToSkip - timeToSkip;

            if (newTimeToSkip > 0 || alreadySkipped < desiredTimeToSkip / 3) {
                index = newIndex;
                timeToSkip = newTimeToSkip;
            } else {
                break;
            }
        }

        Log.d(TAG, "fastForward() " + startIndex + " => " + index);
        current = index;

        ensureTextRangeVisibleOnScreen(true);

        return true;
    }

    private long itemDuration(int index) {
        return textList.get(index).timePosition
                - (index > 0 ? textList.get(index - 1).timePosition : 0);
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
    public synchronized void restoreFromStart() {
        Log.d(TAG, "restoreFromStart -> current = 0");
        current = 0;
    }

    @Override
    public void storeCurrent() {
        storedScrollPosition = ttsHost != null ? ttsHost.getScrollY() : null;
    }

    @Override
    public synchronized void restoreCurrent() {
        if (ttsHost == null) return;

        if (storedScrollPosition != null) {
            int position = storedScrollPosition;
            storedScrollPosition = null;

            if (position == ttsHost.getScrollY()) {
                // no scrolling has been done since pause, don't restore anything
                return;
            }
        }

        float currentTop = ttsHost.getScrollY();
        float currentBottom = currentTop + ttsHost.getViewHeight();
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
    public synchronized long getTime() {
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
        if (ttsHost == null) return;

        GenericItem item = textList.get(current);
        if (item.bottom > ttsHost.getScrollY() + ttsHost.getViewHeight()
                || canMoveBackward && item.top < ttsHost.getScrollY()) {
            // TODO: check: call directly?
            handler.post(() -> ttsHost.scrollTo((int) item.top));
        }
    }

    private float convertWebViewToScreenY(float y) {
        if (ttsHost == null) {
            Log.w(TAG, "convertWebViewToScreenY() ttsHost is null");
            return 0;
        }

        WebView webView = ttsHost.getWebView();
        return y * webView.getHeight() / webView.getContentHeight();
    }

}
