package fr.gaulupeau.apps.Poche.data;

import android.content.res.Resources;
import android.text.TextUtils;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fr.gaulupeau.apps.Poche.App;

public class StorageHelper {

    public enum CopyFileResult {
        OK, SRC_DOES_NOT_EXIST, CAN_NOT_READ_SRC, CAN_NOT_CREATE_PARENT_DIR,
        DST_IS_DIR, CAN_NOT_CREATE_DST, CAN_NOT_WRITE_DST, UNKNOWN_ERROR
    }

    private static final String TAG = StorageHelper.class.getSimpleName();

    public static String readRawString(int id) {
        try {
            return readRawStringUnsafe(id);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't load raw resource", e);
        }
    }

    public static String readRawStringUnsafe(int id) throws IOException {
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
        String storagePathSetting = App.getSettings().getDbPath();

        if (!TextUtils.isEmpty(storagePathSetting)) {
            return storagePathSetting;
        }

        List<String> externalStoragePaths = getExternalStoragePaths();
        return !externalStoragePaths.isEmpty() ? externalStoragePaths.get(0) : null;
    }

    private static List<String> getExternalStoragePaths() {
        List<String> usableExternalFilesDirs = new ArrayList<>();

        for (File extStorageDir : ContextCompat.getExternalFilesDirs(App.getInstance(), null)) {
            if (extStorageDir == null) {
                Log.w(TAG, "getExternalStoragePaths() extStorageDir is null");
                continue;
            }

            usableExternalFilesDirs.add(extStorageDir.getPath());
            Log.d(TAG, "getExternalStoragePaths() extStorageDir.getPath(): "
                    + extStorageDir.getPath());
        }

        return usableExternalFilesDirs;
    }

    public static List<String> getWritableExternalStoragePaths() {
        List<String> paths = new ArrayList<>();
        for (String path : getExternalStoragePaths()) {
            if (isPathWritable(path)) {
                paths.add(path);
            }
        }
        return paths;
    }

    public static boolean isExternalStorageReadable() {
        String storagePath = getExternalStoragePath();
        if (storagePath == null) return false;

        File f = new File(storagePath);
        return f.exists() && f.canRead();
    }

    public static boolean isExternalStorageWritable() {
        return isPathWritable(getExternalStoragePath());
    }

    private static boolean isPathWritable(String path) {
        if (path == null) return false;

        File f = new File(path);
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
        String externalStoragePath = getExternalStoragePath();
        if (!isPathWritable(externalStoragePath)) {
            throw new IllegalStateException("External storage is not writable!");
        }

        String path = externalStoragePath + "/"
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
