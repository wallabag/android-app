package fr.gaulupeau.apps.Poche.ui;

import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;

class JsAnnotationController {

    private static final String TAG = JsAnnotationController.class.getSimpleName();

    public interface Callback {
        List<Annotation> getAnnotations();
        Annotation createAnnotation(Annotation annotation);
        Annotation updateAnnotation(Annotation annotation);
        Annotation deleteAnnotation(Annotation annotation);
    }

    private final Callback callback;

    JsAnnotationController(Callback callback) {
        this.callback = callback;
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getAnnotations() {
        Log.i(TAG, "getAnnotations()");

        try {
            return annotationsToJsonString(callback.getAnnotations());
        } catch (Exception e) {
            Log.e(TAG, "getAnnotations()", e);
        }

        return null;
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String createAnnotation(String annotationString) {
        Log.i(TAG, String.format("createAnnotation(%s)", annotationString));

        try {
            Annotation annotation = annotationFromJsonString(annotationString);

            annotation = callback.createAnnotation(annotation);

            return annotationToJson(annotation).toString();
        } catch (Exception e) {
            Log.e(TAG, "createAnnotation()", e);
        }

        return null;
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String updateAnnotation(String annotationString) {
        Log.i(TAG, String.format("updateAnnotation(%s)", annotationString));

        try {
            Annotation annotation = annotationFromJsonString(annotationString);

            annotation = callback.updateAnnotation(annotation);

            return annotationToJson(annotation).toString();
        } catch (Exception e) {
            Log.e(TAG, "updateAnnotation()", e);
        }

        return null;
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String deleteAnnotation(String annotationString) {
        Log.i(TAG, String.format("deleteAnnotation(%s)", annotationString));

        try {
            Annotation annotation = annotationFromJsonString(annotationString);

            annotation = callback.deleteAnnotation(annotation);

            return annotationToJson(annotation).toString();
        } catch (Exception e) {
            Log.e(TAG, "deleteAnnotation()", e);
        }

        return null;
    }

    private static String annotationsToJsonString(List<Annotation> annotations)
            throws JSONException {
        JSONArray annotationsArray = new JSONArray();

        for (Annotation annotation : annotations) {
            annotationsArray.put(annotationToJson(annotation));
        }

        return annotationsArray.toString();
    }

    private static JSONObject annotationToJson(Annotation annotation) throws JSONException {
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

        return annotationJson;
    }

    private static Annotation annotationFromJsonString(String annotationString)
            throws JSONException {
        JSONObject annotationJson = new JSONObject(annotationString);

        Annotation annotation = new Annotation();
        if (annotationJson.has("id")) annotation.setId(annotationJson.getLong("id"));
        annotation.setText(annotationJson.getString("text"));
        annotation.setQuote(annotationJson.getString("quote"));

        List<AnnotationRange> ranges = new ArrayList<>(1);

        JSONArray rangesArray = annotationJson.getJSONArray("ranges");
        for (int i = 0; i < rangesArray.length(); i++) {
            JSONObject rangeJson = rangesArray.getJSONObject(i);

            AnnotationRange range = new AnnotationRange();
            range.setStart(rangeJson.getString("start"));
            range.setEnd(rangeJson.getString("end"));
            range.setStartOffset(rangeJson.getLong("startOffset"));
            range.setEndOffset(rangeJson.getLong("endOffset"));

            ranges.add(range);
        }

        annotation.setRanges(ranges);

        return annotation;
    }

}
