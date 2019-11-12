package fr.gaulupeau.apps.Poche.data;

import androidx.core.content.ContextCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;

import fr.gaulupeau.apps.Poche.App;

public class StorageHelper {

    public enum CopyFileResult {
        OK, SRC_DOES_NOT_EXIST, CAN_NOT_READ_SRC, CAN_NOT_CREATE_PARENT_DIR,
        DST_IS_DIR, CAN_NOT_CREATE_DST, CAN_NOT_WRITE_DST, UNKNOWN_ERROR
    }

    private static final String TAG = StorageHelper.class.getSimpleName();

    private static String externalStoragePath;

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

    private static File getArticlesDir() {
        File dir = new File(App.getInstance().getFilesDir(), "articles");
        if (!dir.exists()) {
            Log.d(TAG, "getArticlesDir() dir does not exist, creating");
            if (!dir.mkdir()) {
                Log.e(TAG, "getArticlesDir() couldn't create dir");
            }
        }
        return dir;
    }

    private static String getArticleFileExtension() {
        return ".html";
    }

    private static File getArticleFile(String id) {
        return new File(getArticlesDir(), id + getArticleFileExtension());
    }

    public static void storeContentUnsafe(int id, String content) throws IOException {
        storeContentUnsafe(String.valueOf(id), content);
    }

    public static void storeContentUnsafe(String id, String content) throws IOException {
        Log.d(TAG, "storeContentUnsafe(" + id + ") started");

        File file = getArticleFile(id);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
        }
    }

    public static String loadArticleContent(int id) {
        return loadArticleContent(String.valueOf(id));
    }

    public static String loadArticleContent(String id) {
        try {
            return loadArticleContentUnsafe(id);
        } catch (IOException e) {
            Log.w(TAG, "loadArticleContent() loading exception", e);
            return null;
        }
    }

    public static String loadArticleContentUnsafe(String id) throws IOException {
        Log.d(TAG, "loadArticleContentUnsafe(" + id + ") started");

        File file = getArticleFile(id);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();

            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                sb.append(buffer, 0, read);
            }

            return sb.toString();
        }
    }

    public static void deleteArticleContent(int id) {
        deleteArticleContent(String.valueOf(id));
    }

    public static void deleteArticleContent(String id) {
        Log.d(TAG, "deleteArticleContent(" + id + ") started");

        Log.d(TAG, "deleteArticleContent() deletion result: " + getArticleFile(id).delete());
    }

    public static void deleteAllArticleContent() {
        Log.d(TAG, "deleteAllArticleContent() started");

        File[] files = getArticlesDir()
                .listFiles((d, name) -> name.endsWith(getArticleFileExtension()));
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

}
