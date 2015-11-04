package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.widget.Toast;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.ui.ConnectionFailAlert;

public class ToggleFavoriteTask extends GenericArticleTask {

    public ToggleFavoriteTask(Context context, int articleId, ArticleDao articleDao, Article article) {
        super(context, articleId, articleDao, article);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        article.setFavorite(!article.getFavorite());
        articleDao.update(article);
    }

    @Override
    protected Boolean doInBackgroundSimple(Void... params) throws IOException {
        if(isOffline) return false;

        if(service.toggleFavorite(articleId)) return true;

        errorMessage = "Couldn't sync to server";
        return false;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        super.onPostExecute(success);

        article.setSync(success); // ?
        articleDao.update(article);

        if(success || isOffline) {
            if(context != null) {
                Toast.makeText(context, article.getFavorite()
                                ? R.string.added_to_favorites_message
                                : R.string.removed_from_favorites_message,
                        Toast.LENGTH_SHORT).show();

                if(isOffline) {
                    Toast.makeText(context, "Couldn't sync to server: no internet connection",
                            Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            if(context != null) {
                ConnectionFailAlert.getDialog(context, errorMessage).show();
            }
        }
    }

}
