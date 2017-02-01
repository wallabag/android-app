package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.EventHelper;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;

import static fr.gaulupeau.apps.Poche.events.EventHelper.notifyAboutArticleChange;

public class OperationsHelper {

    private static final String TAG = OperationsHelper.class.getSimpleName();

    // we should not perform long/heavy operations on the main thread, hence verbosity
    // TODO: remove excessive logging after tested
    public static void archiveArticle(Context context, int articleID, boolean archive) {
        Log.d(TAG, String.format("archiveArticle(%d, %s) started", articleID, archive));

        long timestamp = SystemClock.elapsedRealtime();

        Log.v(TAG, "archiveArticle() before getArticleDao()");
        ArticleDao articleDao = getArticleDao();
        Log.v(TAG, "archiveArticle() after getArticleDao()");

        Log.v(TAG, "archiveArticle() before getArticle()");
        Article article = getArticle(articleID, articleDao);
        Log.v(TAG, "archiveArticle() after getArticle()");
        if(article == null) {
            Log.w(TAG, "archiveArticle() article was not found");
            return; // not an error?
        }

        Log.v(TAG, "archiveArticle() before local changes");
        if(article.getArchive() != archive) {
            article.setArchive(archive);
            Log.v(TAG, "archiveArticle() before getArticleDao().update()");
            articleDao.update(article);
            Log.v(TAG, "archiveArticle() after getArticleDao().update()");

            ArticlesChangedEvent.ChangeType changeType = archive
                    ? ArticlesChangedEvent.ChangeType.ARCHIVED
                    : ArticlesChangedEvent.ChangeType.UNARCHIVED;

            Log.v(TAG, "archiveArticle() before notifyAboutArticleChange()");
            notifyAboutArticleChange(article, changeType);
            Log.v(TAG, "archiveArticle() after notifyAboutArticleChange()");

            Log.d(TAG, "archiveArticle() article object updated");
        } else {
            Log.d(TAG, "archiveArticle(): article state was not changed");

            // do we need to continue with the sync part? Probably yes
        }
        Log.v(TAG, "archiveArticle() after local changes");

        Log.d(TAG, "archiveArticle() local changes took (ms): "
                + (SystemClock.elapsedRealtime() - timestamp));

        ServiceHelper.archiveArticle(context, articleID);

        Log.d(TAG, "archiveArticle() finished");
    }

    public static void favoriteArticle(Context context, int articleID, boolean favorite) {
        Log.d(TAG, String.format("favoriteArticle(%d, %s) started", articleID, favorite));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleID, articleDao);
        if(article == null) {
            Log.w(TAG, "favoriteArticle() article was not found");
            return; // not an error?
        }

        if(article.getFavorite() != favorite) {
            article.setFavorite(favorite);
            articleDao.update(article);

            ArticlesChangedEvent.ChangeType changeType = favorite
                    ? ArticlesChangedEvent.ChangeType.FAVORITED
                    : ArticlesChangedEvent.ChangeType.UNFAVORITED;

            notifyAboutArticleChange(article, changeType);

            Log.d(TAG, "favoriteArticle() article object updated");
        } else {
            Log.d(TAG, "favoriteArticle(): article state was not changed");

            // TODO: do we need to continue with the sync part? Probably yes
        }

        ServiceHelper.favoriteArticle(context, articleID);

        Log.d(TAG, "archiveArticle() finished");
    }

    public static void deleteArticle(Context context, int articleID) {
        Log.d(TAG, String.format("deleteArticle(%d) started", articleID));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleID, articleDao);
        if(article == null) {
            Log.w(TAG, "favoriteArticle() article was not found");
            return; // not an error?
        }

        articleDao.delete(article);

        notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.DELETED);

        Log.d(TAG, "deleteArticle() article object deleted");

        ServiceHelper.deleteArticle(context, articleID);

        Log.d(TAG, "deleteArticle() finished");
    }

    public static void wipeDB(Settings settings) {
        DbConnection.getSession().getArticleDao().deleteAll();
        DbConnection.getSession().getQueueItemDao().deleteAll();

        settings.setFirstSyncDone(false);

        EventHelper.notifyEverythingChanged();
    }

    private static ArticleDao getArticleDao() {
        return DbConnection.getSession().getArticleDao();
    }

    private static Article getArticle(int articleID, ArticleDao articleDao) {
        return articleDao.queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(articleID))
                .build().unique();
    }

}
