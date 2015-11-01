package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;

public abstract class GenericArticleTask extends AsyncTask<Void, Void, Boolean> {

    protected static String TAG = ToggleFavoriteTask.class.getSimpleName();

    protected Context context;
    protected int articleId;
    protected DaoSession daoSession;
    protected ArticleDao articleDao;
    protected Article article;
    protected WallabagService service;
    protected String errorMessage;

    public GenericArticleTask(Context context, int articleId, DaoSession daoSession) {
        this.context = context;
        this.articleId = articleId;
        this.daoSession = daoSession;
    }

    public GenericArticleTask(Context context, int articleId, ArticleDao articleDao, Article article) {
        this.context = context;
        this.articleId = articleId;
        this.articleDao = articleDao;
        this.article = article;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        prepare();

        try {
            return doInBackgroundSimple(params);
        } catch (IOException e) {
            Log.w(TAG, "IOException", e);
            errorMessage = e.getMessage();
            return false;
        }
    }

    protected Boolean doInBackgroundSimple(Void... params) throws IOException {
        return false;
    }

    protected void prepare() {
        if(articleDao == null) {
            if(daoSession == null) daoSession = DbConnection.getSession();
            articleDao = daoSession.getArticleDao();
        }
        if(article == null) {
            article = articleDao.queryBuilder()
                    .where(ArticleDao.Properties.Id.eq(articleId))
                    .build().unique();
        }

        Settings settings = App.getInstance().getSettings();
        service = new WallabagService(
                settings.getUrl(),
                settings.getKey(Settings.USERNAME),
                settings.getKey(Settings.PASSWORD));
    }

}
