package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
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

    public static void changeArticleTitle(Context context, int articleID, String title) {
        Log.d(TAG, String.format("changeArticleTitle(%d, %s) started", articleID, title));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleID, articleDao);
        if(article == null) {
            Log.w(TAG, "changeArticleTitle() article was not found");
            return; // not an error?
        }

        article.setTitle(title);
        articleDao.update(article);

        notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.TITLE_CHANGED);

        ServiceHelper.changeArticleTitle(context, article.getArticleId());

        Log.d(TAG, "changeArticleTitle() finished");
    }

    public static void setArticleProgress(Context context, int articleID, double progress) {
        Log.d(TAG, String.format("setArticleProgress(%d, %g) started", articleID, progress));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleID, articleDao);
        if(article == null) {
            Log.w(TAG, "setArticleProgress() article was not found");
            return; // not an error?
        }

        article.setArticleProgress(progress);
        articleDao.update(article);

        Log.d(TAG, "setArticleProgress() finished");
    }

    public static void setArticleTags(Context context, int articleID, List<Tag> newTags) {
        Log.d(TAG, String.format("setArticleTags(%d, %s) started", articleID, newTags));

        boolean tagsChanged = false;

        ArticleDao articleDao = getArticleDao();
        Article article = getArticle(articleID, articleDao);
        TagDao tagDao = DbConnection.getSession().getTagDao();
        ArticleTagsJoinDao joinDao = DbConnection.getSession().getArticleTagsJoinDao();

        if(article == null) {
            Log.w(TAG, "setArticleTags() article was not found");
            return; // not an error?
        }

        article.resetTags();
        List<Tag> currentTags = article.getTags();

        List<String> tagsToDelete = new ArrayList<>();
        List<Tag> tagsToInsert = new ArrayList<>();

        List<Long> joinsToDelete = new ArrayList<>();
        List<ArticleTagsJoin> joinsToCreate = new ArrayList<>();

        if(!currentTags.isEmpty()) {
            List<Tag> tagsToRemove = new ArrayList<>();

            for(Tag oldTag: currentTags) {
                Tag newTag = null;
                for(Tag t: newTags) {
                    if(TextUtils.equals(t.getLabel(), oldTag.getLabel())) {
                        newTag = t;
                        break;
                    }
                }

                if(newTag == null) {
                    if(oldTag.getTagId() != null) tagsToDelete.add(oldTag.getTagId().toString());
                    tagsToRemove.add(oldTag);
                    joinsToDelete.add(oldTag.getId());
                } else {
                    newTags.remove(newTag);
                }
            }

            if(!tagsToRemove.isEmpty()) {
                currentTags.removeAll(tagsToRemove);
            }
        }

        if(!newTags.isEmpty()) {
            List<Tag> tags = tagDao.queryBuilder().list();

            for(Tag tag: newTags) {
                Tag existingTag = null;
                for(Tag t: tags) {
                    if(TextUtils.equals(t.getLabel(), tag.getLabel())) {
                        existingTag = t;
                        break;
                    }
                }

                if(existingTag != null) {
                    currentTags.add(existingTag);
                    joinsToCreate.add(new ArticleTagsJoin(
                            null, article.getId(), existingTag.getId()));
                } else {
                    currentTags.add(tag);
                    tagsToInsert.add(tag);
                }
            }
        }

        if(!tagsToInsert.isEmpty()) {
            tagsChanged = true;
            tagDao.insertInTx(tagsToInsert);

            for(Tag tag: tagsToInsert) {
                joinsToCreate.add(new ArticleTagsJoin(null, article.getId(), tag.getId()));
            }
        }

        if(!joinsToDelete.isEmpty()) {
            tagsChanged = true;

            List<ArticleTagsJoin> joins = joinDao.queryBuilder().where(
                    ArticleTagsJoinDao.Properties.ArticleId.eq(article.getId()),
                    ArticleTagsJoinDao.Properties.TagId.in(joinsToDelete)).list();

            joinDao.deleteInTx(joins);
        }

        if(!joinsToCreate.isEmpty()) {
            tagsChanged = true;
            joinDao.insertInTx(joinsToCreate, false);
        }

        if(!tagsToDelete.isEmpty()) {
            tagsChanged = true;

            Log.d(TAG, "setArticleTags() storing deleted tags to offline queue");
            ServiceHelper.deleteTagsFromArticle(context, articleID, tagsToDelete);
        }

        if(tagsChanged) {
            notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.TAGS_CHANGED);

            Log.d(TAG, "setArticleTags() storing tags change to offline queue");
            ServiceHelper.changeArticleTags(context, articleID);
        }

        Log.d(TAG, "setArticleTags() finished");
    }

    public static void deleteArticle(Context context, int articleID) {
        Log.d(TAG, String.format("deleteArticle(%d) started", articleID));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleID, articleDao);
        if(article == null) {
            Log.w(TAG, "favoriteArticle() article was not found");
            return; // not an error?
        }

        getDaoSession().getArticleContentDao().deleteByKey(article.getId());
        articleDao.delete(article);

        notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.DELETED);

        Log.d(TAG, "deleteArticle() article object deleted");

        ServiceHelper.deleteArticle(context, articleID);

        Log.d(TAG, "deleteArticle() finished");
    }

    public static void wipeDB(Settings settings) {
        DaoSession daoSession = getDaoSession();

        daoSession.getArticleContentDao().deleteAll();
        daoSession.getArticleDao().deleteAll();
        daoSession.getTagDao().deleteAll();
        daoSession.getArticleTagsJoinDao().deleteAll();
        daoSession.getQueueItemDao().deleteAll();

        settings.setLatestUpdatedItemTimestamp(0);
        settings.setLatestUpdateRunTimestamp(0);
        settings.setFirstSyncDone(false);

        EventHelper.notifyEverythingRemoved();
    }

    private static ArticleDao getArticleDao() {
        return getDaoSession().getArticleDao();
    }

    private static DaoSession getDaoSession() {
        return DbConnection.getSession();
    }

    private static Article getArticle(int articleID, ArticleDao articleDao) {
        return articleDao.queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(articleID))
                .build().unique();
    }

}
