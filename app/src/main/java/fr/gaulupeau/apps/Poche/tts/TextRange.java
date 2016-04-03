package fr.gaulupeau.apps.Poche.tts;

/**
 * Created by android on 03/04/16.
 */
public class TextRange {
    float top;
    float bottom;
    String text;
    public TextRange(String text, float top, float bottom) {
        this.text = text;
        this.top = top;
        this.bottom = bottom;
    }
}
