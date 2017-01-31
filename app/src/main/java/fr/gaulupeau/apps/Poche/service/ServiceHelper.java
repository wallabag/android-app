package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import fr.gaulupeau.apps.Poche.network.Updater;

public class ServiceHelper {

    private static final String TAG = ServiceHelper.class.getSimpleName();

    public static void syncQueue(Context context) {
        syncQueue(context, false, false, null);
    }

    public static void syncQueue(Context context, boolean auto) {
        syncQueue(context, auto, false, null);
    }

    public static void syncQueue(Context context, boolean auto,
                                 boolean byOperation, Long queueLength) {
        Log.d(TAG, "syncQueue() started");

        ActionRequest request = new ActionRequest(ActionRequest.Action.SYNC_QUEUE);
        if(auto) request.setRequestType(ActionRequest.RequestType.AUTO);
        else if(byOperation) request.setRequestType(ActionRequest.RequestType.MANUAL_BY_OPERATION);
        if(queueLength != null) request.setQueueLength(queueLength);

        startService(context, request, true);

        Log.d(TAG, "syncQueue() finished");
    }

    public static void addLink(Context context, String link) {
        addLink(context, link, null);
    }

    public static void addLink(Context context, String link, Long operationID) {
        Log.d(TAG, "addLink() started");

        ActionRequest request = new ActionRequest(ActionRequest.Action.ADD_LINK);
        request.setLink(link);
        request.setOperationID(operationID);

        startService(context, request, true);

        Log.d(TAG, "addLink() finished");
    }

    public static void archiveArticle(Context context, int articleID, boolean archive) {
        Log.d(TAG, "archiveArticle() started");

        ActionRequest request = new ActionRequest(
                archive ? ActionRequest.Action.ARCHIVE : ActionRequest.Action.UNARCHIVE);
        request.setArticleID(articleID);

        startService(context, request, true);

        Log.d(TAG, "archiveArticle() finished");
    }

    public static void favoriteArticle(Context context, int articleID, boolean favorite) {
        Log.d(TAG, "favoriteArticle() started");

        ActionRequest request = new ActionRequest(
                favorite ? ActionRequest.Action.FAVORITE : ActionRequest.Action.UNFAVORITE);
        request.setArticleID(articleID);

        startService(context, request, true);

        Log.d(TAG, "favoriteArticle() finished");
    }

    public static void deleteArticle(Context context, int articleID) {
        Log.d(TAG, "deleteArticle() started");

        ActionRequest request = new ActionRequest(ActionRequest.Action.DELETE);
        request.setArticleID(articleID);

        startService(context, request, true);

        Log.d(TAG, "deleteArticle() finished");
    }

    public static void updateFeed(Context context, Updater.UpdateType updateType) {
        updateFeed(context, updateType, null, false);
    }

    public static void updateFeed(Context context,
                                  Updater.UpdateType updateType,
                                  Long operationID, boolean auto) {
        Log.d(TAG, "updateFeed() started");

        ActionRequest request = new ActionRequest(ActionRequest.Action.UPDATE_FEED);
        request.setUpdateType(updateType);
        request.setOperationID(operationID);
        if(auto) request.setRequestType(ActionRequest.RequestType.AUTO);

        startService(context, request, true);

        Log.d(TAG, "updateFeed() finished");
    }

    public static void downloadArticleAsPDF(Context context, int articleID, Long operationID) {
        downloadArticleAsFile(context, articleID, ActionRequest.DownloadFormat.PDF, operationID);
    }

    public static void downloadArticleAsFile(Context context, int articleID,
                                             ActionRequest.DownloadFormat downloadFormat,
                                             Long operationID) {
        Log.d(TAG, "downloadArticleAsFile() started");

        ActionRequest request = new ActionRequest(ActionRequest.Action.DOWNLOAD_AS_FILE);
        request.setArticleID(articleID);
        request.setDownloadFormat(downloadFormat);
        request.setOperationID(operationID);

        startService(context, request, false);

        Log.d(TAG, "downloadArticleAsFile() finished");
    }

    public static void fetchImages(Context context) {
        Log.d(TAG, "fetchImages() started");

        startService(context, new ActionRequest(ActionRequest.Action.FETCH_IMAGES), false);

        Log.d(TAG, "fetchImages() finished");
    }

    private static void startService(Context context, ActionRequest request, boolean mainService) {
        Intent intent = new Intent(context, mainService ? MainService.class : SecondaryService.class);
        intent.putExtra(ActionRequest.ACTION_REQUEST, request);

        context.startService(intent);
    }

}
