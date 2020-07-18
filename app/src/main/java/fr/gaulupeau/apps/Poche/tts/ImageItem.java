package fr.gaulupeau.apps.Poche.tts;

class ImageItem extends GenericItem {

    String altText;
    String title;
    String src;

    ImageItem(String altText, String title, String src, Range range, float top, float bottom) {
        super(range, top, bottom);
        this.altText = altText;
        this.title = title;
        this.src = src;
    }

}
