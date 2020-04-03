package fr.gaulupeau.apps.Poche.tts;

import android.text.TextUtils;

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

    @Override
    long approximateDuration() {
        return (!TextUtils.isEmpty(altText) ? altText.length()
                : !TextUtils.isEmpty(title) ? title.length() : 0) * 50;
    }

}
