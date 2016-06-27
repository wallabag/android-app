package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ServiceHelper {

    private static final String TAG = ServiceHelper.class.getSimpleName();

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
        intent.putExtra(BGService.EXTRA_ID, articleID);

        context.startService(intent);

        Log.d(TAG, "archiveArticle() finished");
    }

}
