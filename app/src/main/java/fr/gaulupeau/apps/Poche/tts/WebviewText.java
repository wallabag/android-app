package fr.gaulupeau.apps.Poche.tts;

import android.os.Handler;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebView;
import android.widget.ScrollView;

import java.util.Vector;

/**
 * TextInterface to navigate in a Webview.
 */
public class WebviewText implements TextInterface {

    private final WebView webView;
    private final ScrollView scrollView;
    private final Handler handler;

    private final Vector<TextItem> textList = new Vector<>();
    private volatile int current;
    private Runnable callback;


    private final String WEBVIEW_LOG_CMD_HEADER = "CMD_" + getRandomText(4) + ":";
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
            "        console.log('" + WEBVIEW_LOG_CMD_HEADER + "start');" +
            "}" +
            "function cmdEnd() {" +
            "        console.log('" + WEBVIEW_LOG_CMD_HEADER + "end');" +
            "}" +
            "function cmdText(text, top, bottom) {" +
            "        console.log('" + WEBVIEW_LOG_CMD_HEADER + "' + top + ':' + bottom + ':' + text);" +
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

    private static final String LOG_TAG = "WebviewText";


    public WebviewText(WebView webView, ScrollView scrollView) {
        this.webView = webView;
        this.scrollView = scrollView;
        this.handler = new Handler();
    }

    public void parseWebviewDocument(Runnable callback)
    {
        Log.d(LOG_TAG, "parseWebviewDocument");
        this.textList.clear();
        this.callback = callback;
        webView.loadUrl("javascript:" + JAVASCRIPT_PARSE_DOCUMENT_TEXT + ";parseDocumentText();");
    }

    private void onDocumentParseStart() {
        Log.d(LOG_TAG, "onDocumentParseStart");
    }

    private void onDocumentParseEnd() {
        Log.d(LOG_TAG, "onDocumentParseEnd");
        if (callback != null) {
            callback.run();
        }
    }

    private void onDocumentParseItem(String text, float top, float bottom) {
        top = convertWebviewToScreenY(top);
        bottom = convertWebviewToScreenY(bottom);
        Log.d(LOG_TAG, "onDocumentParseItem " + top + " " + bottom + " " + text);
        textList.add(new TextItem(text, top, bottom));
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
            if (message.startsWith(WEBVIEW_LOG_CMD_HEADER))
            {
                String content = message.substring(WEBVIEW_LOG_CMD_HEADER.length());
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

    @Override
    public String getText(int relativeIndex) {
        int i = current + relativeIndex;
        if ( (i >= 0) && (i < textList.size())) {
            return textList.get(i).text;
        } else {
            return null;
        }
    }

    /**
     * Go to the next text item.
     * @return true if current item changed (not already the end).
     */
    @Override
    public boolean next() {
        Log.d(LOG_TAG, "next, current=" + current);
        boolean result;
        if (current < (textList.size() - 1)) {
            current = current + 1;
            result = true;
        } else {
            result = false;
        }
        ensureTextRangeVisibleOnScreen(false);
        return result;
    }


    /**
     * Fast forward to the next TextItem located below the current one (next line).
     * @return true if current item changed (not already the beginning).
     */
    @Override
    public boolean fastForward() {
        Log.d(LOG_TAG, "fastForward, current=" + current);
        boolean result;
        //for(int i=textListCurrentIndex; i<(textListSize-1) && i<(textListCurrentIndex+6); i++) {
        //    TextItem t = textList.get(i);
        //    Log.d(LOG_TAG, " - " + i + " top=" + t.top + " bottom=" + t.bottom + "  " + t.text);
        //}
        int newIndex = current + 1;
        if ((newIndex >= 0) && (newIndex < textList.size())) {
            float originalBottom = textList.get(newIndex-1).bottom;
            // Look for text's index that start on the next line (its top >= current bottom)
            while((newIndex < (textList.size()-1))
                    && (textList.get(newIndex).top < originalBottom))
            {
                newIndex = newIndex + 1;
            }
            Log.d(LOG_TAG, " => " + newIndex);
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
     * @return true if current item changed (not already the end).
     */
    @Override
    public boolean rewind() {
        Log.d(LOG_TAG, "rewind, current=" + current);
        boolean result;
        //for(int i=current; (i>=0) && i>(current-8); i--) {
        //    TextItem t = textList.get(i);
        //    Log.d(LOG_TAG, " - " + i + " top=" + t.top + " bottom=" + t.bottom + "  " + t.text);
        //}
        int newIndex = current - 1;
        if ((newIndex >= 0) && ((newIndex + 1) < textList.size())) {
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
            current = newIndex;
            result = true;
        } else {
            result = false;
        }
        ensureTextRangeVisibleOnScreen(true);
        return result;
    }



    @Override
    public void restoreCurrent() {
        float currentTop = scrollView.getScrollY();
        float currentBottom = currentTop + scrollView.getHeight();
        int result = Math.min(current, textList.size() - 1);
        TextItem textItem = textList.get(result);
        if ((textItem.bottom <= currentTop) || (textItem.top >= currentBottom)) {
            // current not displayed on screen, switch to the first text visible:
            result = textList.size() - 1;
            for(int i=0; i<textList.size(); i++) {
                if (textList.get(i).top > currentTop) {
                    result = i;
                    break;
                }
            }
        }
        current = result;
    }


    @Override
    public float getTime() {
        return 0;
    }

    @Override
    public float getTotalDuration() {
        return 1.0f;
    }

    private void ensureTextRangeVisibleOnScreen(boolean canMoveBackward) {
        final TextItem textItem = textList.get(current);
        if ((scrollView != null) &&
                ((textItem.bottom > scrollView.getScrollY() + scrollView.getHeight())
                        || (canMoveBackward && (textItem.top < scrollView.getScrollY())))) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.smoothScrollTo(0, (int) textItem.top);
                }
            });
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

    private static StringBuilder getRandomText(int length) {
        StringBuilder result = new StringBuilder(length);
        for(int i=0;i<length;i++) {
            result.append((char)(32 + Math.random() * (127-32)));
        }
        return result;
    }

    private static class TextItem {
        float top;
        float bottom;
        String text;
        public TextItem(String text, float top, float bottom) {
            this.text = text;
            this.top = top;
            this.bottom = bottom;
        }
    }
}
