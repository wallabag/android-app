package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import fr.gaulupeau.apps.Poche.data.DbUtils;
import fr.gaulupeau.apps.Poche.data.QueueHelper;
import fr.gaulupeau.apps.Poche.data.dao.AnnotationDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;

import static fr.gaulupeau.apps.Poche.events.EventHelper.notifyAboutArticleChange;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;

public class OperationsWorker extends BaseWorker {

    private static final String TAG = OperationsWorker.class.getSimpleName();

    public OperationsWorker(Context context) {
        super(context);
    }

    public void addArticle(String link, String origin) {
        queueOfflineChange(queueHelper -> queueHelper.addLink(link, origin));
    }

    public void archiveArticle(int articleId, boolean archive) {
        Log.d(TAG, String.format("archiveArticle(%d, %s) started", articleId, archive));

        Article article = getArticle(articleId);
        if (article == null) {
            Log.w(TAG, "archiveArticle() article was not found");
            return; // not an error?
        }

        if (article.getArchive() != archive) {
            article.setArchive(archive);
            article.update();

            ArticlesChangedEvent.ChangeType changeType = archive
                    ? ArticlesChangedEvent.ChangeType.ARCHIVED
                    : ArticlesChangedEvent.ChangeType.UNARCHIVED;

            notifyAboutArticleChange(article, changeType);

            Log.d(TAG, "archiveArticle() article object updated");
        } else {
            Log.d(TAG, "archiveArticle(): article state was not changed");

            // do we need to continue with the sync part? Probably yes
        }

        queueOfflineArticleChange(articleId, QueueItem.ArticleChangeType.ARCHIVE);

        Log.d(TAG, "archiveArticle() finished");
    }

    public void favoriteArticle(int articleId, boolean favorite) {
        Log.d(TAG, String.format("favoriteArticle(%d, %s) started", articleId, favorite));

        Article article = getArticle(articleId);
        if (article == null) {
            Log.w(TAG, "favoriteArticle() article was not found");
            return; // not an error?
        }

        if (article.getFavorite() != favorite) {
            article.setFavorite(favorite);
            article.update();

            ArticlesChangedEvent.ChangeType changeType = favorite
                    ? ArticlesChangedEvent.ChangeType.FAVORITED
                    : ArticlesChangedEvent.ChangeType.UNFAVORITED;

            notifyAboutArticleChange(article, changeType);

            Log.d(TAG, "favoriteArticle() article object updated");
        } else {
            Log.d(TAG, "favoriteArticle(): article state was not changed");

            // do we need to continue with the sync part? Probably yes
        }

        queueOfflineArticleChange(articleId, QueueItem.ArticleChangeType.FAVORITE);

        Log.d(TAG, "favoriteArticle() finished");
    }

    public void changeArticleTitle(int articleId, String title) {
        Log.d(TAG, String.format("changeArticleTitle(%d, %s) started", articleId, title));

        Article article = getArticle(articleId);
        if (article == null) {
            Log.w(TAG, "changeArticleTitle() article was not found");
            return; // not an error?
        }

        article.setTitle(title);
        article.update();

        notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.TITLE_CHANGED);

        queueOfflineArticleChange(articleId, QueueItem.ArticleChangeType.TITLE);

