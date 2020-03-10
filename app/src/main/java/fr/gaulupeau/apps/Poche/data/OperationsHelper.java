package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import fr.gaulupeau.apps.Poche.data.dao.AnnotationDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.FtsDao;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.EventHelper;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;
import fr.gaulupeau.apps.Poche.service.AddArticleTask;
import fr.gaulupeau.apps.Poche.service.ArticleChangeTask;
import fr.gaulupeau.apps.Poche.service.DeleteArticleTask;
import fr.gaulupeau.apps.Poche.service.UpdateArticleProgressTask;

import static fr.gaulupeau.apps.Poche.events.EventHelper.notifyAboutArticleChange;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.service.ServiceHelper.enqueueServiceTask;
import static fr.gaulupeau.apps.Poche.service.ServiceHelper.enqueueSimpleServiceTask;

public class OperationsHelper {

    private static final String TAG = OperationsHelper.class.getSimpleName();

    public static void addArticle(Context context, String url) {
        addArticle(context, url, null);
    }

    public static void addArticle(Context context, String url, String originUrl) {
        Log.d(TAG, "addArticle() started");

        enqueueSimpleServiceTask(context, new AddArticleTask(url, originUrl));
    }

    public static void addArticleBG(String link, String origin) {
        queueOfflineChange(queueHelper -> queueHelper.addLink(link, origin));
    }

    public static void archiveArticle(Context context, int articleId, boolean archive) {
        enqueueSimpleServiceTask(context, ArticleChangeTask.newArchiveTask(articleId, archive));
    }

    public static void archiveArticleBG(int articleId, boolean archive) {
        Log.d(TAG, String.format("archiveArticleBG(%d, %s) started", articleId, archive));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleId, articleDao);
        if (article == null) {
            Log.w(TAG, "archiveArticleBG() article was not found");
            return; // not an error?
        }

        if (article.getArchive() != archive) {
            article.setArchive(archive);
            articleDao.update(article);

            ArticlesChangedEvent.ChangeType changeType = archive
                    ? ArticlesChangedEvent.ChangeType.ARCHIVED
                    : ArticlesChangedEvent.ChangeType.UNARCHIVED;

            notifyAboutArticleChange(article, changeType);

            Log.d(TAG, "archiveArticleBG() article object updated");
        } else {
            Log.d(TAG, "archiveArticleBG(): article state was not changed");

            // do we need to continue with the sync part? Probably yes
        }

        queueOfflineArticleChange(articleId, QueueItem.ArticleChangeType.ARCHIVE);

