package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.Poche.data.DbUtils;
import fr.gaulupeau.apps.Poche.data.QueueHelper;
import fr.gaulupeau.apps.Poche.data.dao.AnnotationDao;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.entities.AddLinkItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.AddOrUpdateAnnotationItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.AnnotationRange;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleChangeItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.ArticleTagsDeleteItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.DeleteAnnotationItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.LinkUploadedEvent;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueFinishedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueProgressEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueStartedEvent;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
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
        boolean urlUploaded = false;

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

                        AddLinkItem addLinkItem = item.asSpecificItem();
                        String link = addLinkItem.getUrl();
                        String origin = addLinkItem.getOrigin();
                        Log.d(TAG, "syncOfflineQueue() action ADD_LINK link=" + link
                                + ", origin=" + origin);

                        if (!TextUtils.isEmpty(link)) {
                            getWallabagService()
                                    .addArticleBuilder(link)
                                    .originUrl(origin)
                                    .execute();
                            urlUploaded = true;
                        } else {
                            Log.w(TAG, "syncOfflineQueue() action is ADD_LINK, but item has no link; skipping");
                        }
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

        if (urlUploaded) {
            postEvent(new LinkUploadedEvent(new ActionResult()));
        }

        Log.d(TAG, "syncOfflineQueue() finished");
        return new Pair<>(result, queueLength);
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

        for (QueueItem.ArticleChangeType changeType : item.getArticleChanges()) {
            switch (changeType) {
                case ARCHIVE:
                    builder.archive(article.getArchive());
                    break;

                case FAVORITE:
                    builder.starred(article.getFavorite());
                    break;

                case TITLE:
                    builder.title(article.getTitle());
                    break;

                case TAGS:
                    // all tags are pushed
                    for (Tag tag : article.getTags()) {
                        builder.tag(tag.getLabel());
                    }
                    break;

                default:
                    throw new IllegalStateException("Change type is not implemented: " + changeType);
            }
        }

        ActionResult itemResult = null;

        if (builder.execute() == null) {
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
