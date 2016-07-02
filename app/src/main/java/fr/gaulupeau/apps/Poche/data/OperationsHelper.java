package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.events.ArticleChangedEvent;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.notifyAboutFeedChanges;

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

            ArticleChangedEvent.ChangeType changeType = archive
                    ? ArticleChangedEvent.ChangeType.Archived
                    : ArticleChangedEvent.ChangeType.Unarchived;

            Log.v(TAG, "archiveArticle() before post(ArticleChangedEvent)");
            postEvent(new ArticleChangedEvent(article, changeType));
            Log.v(TAG, "archiveArticle() after post(ArticleChangedEvent)");
            Log.v(TAG, "archiveArticle() before notifyAboutFeedChanges()");
            notifyAboutFeedChanges(article, changeType);
            Log.v(TAG, "archiveArticle() after notifyAboutFeedChanges()");

            Log.d(TAG, "archiveArticle() article object updated");
        } else {
            Log.d(TAG, "archiveArticle(): article state was not changed");

            // do we need to continue with the sync part? Probably yes
        }
        Log.v(TAG, "archiveArticle() after local changes");

        Log.d(TAG, "archiveArticle() local changes took (ms): "
                + (SystemClock.elapsedRealtime() - timestamp));

        ServiceHelper.archiveArticle(context, articleID, archive);

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

            ArticleChangedEvent.ChangeType changeType = favorite
                    ? ArticleChangedEvent.ChangeType.Favorited
                    : ArticleChangedEvent.ChangeType.Unfavorited;

            postEvent(new ArticleChangedEvent(article, changeType));
            notifyAboutFeedChanges(article, changeType);

            Log.d(TAG, "favoriteArticle() article object updated");
        } else {
            Log.d(TAG, "favoriteArticle(): article state was not changed");

            // TODO: do we need to continue with the sync part? Probably yes
        }

        ServiceHelper.favoriteArticle(context, articleID, favorite);

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

        ArticleChangedEvent.ChangeType changeType = ArticleChangedEvent.ChangeType.Deleted;

        postEvent(new ArticleChangedEvent(article, changeType));
        notifyAboutFeedChanges(article, changeType);

        Log.d(TAG, "deleteArticle() article object deleted");

        ServiceHelper.deleteArticle(context, articleID);

        Log.d(TAG, "deleteArticle() finished");
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
