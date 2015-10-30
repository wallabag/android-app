package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;
import fr.gaulupeau.apps.Poche.ui.ConnectionFailAlert;

public class ToggleFavoriteTask extends AsyncTask<Void, Void, Boolean> {

    private static String TAG = ToggleFavoriteTask.class.getSimpleName();

    private Context context;
    private int articleId;
    private DaoSession daoSession;
    private ArticleDao articleDao;
    private Article article;
    private WallabagService service;
    private String errorMessage;

    public ToggleFavoriteTask(Context context, int articleId, DaoSession daoSession) {
        this.context = context;
        this.articleId = articleId;
        this.daoSession = daoSession;
    }

    public ToggleFavoriteTask(Context context, int articleId, ArticleDao articleDao, Article article) {
        this.context = context;
        this.articleId = articleId;
        this.articleDao = articleDao;
        this.article = article;
    }

    @Override
    protected void onPreExecute() {
        if(articleDao == null) {
            if(daoSession == null) daoSession = DbConnection.getSession();
            articleDao = daoSession.getArticleDao();
        }
        if(article == null) {
            article = articleDao.queryBuilder()
                    .where(ArticleDao.Properties.Id.eq(articleId))
                    .build().unique();
        }

        article.setFavorite(!article.getFavorite());
        articleDao.update(article);

        Settings settings = App.getInstance().getSettings();
        service = new WallabagService(
                settings.getUrl(),
                settings.getKey(Settings.USERNAME),
                settings.getKey(Settings.PASSWORD));
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            return service.toggleFavorite(articleId);
        } catch (IOException e) {
            Log.w(TAG, "IOException", e);
            errorMessage = e.getMessage();
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (success) {
            article.setSync(true);
            articleDao.update(article);

            if(context != null) {
                Toast.makeText(context, article.getFavorite()
                                ? R.string.added_to_favorites_message
                                : R.string.removed_from_favorites_message,
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            if(context != null) ConnectionFailAlert.getDialog(context, errorMessage).show();
        }
    }

}