        Log.d(TAG, "changeArticleTitle() finished");
    }

    public void setArticleProgress(int articleId, double progress) {
        Log.d(TAG, String.format("setArticleProgress(%d, %g) started", articleId, progress));

        Article article = getArticle(articleId);
        if (article == null) {
            Log.w(TAG, "setArticleProgress() article was not found");
            return; // not an error?
        }

        article.setArticleProgress(progress);
        article.update();

        Log.d(TAG, "setArticleProgress() finished");
    }

    public void deleteArticle(int articleId) {
        Log.d(TAG, String.format("deleteArticle(%d) started", articleId));

        Article article = getArticle(articleId);
        if (article == null) {
            Log.w(TAG, "deleteArticle() article was not found");
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
        article.delete();

        notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.DELETED);

        Log.d(TAG, "deleteArticle() article object deleted");

        queueOfflineChange(queueHelper -> queueHelper.deleteArticle(articleId));

        Log.d(TAG, "deleteArticle() finished");
    }

    public void setArticleTags(int articleId, List<Tag> newTags) {
        Log.d(TAG, String.format("setArticleTags(%d, %s) started", articleId, newTags));

        boolean tagsChanged = false;

        Article article = getArticle(articleId);
        TagDao tagDao = getDaoSession().getTagDao();
        ArticleTagsJoinDao joinDao = getDaoSession().getArticleTagsJoinDao();

        if (article == null) {
            Log.w(TAG, "setArticleTags() article was not found");
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

            Log.d(TAG, "setArticleTags() storing deleted tags to offline queue");
            queueOfflineChange(queueHelper
                    -> queueHelper.deleteTagsFromArticle(articleId, tagsToDelete));
        }

        if (tagsChanged) {
            notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.TAGS_CHANGED);

            Log.d(TAG, "setArticleTags() storing tags change to offline queue");
            queueOfflineArticleChange(articleId, QueueItem.ArticleChangeType.TAGS);
        }

        Log.d(TAG, "setArticleTags() finished");
    }

    public void addAnnotation(int articleId, Annotation annotation) {
        Log.d(TAG, String.format("addAnnotation(%d, %s) started", articleId, annotation));

        Article article = getArticle(articleId);

        Long annotationId = annotation.getId();

        if (annotationId == null) {
            annotation.setArticleId(article.getId());

            getDaoSession().getAnnotationDao().insert(annotation);
            annotationId = annotation.getId();

            List<AnnotationRange> ranges = annotation.getRanges();
            if (!ranges.isEmpty()) {
                for (AnnotationRange range : ranges) {
                    range.setAnnotationId(annotationId);
                }
                getDaoSession().getAnnotationRangeDao().insertInTx(ranges);
            }

            Log.d(TAG, "addAnnotation() annotation object inserted");
        } else {
            Log.d(TAG, "addAnnotation() annotation was already persisted");
        }

        if (article != null) {
            if (!article.getAnnotations().contains(annotation)) {
                article.getAnnotations().add(annotation);
            }
            notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.ANNOTATIONS_CHANGED);
        }

        Log.d(TAG, "addAnnotation() ID: " + annotationId);
        if (annotationId != null) {
            long finalAnnotationId = annotationId;
            queueOfflineChange(queueHelper
                    -> queueHelper.addAnnotationToArticle(articleId, finalAnnotationId));
        }

        Log.d(TAG, "addAnnotation() finished");
    }

    public void updateAnnotation(int articleId, Annotation annotation) {
        Log.d(TAG, String.format("updateAnnotation(%d, %s) started", articleId, annotation));

        Long annotationId = annotation.getId();

        if (annotationId == null) {
            throw new RuntimeException("Annotation wasn't persisted first");
        }

        String newText = annotation.getText();

        AnnotationDao annotationDao = getDaoSession().getAnnotationDao();
        annotation = annotationDao.queryBuilder()
                .where(AnnotationDao.Properties.Id.eq(annotationId)).unique();

        if (TextUtils.equals(annotation.getText(), newText)) {
            Log.w(TAG, "updateAnnotation() annotation ID=" + annotationId
                    + " already has text=" + newText);
            return;
        }

        annotation.setText(newText);

        annotationDao.update(annotation);
        Log.d(TAG, "updateAnnotation() annotation object updated");

        Article article = getArticle(articleId);
        if (article != null) {
            notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.ANNOTATIONS_CHANGED);
        }

        Log.d(TAG, "updateAnnotation() ID: " + annotationId);
        queueOfflineChange(queueHelper
                -> queueHelper.updateAnnotationOnArticle(articleId, annotationId));

        Log.d(TAG, "updateAnnotation() finished");
    }

    public void deleteAnnotation(int articleId, Annotation annotation) {
        Log.d(TAG, String.format("deleteAnnotation(%d, %s) started", articleId, annotation));

        Integer remoteId = annotation.getAnnotationId();

        if (annotation.getId() != null) {
            List<AnnotationRange> ranges = annotation.getRanges();
            if (!ranges.isEmpty()) {
                getDaoSession().getAnnotationRangeDao().deleteInTx(ranges);
            }

            getDaoSession().getAnnotationDao().delete(annotation);
            Log.d(TAG, "deleteAnnotation() annotation object deleted");
        } else {
            Log.d(TAG, "deleteAnnotation() annotation was not persisted");
        }

        Article article = getArticle(articleId);
        if (article != null) {
            article.getAnnotations().remove(annotation);
            notifyAboutArticleChange(article, ArticlesChangedEvent.ChangeType.ANNOTATIONS_CHANGED);
        }

        Log.d(TAG, "deleteAnnotation() remote ID: " + remoteId);
        if (remoteId != null) {
            queueOfflineChange(queueHelper
                    -> queueHelper.deleteAnnotationFromArticle(articleId, remoteId));
        }

        Log.d(TAG, "deleteAnnotation() finished");
    }

    private Article getArticle(int articleId) {
        return getArticleDao().queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(articleId))
                .build().unique();
    }

    private ArticleDao getArticleDao() {
        return getDaoSession().getArticleDao();
    }

    private void queueOfflineArticleChange(int articleId,
                                                 QueueItem.ArticleChangeType changeType) {
        queueOfflineChange(queueHelper -> queueHelper.changeArticle(articleId, changeType));
    }

    private void queueOfflineChange(Consumer<QueueHelper> action) {
        long queueChangedLength;

        queueChangedLength = DbUtils.callInNonExclusiveTx(getDaoSession(), session -> {
            QueueHelper queueHelper = new QueueHelper(session);

            action.accept(queueHelper);

            return queueHelper.getQueueLength();
        });

        postEvent(new OfflineQueueChangedEvent(queueChangedLength, true));
    }

}
