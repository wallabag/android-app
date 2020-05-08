package fr.gaulupeau.apps.Poche.tts;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import androidx.core.util.Pair;

import java.io.File;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.service.workers.ArticleImageLoader;
import fr.gaulupeau.apps.Poche.utils.WallabagFileProvider;

public class AlbumArtLoaderTask extends AsyncTask<Void, Void, Pair<String, Bitmap>> {

    public interface Callback {
        void onLoad(int articleId, String imageUrl, String effectiveUri, Bitmap image);
    }

    private int articleId;
    private String imageUrl;
    private Callback callback;

    public AlbumArtLoaderTask(int articleId, String imageUrl, Callback callback) {
        this.articleId = articleId;
        this.imageUrl = imageUrl;
        this.callback = callback;
    }

    public void execute() {
        execute((Void) null);
    }

    @Override
    protected Pair<String, Bitmap> doInBackground(Void... voids) {
        ArticleImageLoader.Result result = new ArticleImageLoader().loadImage(articleId, imageUrl);

        if (result.getImage() == null) {
            return new Pair<>(imageUrl, null);
        }

        Bitmap image = result.getImage();
        String imageUri = result.getEffectiveUri();

        if (result.isLocal()) {
            File file = new File(imageUri);
            imageUri = WallabagFileProvider.getUriForFile(App.getInstance(), file).toString();
        }

        // resizing could be done here, but the lockscreen seems to use the same image
        // regardless of the METADATA_KEY_ALBUM_ART_URI parameter being set

        return new Pair<>(imageUri, image);
    }

    @Override
    protected void onPostExecute(Pair<String, Bitmap> result) {
        callback.onLoad(articleId, imageUrl, result.first, result.second);
    }

}
