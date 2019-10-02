package fr.gaulupeau.apps.Poche.ui;

import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;

class JsAnnotationController {

    private static final String TAG = JsAnnotationController.class.getSimpleName();

    public interface Callback {
        List<Annotation> getAnnotations();
    }

    private final Callback callback;

    public JsAnnotationController(Callback callback) {
        this.callback = callback;
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getAnnotations() {
        Log.i(TAG, "getAnnotations()");

        return annotationsToJsonString(callback.getAnnotations());
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void newAnnotation(String someParam) {
        Log.i(TAG, String.format("newAnnotation(%s)", someParam));

        // TODO: implement
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void editAnnotation(String someParam) {
        Log.i(TAG, String.format("editAnnotation(%s)", someParam));

        // TODO: implement
    }

    private static String annotationsToJsonString(List<Annotation> annotations) {
        try {
            JSONArray annotationsArray = new JSONArray();

            for (Annotation annotation : annotations) {
                JSONObject annotationJson = new JSONObject();

                JSONArray rangesArray = new JSONArray();
                for (AnnotationRange range : annotation.getRanges()) {
                    JSONObject rangeJson = new JSONObject();

                    rangeJson.put("start", range.getStart());
                    rangeJson.put("end", range.getEnd());
                    rangeJson.put("startOffset", range.getStartOffset());
                    rangeJson.put("endOffset", range.getEndOffset());

                    rangesArray.put(rangeJson);
                }

                annotationJson.put("id", annotation.getId());
                annotationJson.put("text", annotation.getText());
                annotationJson.put("quote", annotation.getQuote());
                annotationJson.put("ranges", rangesArray);

                annotationsArray.put(annotationJson);
            }

            return annotationsArray.toString();
        } catch (JSONException e) {
            Log.e(TAG, "annotationsToJsonString() exception", e);
        }
        return null;
    }

}
