package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

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
        Log.d(TAG, "archiveArticle() started");

        Intent intent = new Intent(context, BGService.class);
        intent.setAction(BGService.ACTION_ADD_LINK);
        intent.putExtra(BGService.EXTRA_LINK, link);

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

}
