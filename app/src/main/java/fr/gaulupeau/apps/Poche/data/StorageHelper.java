package fr.gaulupeau.apps.Poche.data;

import android.content.res.Resources;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import fr.gaulupeau.apps.Poche.App;

public class StorageHelper {

    public enum CopyFileResult {
        OK, SRC_DOES_NOT_EXIST, CAN_NOT_READ_SRC, CAN_NOT_CREATE_PARENT_DIR,
        DST_IS_DIR, CAN_NOT_CREATE_DST, CAN_NOT_WRITE_DST, UNKNOWN_ERROR
    }

    private static final String TAG = StorageHelper.class.getSimpleName();

    private static String externalStoragePath;

    public static String readRawString(int id) throws IOException {
        Resources resources = App.getInstance().getResources();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resources.openRawResource(id)))) {

            StringBuilder sb = new StringBuilder();
            String s;
            while ((s = reader.readLine()) != null) {
                sb.append(s).append('\n');
            }

            return sb.toString();
        }
    }

    public static String getExternalStoragePath() {
        if(externalStoragePath == null) {
            String returnPath = null;
            File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(App.getInstance(), null);
            if(externalFilesDirs != null) {
                // TODO: better SD Card detection
                for(File extStorageDir: externalFilesDirs) {
                    if(extStorageDir == null) {
                        Log.w(TAG, "getExternalStoragePath() extStorageDir is null");
                        continue;
                    }

                    Log.d(TAG, "getExternalStoragePath() extStorageDir.getPath(): "
                            + extStorageDir.getPath());
                    returnPath = extStorageDir.getPath();
                }
            } else {
                Log.w(TAG, "getExternalStoragePath() getExternalFilesDirs() returned null");
            }

            Log.d(TAG, "getExternalStoragePath() returnPath: " + returnPath);
            return externalStoragePath = returnPath;
        }

        return externalStoragePath;
    }

    public static boolean isExternalStorageReadable() {
        String externalStoragePath = getExternalStoragePath();
        if(externalStoragePath == null) return false;

        File f = new File(externalStoragePath);
        return f.exists() && f.canRead();
    }

    public static boolean isExternalStorageWritable() {
        String externalStoragePath = getExternalStoragePath();
        if(externalStoragePath == null) return false;

        File f = new File(externalStoragePath);
        return f.exists() && f.canWrite();
    }

    public static CopyFileResult copyFile(String srcPath, String dstPath) {
        Log.d(TAG, String.format("copyFile(%s, %s)", srcPath, dstPath));

        File srcFile = new File(srcPath);
        if(!srcFile.exists()) return CopyFileResult.CAN_NOT_READ_SRC;
        if(!srcFile.canRead()) return CopyFileResult.CAN_NOT_READ_SRC;

        File dstFile = new File(dstPath);

        if(!dstFile.getParentFile().exists()) {
            if(!dstFile.getParentFile().mkdirs()) return CopyFileResult.CAN_NOT_CREATE_PARENT_DIR;
        }

        FileChannel src = null;
        FileChannel dst = null;
        try {
            if(dstFile.exists()) {
                if(dstFile.isDirectory()) return CopyFileResult.DST_IS_DIR;
            } else {
                if(!dstFile.createNewFile()) return CopyFileResult.CAN_NOT_CREATE_DST;
            }
            if(!dstFile.canWrite()) return CopyFileResult.CAN_NOT_WRITE_DST;

            src = new FileInputStream(srcFile).getChannel();
            dst = new FileOutputStream(dstFile).getChannel();
            dst.transferFrom(src, 0, src.size());
        } catch(IOException e) {
            Log.e(TAG, "copyFile() IOException", e);
            return CopyFileResult.UNKNOWN_ERROR;
        } finally {
            if(src != null) {
                try {
                    src.close();
                } catch(IOException ignored) {}
            }
            if(dst != null) {
                try {
                    dst.close();
                } catch(IOException ignored) {}
            }
        }

        return CopyFileResult.OK;
    }

    public static boolean deleteFile(String path) {
        return new File(path).delete();
    }

    public static File dumpQueueData(String data) throws IOException {
        if (!isExternalStorageWritable()) {
            throw new IllegalStateException("External storage is not writable!");
        }

        String path = getExternalStoragePath() + "/"
                + "Local_changes_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".txt";
        File file = new File(path);

        //noinspection ResultOfMethodCallIgnored: should exist either way
        file.createNewFile();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(data);
        }

        return file;
    }

}
