package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class ServiceHelper {

    private static final String TAG = ServiceHelper.class.getSimpleName();

    // TODO: reuse code

    public static void syncQueue(Context context) {
        Log.d(TAG, "syncQueue() started");

        Intent intent = new Intent(context, BGService.class);
        intent.setAction(BGService.ACTION_SYNC_QUEUE);

        context.startService(intent);

        Log.d(TAG, "syncQueue() finished");
    }

    public static void addLink(Context context, String link) {
        addLink(context, link, null);
    }

    public static void addLink(Context context, String link, Long operationID) {
        Log.d(TAG, "archiveArticle() started");

        Intent intent = new Intent(context, BGService.class);
        intent.setAction(BGService.ACTION_ADD_LINK);
        intent.putExtra(BGService.EXTRA_LINK, link);
        if(operationID != null) intent.putExtra(BGService.EXTRA_OPERATION_ID, operationID);

        context.startService(intent);

        Log.d(TAG, "archiveArticle() finished");
    }

    public static void archiveArticle(Context context, int articleID) {
        archiveArticle(context, articleID, true);
    }

    public static void unarchiveArticle(Context context, int articleID) {
        archiveArticle(context, articleID, false);
    }

    public static void archiveArticle(Context context, int articleID, boolean archive) {
        Log.d(TAG, "archiveArticle() started");

        Intent intent = new Intent(context, BGService.class);
        intent.setAction(archive
                ? BGService.ACTION_ARCHIVE
                : BGService.ACTION_UNARCHIVE);
        intent.putExtra(BGService.EXTRA_ARTICLE_ID, articleID);

        context.startService(intent);

        Log.d(TAG, "archiveArticle() finished");
    }

    public static void favoriteArticle(Context context, int articleID) {
        favoriteArticle(context, articleID, true);
    }

    public static void unfavoriteArticle(Context context, int articleID) {
        favoriteArticle(context, articleID, false);
    }

    public static void favoriteArticle(Context context, int articleID, boolean favorite) {
        Log.d(TAG, "favoriteArticle() started");

        Intent intent = new Intent(context, BGService.class);
        intent.setAction(favorite
                ? BGService.ACTION_FAVORITE
                : BGService.ACTION_UNFAVORITE);
        intent.putExtra(BGService.EXTRA_ARTICLE_ID, articleID);

        context.startService(intent);

        Log.d(TAG, "favoriteArticle() finished");
    }

    public static void deleteArticle(Context context, int articleID) {
        Log.d(TAG, "deleteArticle() started");

        Intent intent = new Intent(context, BGService.class);
        intent.setAction(BGService.ACTION_DELETE);
        intent.putExtra(BGService.EXTRA_ARTICLE_ID, articleID);

        context.startService(intent);

        Log.d(TAG, "deleteArticle() finished");
    }

    public static void updateFeed(Context context,
                                  FeedUpdater.FeedType feedType,
                                  FeedUpdater.UpdateType updateType) {
        updateFeed(context, feedType, updateType, null);
    }

    public static void updateFeed(Context context,
                                  FeedUpdater.FeedType feedType,
                                  FeedUpdater.UpdateType updateType,
                                  Long operationID) {
        Log.d(TAG, "updateFeed() started");

        Intent intent = new Intent(context, BGService.class);
        intent.setAction(BGService.ACTION_UPDATE_FEED);
        if(feedType != null) {
            intent.putExtra(BGService.EXTRA_UPDATE_FEED_FEED_TYPE, feedType.toString());
        }
        if(updateType != null) {
            intent.putExtra(BGService.EXTRA_UPDATE_FEED_UPDATE_TYPE, updateType.toString());
        }
        if(operationID != null) {
            intent.putExtra(BGService.EXTRA_OPERATION_ID, (long)operationID);
        }

        context.startService(intent);

        Log.d(TAG, "updateFeed() finished");
    }

}
