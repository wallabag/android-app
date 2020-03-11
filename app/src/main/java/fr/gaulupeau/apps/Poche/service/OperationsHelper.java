package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.util.Log;

import java.util.List;

import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.DbUtils;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.FtsDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Annotation;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.events.EventHelper;
import fr.gaulupeau.apps.Poche.service.tasks.AddArticleTask;
import fr.gaulupeau.apps.Poche.service.tasks.ArticleChangeTask;
import fr.gaulupeau.apps.Poche.service.tasks.DeleteArticleTask;
import fr.gaulupeau.apps.Poche.service.tasks.UpdateArticleProgressTask;
import fr.gaulupeau.apps.Poche.service.workers.ArticleUpdater;
import fr.gaulupeau.apps.Poche.service.workers.OperationsWorker;
import wallabag.apiwrapper.WallabagService;

import static fr.gaulupeau.apps.Poche.service.ServiceHelper.enqueueServiceTask;
import static fr.gaulupeau.apps.Poche.service.ServiceHelper.enqueueSimpleServiceTask;
import static fr.gaulupeau.apps.Poche.service.ServiceHelper.startService;

public class OperationsHelper {

    private static final String TAG = OperationsHelper.class.getSimpleName();

    public static void addArticle(Context context, String url) {
        addArticle(context, url, null);
    }

    public static void addArticle(Context context, String url, String originUrl) {
        Log.d(TAG, "addArticle() started");

        enqueueSimpleServiceTask(context, new AddArticleTask(url, originUrl));
    }

    public static void archiveArticle(Context context, int articleId, boolean archive) {
        enqueueSimpleServiceTask(context, ArticleChangeTask.newArchiveTask(articleId, archive));
    }

    public static void favoriteArticle(Context context, int articleId, boolean favorite) {
        enqueueSimpleServiceTask(context, ArticleChangeTask.newFavoriteTask(articleId, favorite));
    }

    public static void changeArticleTitle(Context context, int articleId, String title) {
        enqueueSimpleServiceTask(context, ArticleChangeTask.newChangeTitleTask(articleId, title));
    }

    public static void setArticleProgress(Context context, int articleId, double progress) {
        enqueueSimpleServiceTask(context, new UpdateArticleProgressTask(articleId, progress));
    }

    public static void setArticleTags(Context context, int articleId, List<Tag> newTags,
                                      Runnable postCallCallback) {
        enqueueServiceTask(context, ctx -> new OperationsWorker(ctx)
                .setArticleTags(articleId, newTags), postCallCallback);
    }

    public static void addAnnotation(Context context, int articleId, Annotation annotation) {
        enqueueServiceTask(context, ctx -> new OperationsWorker(ctx)
                .addAnnotation(articleId, annotation), null);
    }

    public static void updateAnnotation(Context context, int articleId, Annotation annotation) {
        enqueueServiceTask(context, ctx -> new OperationsWorker(ctx)
                .updateAnnotation(articleId, annotation), null);
    }

    public static void deleteAnnotation(Context context, int articleId, Annotation annotation) {
        enqueueServiceTask(context, ctx -> new OperationsWorker(ctx)
                .deleteAnnotation(articleId, annotation), null);
    }

    public static void deleteArticle(Context context, int articleId) {
        enqueueSimpleServiceTask(context, new DeleteArticleTask(articleId));
    }

