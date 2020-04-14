package fr.gaulupeau.apps.Poche.tts;

class ImageItem extends GenericItem {

    String altText;
    String title;
    String src;

    ImageItem(String altText, String title, String src, float top, float bottom) {
        super(top, bottom);
        this.altText = altText;
        this.title = title;
        this.src = src;
    }

}
