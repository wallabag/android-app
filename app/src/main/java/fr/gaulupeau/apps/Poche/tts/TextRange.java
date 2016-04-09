package fr.gaulupeau.apps.Poche.tts;

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