    public static void wipeDB(Settings settings) {
        DbUtils.runInNonExclusiveTx(DbConnection.getSession(), session -> {
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

    public static void syncAndUpdate(Context context, Settings settings,
                                     ArticleUpdater.UpdateType updateType, boolean auto) {
        syncAndUpdate(context, settings, updateType, auto, null);
    }

    private static void syncAndUpdate(Context context, Settings settings,
                                      ArticleUpdater.UpdateType updateType,
                                      boolean auto, Long operationID) {
        Log.d(TAG, "syncAndUpdate() started");

        if (settings != null && settings.isOfflineQueuePending()) {
            Log.d(TAG, "syncAndUpdate() running sync and update");

            ActionRequest syncRequest = getSyncQueueRequest(auto, false);
            syncRequest.setNextRequest(getUpdateArticlesRequest(settings, updateType, auto, operationID));

            startService(context, syncRequest);
        } else {
            updateArticles(context, settings, updateType, auto, operationID);
        }
    }

    public static void syncQueue(Context context) {
        syncQueue(context, false, false);
    }

    public static void syncQueue(Context context, boolean auto) {
        syncQueue(context, auto, false);
    }

    public static void syncQueue(Context context, boolean auto, boolean byOperation) {
        Log.d(TAG, "syncQueue() started");

        startService(context, getSyncQueueRequest(auto, byOperation));
    }

    private static ActionRequest getSyncQueueRequest(boolean auto, boolean byOperation) {
        ActionRequest request = new ActionRequest(ActionRequest.Action.SYNC_QUEUE);
        if (auto) request.setRequestType(ActionRequest.RequestType.AUTO);
        else if (byOperation) request.setRequestType(ActionRequest.RequestType.MANUAL_BY_OPERATION);

        return request;
    }

    public static void updateArticles(Context context, Settings settings,
                                      ArticleUpdater.UpdateType updateType,
                                      boolean auto, Long operationID) {
        Log.d(TAG, "updateArticles() started");

        startService(context, getUpdateArticlesRequest(settings, updateType, auto, operationID));
    }

    private static ActionRequest getUpdateArticlesRequest(Settings settings,
                                                          ArticleUpdater.UpdateType updateType,
                                                          boolean auto, Long operationID) {
        ActionRequest request = new ActionRequest(ActionRequest.Action.UPDATE_ARTICLES);
        request.setUpdateType(updateType);
        request.setOperationID(operationID);
        if (auto) request.setRequestType(ActionRequest.RequestType.AUTO);

        if (updateType == ArticleUpdater.UpdateType.FAST && settings.isSweepingAfterFastSyncEnabled()) {
            request.setNextRequest(getSweepDeletedArticlesRequest(auto, operationID));
        }

        if (settings.isImageCacheEnabled()) {
            addNextRequest(request, getFetchImagesRequest());
        }

        return request;
    }

    public static void sweepDeletedArticles(Context context) {
        Log.d(TAG, "sweepDeletedArticles() started");

        startService(context, getSweepDeletedArticlesRequest(false, null));
    }

    private static ActionRequest getSweepDeletedArticlesRequest(boolean auto, Long operationID) {
        ActionRequest request = new ActionRequest(ActionRequest.Action.SWEEP_DELETED_ARTICLES);
        request.setOperationID(operationID);
        if (auto) request.setRequestType(ActionRequest.RequestType.AUTO);

        return request;
    }

    public static void downloadArticleAsFile(Context context, int articleID,
                                             WallabagService.ResponseFormat downloadFormat,
                                             Long operationID) {
        Log.d(TAG, "downloadArticleAsFile() started; download format: " + downloadFormat);

        ActionRequest request = new ActionRequest(ActionRequest.Action.DOWNLOAD_AS_FILE);
        request.setArticleID(articleID);
        request.setDownloadFormat(downloadFormat);
        request.setOperationID(operationID);

        startService(context, request);
    }

    public static void fetchImages(Context context) {
        Log.d(TAG, "fetchImages() started");

        startService(context, getFetchImagesRequest());
    }

    private static ActionRequest getFetchImagesRequest() {
        return new ActionRequest(ActionRequest.Action.FETCH_IMAGES);
    }

    private static void addNextRequest(ActionRequest actionRequest, ActionRequest nextRequest) {
        while (actionRequest.getNextRequest() != null)
            actionRequest = actionRequest.getNextRequest();

        actionRequest.setNextRequest(nextRequest);
    }

}
