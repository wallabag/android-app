package fr.gaulupeau.apps.Poche.service.workers;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.core.util.ObjectsCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fr.gaulupeau.apps.Poche.data.DbUtils;
import fr.gaulupeau.apps.Poche.data.QueueHelper;
import fr.gaulupeau.apps.Poche.data.dao.AnnotationDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleTagsJoinDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.entities.AddLinkItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.AddOrUpdateAnnotationItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleChangeItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsDeleteItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsJoin;
import fr.gaulupeau.apps.Poche.data.dao.entities.DeleteAnnotationItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.LocalArticleReplacedEvent;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueFinishedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueProgressEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueStartedEvent;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;
import wallabag.apiwrapper.AddArticleBuilder;
import wallabag.apiwrapper.ModifyArticleBuilder;
import wallabag.apiwrapper.WallabagService;
import wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class OfflineChangesSynchronizer extends BaseNetworkWorker {

    private static final String TAG = OfflineChangesSynchronizer.class.getSimpleName();

    public OfflineChangesSynchronizer(Context context) {
        super(context);
    }

    public ActionResult synchronize(ActionRequest actionRequest) {
        Log.d(TAG, "synchronizeOfflineChanges() started");

        ActionResult result = null;

        SyncQueueStartedEvent startEvent = new SyncQueueStartedEvent(actionRequest);
        postStickyEvent(startEvent);

        Pair<ActionResult, Long> syncResult = null;
        try {
            syncResult = syncOfflineQueue(actionRequest);
            result = syncResult.first;
        } finally {
            removeStickyEvent(startEvent);

            if (result == null) result = new ActionResult(ActionResult.ErrorType.UNKNOWN);
            postEvent(new SyncQueueFinishedEvent(actionRequest, result,
                    syncResult != null ? syncResult.second : null));
        }

        Log.d(TAG, "synchronizeOfflineChanges() finished");
        return result;
    }

    private Pair<ActionResult, Long> syncOfflineQueue(ActionRequest actionRequest) {
        Log.d(TAG, "syncOfflineQueue() started");

        if (!WallabagConnection.isNetworkAvailable()) {
            Log.i(TAG, "syncOfflineQueue() not on-line; exiting");
            return new Pair<>(new ActionResult(ActionResult.ErrorType.NO_NETWORK), null);
        }

        ActionResult result = new ActionResult();

        DaoSession daoSession = getDaoSession();
        QueueHelper queueHelper = new QueueHelper(daoSession);

        QueueHelper.QueueState queueState = queueHelper.getQueueState();

        List<QueueItem> queueItems = queueState.getQueueItems();

        int counter = 0, totalNumber = queueItems.size();
        for (QueueItem item : queueItems) {
            Log.d(TAG, "syncOfflineQueue() current QueueItem(" + (counter + 1) + " out of " + totalNumber + "): " + item);
            postEvent(new SyncQueueProgressEvent(actionRequest, counter, totalNumber));

            Integer articleIdInteger = item.getArticleId();

            Log.d(TAG, String.format(
                    "syncOfflineQueue() processing: queue item ID: %d, article ID: \"%s\"",
                    item.getId(), articleIdInteger));

            int articleId = articleIdInteger != null ? articleIdInteger : -1;

            boolean canTolerateNotFound = true;

            ActionResult itemResult = null;
            try {
                switch (item.getAction()) {
                    case ARTICLE_CHANGE:
                        itemResult = syncArticleChange(item.asSpecificItem(), articleId);
                        break;

                    case ARTICLE_TAGS_DELETE:
                        itemResult = syncDeleteTagsFromArticle(item.asSpecificItem(), articleId);
                        break;

                    case ANNOTATION_ADD:
                        itemResult = syncAddAnnotationToArticle(item.asSpecificItem(), articleId);
                        break;

                    case ANNOTATION_UPDATE:
                        itemResult = syncUpdateAnnotationOnArticle(item.asSpecificItem(), articleId);
                        break;

                    case ANNOTATION_DELETE:
                        itemResult = syncDeleteAnnotationFromArticle(item.asSpecificItem(), articleId);
                        break;

                    case ARTICLE_DELETE:
                        if (!getWallabagService().deleteArticle(articleId)) {
                            itemResult = new ActionResult(ActionResult.ErrorType.NOT_FOUND);
                        }
                        break;

                    case ADD_LINK: {
                        canTolerateNotFound = false;

                        addLink(item.asSpecificItem());
                        break;
                    }

                    default:
                        throw new IllegalArgumentException("Unknown action: " + item.getAction());
                }
            } catch (IncorrectConfigurationException | UnsuccessfulResponseException
                    | IOException | IllegalArgumentException e) {
                ActionResult r = processException(e, "syncOfflineQueue()");
                if (!r.isSuccess()) itemResult = r;
            } catch (Exception e) {
                Log.e(TAG, "syncOfflineQueue() item processing exception", e);

                itemResult = new ActionResult(ActionResult.ErrorType.UNKNOWN, e);
            }

            if (itemResult != null && !itemResult.isSuccess() && canTolerateNotFound
                    && itemResult.getErrorType() == ActionResult.ErrorType.NOT_FOUND) {
                Log.i(TAG, "syncOfflineQueue() ignoring NOT_FOUND");
                itemResult = null;
            }

            if (itemResult == null || itemResult.isSuccess()) {
                queueState.completed(item);
            } else if (itemResult.getErrorType() != null) {
                ActionResult.ErrorType itemError = itemResult.getErrorType();

                Log.i(TAG, "syncOfflineQueue() itemError: " + itemError);

                boolean stop = true;
                switch (itemError) {
                    case NOT_FOUND_LOCALLY:
                    case NEGATIVE_RESPONSE:
                        stop = false;
                        break;
                }

                if (stop) {
                    result.updateWith(itemResult);
                    Log.i(TAG, "syncOfflineQueue() the itemError is a showstopper; breaking");
                    break;
                }
            } else { // should not happen
                Log.w(TAG, "syncOfflineQueue() errorType is not present in itemResult");
            }

            Log.d(TAG, "syncOfflineQueue() finished processing queue item");
        }

        Long queueLength = null;

        if (queueState.hasChanges()) {
            queueLength = DbUtils.callInNonExclusiveTx(daoSession, (s) -> {
                queueHelper.save(queueState);

                return queueHelper.getQueueLength();
            });
        }

        if (queueLength != null) {
            postEvent(new OfflineQueueChangedEvent(queueLength));
        } else {
            queueLength = (long) queueState.itemsLeft();
        }

        Log.d(TAG, "syncOfflineQueue() finished");
        return new Pair<>(result, queueLength);
    }

    private void addLink(AddLinkItem item) throws IncorrectConfigurationException,
            UnsuccessfulResponseException, IOException {
        String link = item.getUrl();
        String origin = item.getOrigin();
        Log.d(TAG, "addLink() link=" + link + ", origin=" + origin);

        if (TextUtils.isEmpty(link)) {
            Log.w(TAG, "addLink() action has no link; skipping");
            return;
        }

        ArticleDao articleDao = getDaoSession().getArticleDao();

        Article local = null;
        if (item.getLocalArticleId() != null) {
            local = articleDao.queryBuilder()
                    .where(ArticleDao.Properties.Id.eq(item.getLocalArticleId()))
                    .unique();
        }

        WallabagService wallabagService = getWallabagService();

        AddArticleBuilder addArticleBuilder = wallabagService
                .addArticleBuilder(link)
                .originUrl(origin);

        if (local != null) {
            addArticleBuilder
                    .archive(local.getArchive())
                    .starred(local.getFavorite());

            for (Tag tag : local.getTags()) {
                addArticleBuilder.tag(tag.getLabel());
            }
        }

        wallabag.apiwrapper.models.Article article = addArticleBuilder.execute();

        LocalArticleReplacedEvent articleReplacedEvent = null;
        if (local != null) {
            articleReplacedEvent = new LocalArticleReplacedEvent(
                    local.getId(), article.id, local.getGivenUrl());
        }

        Article existing = articleDao.queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(article.id))
                .unique();

        if (existing != null) {
            Log.i(TAG, "addLink() the article was already bagged as " + article.id);

            if (local != null) {
                updateGivenUrl(existing, local.getGivenUrl());
            }

            // don't store the received article due to the risk of overwriting local changes
            // (that may be further in the queue)
            article = null;
        }

        ArticlesChangedEvent event = new ArticlesChangedEvent();

        if (article != null) {
            new ArticleUpdater(getDaoSession(), wallabagService)
                    .updateArticles(event, Collections.singleton(article));

            if (local != null) {
                Article newArticle = articleDao.queryBuilder()
                        .where(ArticleDao.Properties.ArticleId.eq(article.id))
                        .unique();

                if (newArticle != null) {
                    updateGivenUrl(newArticle, local.getGivenUrl());
                }
            }
        }

        if (local != null) {
            articleDao.delete(local);

            ArticleTagsJoinDao articleTagsJoinDao = getDaoSession().getArticleTagsJoinDao();

            List<ArticleTagsJoin> joins = articleTagsJoinDao.queryBuilder()
                    .where(ArticleTagsJoinDao.Properties.ArticleId.eq(local.getId()))
                    .list();

            articleTagsJoinDao.deleteInTx(joins);
        }

        if (event.isAnythingChanged()) postEvent(event);
        if (articleReplacedEvent != null) postEvent(articleReplacedEvent);
    }

    private void updateGivenUrl(Article article, String givenUrl) {
        if (!ObjectsCompat.equals(article.getGivenUrl(), givenUrl)) {
            article.setGivenUrl(givenUrl);
            article.update();
        }
    }

    private ActionResult syncArticleChange(ArticleChangeItem item, int articleID)
            throws IncorrectConfigurationException, UnsuccessfulResponseException, IOException {
        Article article = getDaoSession().getArticleDao().queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(articleID)).unique();

        if (article == null) {
            return new ActionResult(ActionResult.ErrorType.NOT_FOUND_LOCALLY,
                    "Article is not found locally");
        }

        ModifyArticleBuilder builder = getWallabagService()
                .modifyArticleBuilder(articleID);

        boolean changed = false;

        for (QueueItem.ArticleChangeType changeType : item.getArticleChanges()) {
            switch (changeType) {
                case ARCHIVE:
                    builder.archive(Boolean.TRUE.equals(article.getArchive()));
                    changed = true;
                    break;

                case FAVORITE:
                    builder.starred(Boolean.TRUE.equals(article.getFavorite()));
                    changed = true;
                    break;

                case TITLE:
                    builder.title(article.getTitle());
                    changed = true;
                    break;

                case TAGS:
                    // all tags are pushed
                    for (Tag tag : article.getTags()) {
                        builder.tag(tag.getLabel());
                        changed = true;
                    }
                    break;

                default:
                    throw new IllegalStateException("Change type is not implemented: " + changeType);
            }
        }

        ActionResult itemResult = null;

        if (!changed) {
            Log.w(TAG, "syncArticleChange() no changes to send is item "
                    + item.genericItem().toString());
        }

        if (changed && builder.execute() == null) {
            itemResult = new ActionResult(ActionResult.ErrorType.NOT_FOUND);
        }

        return itemResult;
    }

    private ActionResult syncDeleteTagsFromArticle(ArticleTagsDeleteItem item, int articleID)
            throws IncorrectConfigurationException, UnsuccessfulResponseException, IOException {
        WallabagService wallabagService = getWallabagService();

        ActionResult itemResult = null;

        for (String tag : item.getTagIds()) {
            if (wallabagService.deleteTag(articleID, Integer.parseInt(tag)) == null
                    && itemResult == null) {
                itemResult = new ActionResult(ActionResult.ErrorType.NOT_FOUND);
            }
        }

        return itemResult;
    }

    private ActionResult syncAddAnnotationToArticle(AddOrUpdateAnnotationItem item, int articleId)
            throws IncorrectConfigurationException, UnsuccessfulResponseException, IOException {
        AnnotationDao annotationDao = getDaoSession().getAnnotationDao();
        Annotation annotation = annotationDao.queryBuilder()
                .where(AnnotationDao.Properties.Id.eq(item.getLocalAnnotationId())).unique();

        if (annotation == null) {
            return new ActionResult(ActionResult.ErrorType.NOT_FOUND_LOCALLY,
                    "Annotation wasn't found locally");
        }

        List<wallabag.apiwrapper.models.Annotation.Range> ranges
                = new ArrayList<>(annotation.getRanges().size());
        for (AnnotationRange range : annotation.getRanges()) {
            wallabag.apiwrapper.models.Annotation.Range apiRange
                    = new wallabag.apiwrapper.models.Annotation.Range();

            apiRange.start = range.getStart();
            apiRange.end = range.getEnd();
            apiRange.startOffset = range.getStartOffset();
            apiRange.endOffset = range.getEndOffset();

            ranges.add(apiRange);
        }

        wallabag.apiwrapper.models.Annotation remoteAnnotation = getWallabagService()
                .addAnnotation(articleId, ranges, annotation.getText(), annotation.getQuote());

        if (remoteAnnotation == null) {
            Log.w(TAG, String.format("Couldn't add annotation %s to article %d" +
                            ": article wasn't found on server",
                    annotation, articleId));
            return new ActionResult(ActionResult.ErrorType.NOT_FOUND);
        }

        annotation.setAnnotationId(remoteAnnotation.id);

        Log.d(TAG, "syncAddAnnotationToArticle() updating annotation with remote ID: "
                + annotation.getAnnotationId());
        annotationDao.update(annotation);
        Log.d(TAG, "syncAddAnnotationToArticle() updated annotation with remote ID");

        return null;
    }

    private ActionResult syncUpdateAnnotationOnArticle(AddOrUpdateAnnotationItem item, int articleId)
            throws IncorrectConfigurationException, UnsuccessfulResponseException, IOException {
        Annotation annotation = getDaoSession().getAnnotationDao().queryBuilder()
                .where(AnnotationDao.Properties.Id.eq(item.getLocalAnnotationId())).unique();

        if (annotation == null) {
            return new ActionResult(ActionResult.ErrorType.NOT_FOUND_LOCALLY,
                    "Annotation wasn't found locally");
        }
        if (annotation.getAnnotationId() == null) {
            Log.w(TAG, "syncUpdateAnnotationOnArticle() annotation ID is null!");
            return null;
        }

        if (getWallabagService()
                .updateAnnotation(annotation.getAnnotationId(), annotation.getText()) == null) {
            Log.w(TAG, String.format("Couldn't update annotation %s on article %d" +
                            ": not found remotely",
                    annotation, articleId));
            return new ActionResult(ActionResult.ErrorType.NOT_FOUND);
        }

        return null;
    }

    private ActionResult syncDeleteAnnotationFromArticle(DeleteAnnotationItem item, int articleId)
            throws IncorrectConfigurationException, UnsuccessfulResponseException, IOException {
        if (getWallabagService().deleteAnnotation(item.getRemoteAnnotationId()) == null) {
            Log.w(TAG, String.format("Couldn't remove annotationId %d from article %d",
                    item.getRemoteAnnotationId(), articleId));
            return new ActionResult(ActionResult.ErrorType.NOT_FOUND);
        }

        return null;
    }

}
