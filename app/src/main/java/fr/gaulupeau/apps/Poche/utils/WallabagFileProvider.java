package fr.gaulupeau.apps.Poche.utils;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;

import fr.gaulupeau.apps.InThePoche.BuildConfig;

public class WallabagFileProvider extends FileProvider {

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider";

    public static Uri getUriForFile(@NonNull Context context, @NonNull File file) {
        return getUriForFile(context, AUTHORITY, file);
    }

}
