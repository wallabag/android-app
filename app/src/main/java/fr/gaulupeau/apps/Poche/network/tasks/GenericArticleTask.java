package fr.gaulupeau.apps.Poche.network.tasks;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;

public abstract class GenericArticleTask extends AsyncTask<Void, Integer, Boolean> {

    protected static String TAG = GenericArticleTask.class.getSimpleName();

    protected Context context;
    protected int articleId;
    protected DaoSession daoSession;
    protected ArticleDao articleDao;
    protected Article article;
    protected WallabagService service;
    protected String errorMessage;
    protected boolean isOffline;
    protected boolean noCredentials;

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
    protected void onPreExecute() {
        preparePre();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        prepareBG();

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

    protected void preparePre() {
        if(articleDao == null) {
            if(daoSession == null) daoSession = DbConnection.getSession();
            articleDao = daoSession.getArticleDao();
        }
        if(article == null) {
            article = articleDao.queryBuilder()
                    .where(ArticleDao.Properties.Id.eq(articleId))
                    .build().unique();
        }
    }

    protected void prepareBG() {
        if(WallabagConnection.isNetworkOnline()) {
            Settings settings = App.getInstance().getSettings();
            String username = settings.getKey(Settings.USERNAME);
            noCredentials = username == null || username.length() == 0;
            if(!noCredentials) {
                service = new WallabagService(
                        settings.getUrl(),
                        username,
                        settings.getKey(Settings.PASSWORD));
            }
        } else {
            isOffline = true;
        }
    }

}
