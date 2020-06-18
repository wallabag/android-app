package fr.gaulupeau.apps.Poche.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import java.io.File;

import fr.gaulupeau.apps.InThePoche.BuildConfig;

public class WallabagFileProvider extends FileProvider {

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider";

    private static final String TAG = WallabagFileProvider.class.getSimpleName();

    public static Uri getUriForFile(@NonNull Context context, @NonNull File file) {
        return getUriForFile(context, AUTHORITY, file);
    }

    public static boolean shareFile(@NonNull Activity activity, @NonNull File file) {
        try {
            Uri uri = getUriForFile(activity, file);

            ShareCompat.IntentBuilder shareBuilder = ShareCompat.IntentBuilder.from(activity)
                    .setStream(uri)
                    .setType(activity.getContentResolver().getType(uri));

            shareBuilder.getIntent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            shareBuilder.startChooser();

            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error sharing file", e);
        }

        return false;
    }

}
