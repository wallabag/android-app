package fr.gaulupeau.apps.Poche.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.QueueHelper;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;
import fr.gaulupeau.apps.Poche.events.ArticleChangedEvent;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectCredentialsException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;

public class BGService extends IntentService {

    public static final String ACTION_ARCHIVE = "wallabag.action.archive";
    public static final String ACTION_UNARCHIVE = "wallabag.action.unarchive";

    public static final String EXTRA_ID = "wallabag.extra.id";

    private static final String TAG = BGService.class.getSimpleName();

    // TODO: rename these so it is obvious to use getters instead?
    private Handler handler;

    private Settings settings;

    private DaoSession daoSession;
    private ArticleDao articleDao;
    private WallabagService wallabagService;

    public BGService() {
        super(BGService.class.getSimpleName());

        Log.d(TAG, "BGService() created");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent() started");

        String action = intent.getAction();
        switch(action) {
            case ACTION_ARCHIVE:
            case ACTION_UNARCHIVE:
                // TODO: check articleID
                archiveArticle(intent.getIntExtra(EXTRA_ID, -1), ACTION_ARCHIVE.equals(action));
                break;
        }

        Log.d(TAG, "onHandleIntent() finished");
    }

    private void archiveArticle(int articleID, boolean archive) {
        Log.d(TAG, String.format("archiveArticle(%d, %s) started", articleID, archive));

        Article article = getArticle(articleID);
        if(article == null) {
            Log.w(TAG, "archiveArticle() article was not found");
            return;
        }

        // local changes

        if(article.getArchive() != archive) {
            article.setArchive(archive);
            getArticleDao().update(article);

            EventBus.getDefault().post(new ArticleChangedEvent(article));
            // TODO: notify widget somehow (more specific event?)

            Log.d(TAG, "archiveArticle() article object updated");
        } else {
            Log.d(TAG, "archiveArticle(): article state was not changed");

            // TODO: check: do we need to continue with the sync part? Probably yes
        }

        // remote changes / queue

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            if(queueHelper.archiveArticle(articleID, archive)) {
                boolean synced = false;
                boolean error = false;
                if(WallabagConnection.isNetworkOnline()) {
                    try {
                        if(getWallabagService().toggleArchive(articleID)) {
                            synced = true;
                        }
                    } catch(IncorrectCredentialsException e) {
                        error = true;
                        Log.w(TAG, "archiveArticle() IncorrectCredentialsException", e);
                    } catch(IncorrectConfigurationException e) {
                        // this means configuration error; enqueue action;
                        // user must fix something before retry
                        error = true;
                        Log.w(TAG, "archiveArticle() IncorrectConfigurationException", e);
                    } catch(RequestException e) {
                        // this is unknown yet;
                        // enqueue action
                        error = true;
                        Log.w(TAG, "archiveArticle() RequestException", e);
                    } catch(IOException e) { // TODO: differentiate errors: timeouts and stuff
                        // IOExceptions in most cases mean temporary error,
                        // just queue action and retry later;
                        // in some cases may mean that the action was completed anyway
                        error = true;
                        Log.w(TAG, "archiveArticle() IOException", e);
                    }
                }

                if(!synced) {
                    if(error) {
                        // TODO: process errors differently
                    }

                    queueHelper.enqueueArchiveArticle(articleID, archive);
                }

                Log.d(TAG, "archiveArticle() synced: " + synced);
            } else {
                Log.d(TAG, "archiveArticle(): QueueHelper reports there's nothing to do");
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        Log.d(TAG, "archiveArticle() finished");
    }

    private Handler getHandler() {
        if(handler == null) {
            handler = new Handler(getMainLooper());
        }

        return handler;
    }

    private Settings getSettings() {
        if(settings == null) {
            settings = new Settings(this);
        }

        return settings;
    }

    private DaoSession getDaoSession() {
        if(daoSession == null) {
            daoSession = DbConnection.getSession();
        }

        return daoSession;
    }

    private ArticleDao getArticleDao() {
        if(articleDao == null) {
            articleDao = getDaoSession().getArticleDao();
        }

        return articleDao;
    }

    private WallabagService getWallabagService() {
        if(wallabagService == null) {
            Settings settings = getSettings();
            // TODO: check credentials? (throw an exception)
            wallabagService = new WallabagService(
                    settings.getUrl(),
                    settings.getKey(Settings.USERNAME),
                    settings.getKey(Settings.PASSWORD));
        }

        return wallabagService;
    }

    private void showToast(final String text, final int duration) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, duration).show();
            }
        });
    }

    private Article getArticle(int articleID) {
        return getArticleDao().queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(articleID))
                .build().unique();
    }

}
