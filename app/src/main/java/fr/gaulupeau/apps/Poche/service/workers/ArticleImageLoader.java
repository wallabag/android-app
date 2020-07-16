package fr.gaulupeau.apps.Poche.service.workers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.network.ImageCacheUtils;

import static fr.gaulupeau.apps.Poche.network.ImageCacheUtils.WALLABAG_RELATIVE_URL_PATH;

public class ArticleImageLoader {

    public static class Result {
        private Bitmap image;
        private String effectiveUri;
        private boolean local;

        public Bitmap getImage() {
            return image;
        }

        public String getEffectiveUri() {
            return effectiveUri;
        }

        public boolean isLocal() {
            return local;
        }
    }

    private static final String TAG = ArticleImageLoader.class.getSimpleName();

    public Result loadImage(int articleId, String imageUrl) {
        return loadImage(articleId, imageUrl, App.getSettings().isImageCacheEnabled());
    }

    public Result loadImage(int articleId, String imageUrl, boolean tryLocal) {
        Result result = new Result();

        if (tryLocal) {
            Log.v(TAG, "loadImage() trying to load local image");

            File file = null;
            try {
                file = ImageCacheUtils.getCachedImageFile(imageUrl, articleId);
                if (file != null) {
                    result.image = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.w(TAG, "loadImage() local loading exception", e);
            }

            if (result.image != null) {
                result.local = true;
                result.effectiveUri = file.getAbsolutePath();

                return result;
            }
        }

        Log.v(TAG, "loadImage() trying to load remote image");

        if (imageUrl.startsWith(WALLABAG_RELATIVE_URL_PATH)) {
            imageUrl = App.getSettings().getUrl() + imageUrl;
        }

        URL url = null;
        try {
            url = new URL(imageUrl);
            try (InputStream is = url.openStream()) {
                result.image = BitmapFactory.decodeStream(is);
            }
        } catch (Exception e) {
            Log.w(TAG, "loadImage() remote loading exception", e);
        }

        if (result.image != null) {
            try {
                result.effectiveUri = Uri.parse(url.toURI().toString()).toString();
            } catch (URISyntaxException e) {
                Log.w(TAG, "loadImage() URI parsing exception", e);
            }
        }

        return result;
    }

}
