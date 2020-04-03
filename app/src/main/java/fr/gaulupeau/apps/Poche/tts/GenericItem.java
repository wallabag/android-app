package fr.gaulupeau.apps.Poche.tts;

abstract class GenericItem {

    float top;    // top location in the web view
    float bottom; // bottom location in the web view
    long timePosition; // in milliseconds from the beginning of the document

    GenericItem() {}

    GenericItem(float top, float bottom) {
        this.top = top;
        this.bottom = bottom;
    }

    abstract long approximateDuration();

}