        Log.d(TAG, "archiveArticleBG() finished");
    }

    public static void favoriteArticle(Context context, int articleId, boolean favorite) {
        enqueueSimpleServiceTask(context, ArticleChangeTask.newFavoriteTask(articleId, favorite));
    }

    public static void favoriteArticleBG(int articleId, boolean favorite) {
        Log.d(TAG, String.format("favoriteArticleBG(%d, %s) started", articleId, favorite));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleId, articleDao);
        if (article == null) {
            Log.w(TAG, "favoriteArticleBG() article was not found");
            return; // not an error?
        }

        if (article.getFavorite() != favorite) {
            article.setFavorite(favorite);
            articleDao.update(article);

            ArticlesChangedEvent.ChangeType changeType = favorite
                    ? ArticlesChangedEvent.ChangeType.FAVORITED
                    : ArticlesChangedEvent.ChangeType.UNFAVORITED;

            notifyAboutArticleChange(article, changeType);

            Log.d(TAG, "favoriteArticleBG() article object updated");
        } else {
            Log.d(TAG, "favoriteArticleBG(): article state was not changed");

            // do we need to continue with the sync part? Probably yes
        }

        queueOfflineArticleChange(articleId, QueueItem.ArticleChangeType.FAVORITE);

        Log.d(TAG, "favoriteArticleBG() finished");
    }

    public static void changeArticleTitle(Context context, int articleId, String title) {
        enqueueSimpleServiceTask(context, ArticleChangeTask.newChangeTitleTask(articleId, title));
    }

    public static void changeArticleTitleBG(int articleId, String title) {
        Log.d(TAG, String.format("changeArticleTitleBG(%d, %s) started", articleId, title));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleId, articleDao);
        if (article == null) {
            Log.w(TAG, "changeArticleTitleBG() article was not found");
            return; // not an error?
        }

        article.setTitle(title);
        articleDao.update(article);

        notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.TITLE_CHANGED);

        queueOfflineArticleChange(articleId, QueueItem.ArticleChangeType.TITLE);

        Log.d(TAG, "changeArticleTitleBG() finished");
    }

    public static void setArticleProgress(Context context, int articleId, double progress) {
        enqueueSimpleServiceTask(context, new UpdateArticleProgressTask(articleId, progress));
    }

    public static void setArticleProgressBG(int articleId, double progress) {
        Log.d(TAG, String.format("setArticleProgressBG(%d, %g) started", articleId, progress));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleId, articleDao);
        if (article == null) {
            Log.w(TAG, "setArticleProgressBG() article was not found");
            return; // not an error?
        }

        article.setArticleProgress(progress);
        articleDao.update(article);

        Log.d(TAG, "setArticleProgressBG() finished");
    }

    public static void setArticleTags(Context context, int articleId, List<Tag> newTags,
                                      Runnable postCallCallback) {
        enqueueServiceTask(context, ctx -> setArticleTagsBG(articleId, newTags), postCallCallback);
    }

    private static void setArticleTagsBG(int articleId, List<Tag> newTags) {
        Log.d(TAG, String.format("setArticleTagsBG(%d, %s) started", articleId, newTags));

        boolean tagsChanged = false;

        ArticleDao articleDao = getArticleDao();
        Article article = getArticle(articleId, articleDao);
        TagDao tagDao = DbConnection.getSession().getTagDao();
        ArticleTagsJoinDao joinDao = DbConnection.getSession().getArticleTagsJoinDao();

        if (article == null) {
            Log.w(TAG, "setArticleTagsBG() article was not found");
            return; // not an error?
        }

        article.resetTags();
        List<Tag> currentTags = article.getTags();

        List<String> tagsToDelete = new ArrayList<>();
        List<Tag> tagsToInsert = new ArrayList<>();

        List<Long> joinsToDelete = new ArrayList<>();
        List<ArticleTagsJoin> joinsToCreate = new ArrayList<>();

        if (!currentTags.isEmpty()) {
            List<Tag> tagsToRemove = new ArrayList<>();

            for (Tag oldTag : currentTags) {
                Tag newTag = null;
                for (Tag t : newTags) {
                    if (TextUtils.equals(t.getLabel(), oldTag.getLabel())) {
                        newTag = t;
                        break;
                    }
                }

                if (newTag == null) {
                    if (oldTag.getTagId() != null) tagsToDelete.add(oldTag.getTagId().toString());
                    tagsToRemove.add(oldTag);
                    joinsToDelete.add(oldTag.getId());
                } else {
                    newTags.remove(newTag);
                }
            }

            if (!tagsToRemove.isEmpty()) {
                currentTags.removeAll(tagsToRemove);
            }
        }

        if (!newTags.isEmpty()) {
            List<Tag> tags = tagDao.queryBuilder().list();

            for (Tag tag : newTags) {
                Tag existingTag = null;
                for (Tag t : tags) {
                    if (TextUtils.equals(t.getLabel(), tag.getLabel())) {
                        existingTag = t;
                        break;
                    }
                }

                if (existingTag != null) {
                    currentTags.add(existingTag);
                    joinsToCreate.add(new ArticleTagsJoin(
                            null, article.getId(), existingTag.getId()));
                } else {
                    currentTags.add(tag);
                    tagsToInsert.add(tag);
                }
            }
        }

        if (!tagsToInsert.isEmpty()) {
            tagsChanged = true;
            tagDao.insertInTx(tagsToInsert);

            for (Tag tag : tagsToInsert) {
                joinsToCreate.add(new ArticleTagsJoin(null, article.getId(), tag.getId()));
            }
        }

        if (!joinsToDelete.isEmpty()) {
            tagsChanged = true;

            List<ArticleTagsJoin> joins = joinDao.queryBuilder().where(
                    ArticleTagsJoinDao.Properties.ArticleId.eq(article.getId()),
                    ArticleTagsJoinDao.Properties.TagId.in(joinsToDelete)).list();

            joinDao.deleteInTx(joins);
        }

        if (!joinsToCreate.isEmpty()) {
            tagsChanged = true;
            joinDao.insertInTx(joinsToCreate, false);
        }

        if (!tagsToDelete.isEmpty()) {
            tagsChanged = true;

            Log.d(TAG, "setArticleTagsBG() storing deleted tags to offline queue");
            queueOfflineChange(queueHelper
                    -> queueHelper.deleteTagsFromArticle(articleId, tagsToDelete));
        }

        if (tagsChanged) {
            notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.TAGS_CHANGED);

            Log.d(TAG, "setArticleTagsBG() storing tags change to offline queue");
            queueOfflineArticleChange(articleId, QueueItem.ArticleChangeType.TAGS);
        }

        Log.d(TAG, "setArticleTagsBG() finished");
    }

    public static void addAnnotation(Context context, int articleId, Annotation annotation) {
        enqueueServiceTask(context, ctx -> addAnnotationBG(articleId, annotation), null);
    }

    private static void addAnnotationBG(int articleId, Annotation annotation) {
        Log.d(TAG, String.format("addAnnotationBG(%d, %s) started", articleId, annotation));

        Article article = getArticle(articleId, getArticleDao());

        Long annotationId = annotation.getId();

        if (annotationId == null) {
            annotation.setArticleId(article.getId());

            DbConnection.getSession().getAnnotationDao().insert(annotation);
            annotationId = annotation.getId();

            List<AnnotationRange> ranges = annotation.getRanges();
            if (!ranges.isEmpty()) {
                for (AnnotationRange range : ranges) {
                    range.setAnnotationId(annotationId);
                }
                DbConnection.getSession().getAnnotationRangeDao().insertInTx(ranges);
            }

            Log.d(TAG, "addAnnotationBG() annotation object inserted");
        } else {
            Log.d(TAG, "addAnnotationBG() annotation was already persisted");
        }

        if (article != null) {
            if (!article.getAnnotations().contains(annotation)) {
                article.getAnnotations().add(annotation);
            }
            notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.ANNOTATIONS_CHANGED);
        }

        Log.d(TAG, "addAnnotationBG() ID: " + annotationId);
        if (annotationId != null) {
            long finalAnnotationId = annotationId;
            queueOfflineChange(queueHelper
                    -> queueHelper.addAnnotationToArticle(articleId, finalAnnotationId));
        }

        Log.d(TAG, "addAnnotationBG() finished");
    }

    public static void updateAnnotation(Context context, int articleId, Annotation annotation) {
        enqueueServiceTask(context, ctx -> updateAnnotationBG(articleId, annotation), null);
    }

    private static void updateAnnotationBG(int articleId, Annotation annotation) {
        Log.d(TAG, String.format("updateAnnotationBG(%d, %s) started", articleId, annotation));

        Long annotationId = annotation.getId();

        if (annotationId == null) {
            throw new RuntimeException("Annotation wasn't persisted first");
        }

        String newText = annotation.getText();

        AnnotationDao annotationDao = DbConnection.getSession().getAnnotationDao();
        annotation = annotationDao.queryBuilder()
                .where(AnnotationDao.Properties.Id.eq(annotationId)).unique();

        if (TextUtils.equals(annotation.getText(), newText)) {
            Log.w(TAG, "updateAnnotationBG() annotation ID=" + annotationId
                    + " already has text=" + newText);
            return;
        }

        annotation.setText(newText);

        annotationDao.update(annotation);
        Log.d(TAG, "updateAnnotationBG() annotation object updated");

        Article article = getArticle(articleId, getArticleDao());
        if (article != null) {
            notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.ANNOTATIONS_CHANGED);
        }

        Log.d(TAG, "updateAnnotationBG() ID: " + annotationId);
        queueOfflineChange(queueHelper
                -> queueHelper.updateAnnotationOnArticle(articleId, annotationId));

        Log.d(TAG, "updateAnnotationBG() finished");
    }

    public static void deleteAnnotation(Context context, int articleId, Annotation annotation) {
        enqueueServiceTask(context, ctx -> deleteAnnotationBG(articleId, annotation), null);
    }

    private static void deleteAnnotationBG(int articleId, Annotation annotation) {
        Log.d(TAG, String.format("deleteAnnotationBG(%d, %s) started", articleId, annotation));

        Integer remoteId = annotation.getAnnotationId();

        if (annotation.getId() != null) {
            List<AnnotationRange> ranges = annotation.getRanges();
            if (!ranges.isEmpty()) {
                DbConnection.getSession().getAnnotationRangeDao().deleteInTx(ranges);
            }

            DbConnection.getSession().getAnnotationDao().delete(annotation);
            Log.d(TAG, "deleteAnnotationBG() annotation object deleted");
        } else {
            Log.d(TAG, "deleteAnnotationBG() annotation was not persisted");
        }

        Article article = getArticle(articleId, getArticleDao());
        if (article != null) {
            article.getAnnotations().remove(annotation);
            notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.ANNOTATIONS_CHANGED);
        }

        Log.d(TAG, "deleteAnnotationBG() remote ID: " + remoteId);
        if (remoteId != null) {
            queueOfflineChange(queueHelper
                    -> queueHelper.deleteAnnotationFromArticle(articleId, remoteId));
        }

        Log.d(TAG, "deleteAnnotationBG() finished");
    }

    public static void deleteArticle(Context context, int articleId) {
        enqueueSimpleServiceTask(context, new DeleteArticleTask(articleId));
    }

    public static void deleteArticleBG(int articleId) {
        Log.d(TAG, String.format("deleteArticleBG(%d) started", articleId));

        ArticleDao articleDao = getArticleDao();

        Article article = getArticle(articleId, articleDao);
        if (article == null) {
            Log.w(TAG, "deleteArticleBG() article was not found");
            return; // not an error?
        }

        List<Long> articleIds = Collections.singletonList(article.getId());

        DaoSession daoSession = getDaoSession();

        // delete related tag joins
        ArticleTagsJoin.getTagsJoinByArticleQueryBuilder(
                articleIds, daoSession.getArticleTagsJoinDao())
                .buildDelete().executeDeleteWithoutDetachingEntities();

        Collection<Long> annotationIds = Annotation.getAnnotationIdsByArticleIds(
                articleIds, daoSession.getAnnotationDao());

        // delete ranges of related annotations
        AnnotationRange.getAnnotationRangesByAnnotationsQueryBuilder(
                annotationIds, daoSession.getAnnotationRangeDao())
                .buildDelete().executeDeleteWithoutDetachingEntities();

        // delete related annotations
        daoSession.getAnnotationDao().deleteByKeyInTx(annotationIds);

        daoSession.getArticleContentDao().deleteByKey(article.getId());
        articleDao.delete(article);

        notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.DELETED);

        Log.d(TAG, "deleteArticleBG() article object deleted");

        queueOfflineChange(queueHelper -> queueHelper.deleteArticle(articleId));

        Log.d(TAG, "deleteArticleBG() finished");
    }

    public static void wipeDB(Settings settings) {
        DbUtils.runInNonExclusiveTx(getDaoSession(), session -> {
            FtsDao.deleteAllArticles(session.getDatabase());
            session.getAnnotationRangeDao().deleteAll();
            session.getAnnotationDao().deleteAll();
            session.getArticleContentDao().deleteAll();
            session.getArticleDao().deleteAll();
            session.getTagDao().deleteAll();
            session.getArticleTagsJoinDao().deleteAll();
            session.getQueueItemDao().deleteAll();
        });

        settings.setLatestUpdatedItemTimestamp(0);
        settings.setLatestUpdateRunTimestamp(0);
        settings.setFirstSyncDone(false);

        EventHelper.notifyEverythingRemoved();
    }

    private static void queueOfflineArticleChange(int articleId,
                                                  QueueItem.ArticleChangeType changeType) {
        queueOfflineChange(queueHelper -> queueHelper.changeArticle(articleId, changeType));
    }

    private static void queueOfflineChange(Consumer<QueueHelper> action) {
        long queueChangedLength;

        queueChangedLength = DbUtils.callInNonExclusiveTx(getDaoSession(), session -> {
            QueueHelper queueHelper = new QueueHelper(session);

            action.accept(queueHelper);

            return queueHelper.getQueueLength();
        });

        postEvent(new OfflineQueueChangedEvent(queueChangedLength, true));
    }

    private static ArticleDao getArticleDao() {
        return getDaoSession().getArticleDao();
    }

    private static DaoSession getDaoSession() {
        return DbConnection.getSession();
    }

    private static Article getArticle(int articleId, ArticleDao articleDao) {
        return articleDao.queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(articleId))
                .build().unique();
    }

}
