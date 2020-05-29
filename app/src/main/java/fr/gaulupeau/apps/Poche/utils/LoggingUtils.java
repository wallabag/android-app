package fr.gaulupeau.apps.Poche.utils;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.StorageHelper;

public class LoggingUtils {

    private static final String TAG = LoggingUtils.class.getSimpleName();

    public static void saveLogcatToFile(Activity context) {
        String filePath = null;
        try {
            filePath = saveLogcatToFileInternal();

            Toast.makeText(context, context.getString(R.string.misc_logging_logcatToFile_result_saved,
                    filePath), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.w(TAG, "Error saving logcat output to file", e);
            Toast.makeText(context, context.getString(R.string.misc_logging_logcatToFile_result_error,
                    e.toString()), Toast.LENGTH_LONG).show();
        }

        if (filePath != null) {
            WallabagFileProvider.shareFile(context, new File(filePath));
        }
    }

    private static String saveLogcatToFileInternal() throws IOException {
        if (!StorageHelper.isExternalStorageWritable()) {
            throw new IllegalStateException("External storage is not writable!");
        }

        String path = StorageHelper.getExternalStoragePath() + "/"
                + "logcat_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".txt";

        Runtime.getRuntime().exec("logcat -d -f " + path);

        return path;
    }

}
