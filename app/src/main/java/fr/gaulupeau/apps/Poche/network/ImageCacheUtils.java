package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.gaulupeau.apps.Poche.App;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

/**
 * Created by strubbl on 04.11.16.
 */


public class ImageCacheUtils {
    private static final String TAG = ImageCacheUtils.class.getSimpleName();
    private static final String IMAGE_CACHE_DIR = "imagecache";
    private static final int MAXIMUM_FILE_EXT_LENGTH = 5; // incl. the dot

    private static OkHttpClient okHttpClient = null;
    private static String externalStoragePath = null;

    public static void cacheImages(Long articleId, String articleContent) {
        Log.d(TAG, "cacheImages: articleId=" + articleId + " and is articleContent empty=" + articleContent.isEmpty());
        String extStoragePath = getExternalStoragePath();
        Log.d(TAG, "cacheImages: extStoragePath=" + extStoragePath);

        if(articleId == null || articleContent == null || extStoragePath == null || extStoragePath.isEmpty()) {
            Log.d(TAG, "cacheImages: returning, because an essential var is null, see previous debug messages");
            // TODO: fresh articles always have null as id, therefore image caching currently only works on second full update
            return;
        }
        // TODO: collect them all and process them in a thread with progress showing in status bar as notification
        List<String> imageURLs = findImageUrlsInHtml(articleContent);
        for(int i = 0; i < imageURLs.size(); i++) {
            String imageURL = imageURLs.get(i);
            Log.d(TAG, "cacheImages: downloading " + imageURL);
            String destinationPath = getCacheImagePath(extStoragePath, articleId, imageURL);
            if(destinationPath == null) {
                Log.d(TAG, "cacheImages: destinationPath is null, skip downloading " + imageURL);
                continue;
            }
            else {
                downloadImageToCache(imageURL, destinationPath, articleId);
            }
        }
    }

    public static List<String> findImageUrlsInHtml(String htmlContent) {
        List<String> imageURLs = new ArrayList<>();
        Pattern pattern = Pattern.compile("<img[\\w\\W]*?src=\"([^\"]+?)\"[\\w\\W]*?>");
        Matcher matcher = pattern.matcher(htmlContent);
        while (matcher.find()) {
            imageURLs.add(matcher.group(1)); //group(0) is the whole tag, 1 only the image URL
            Log.d(TAG, "findImageUrlsInHtml: found image URL " + matcher.group(1));
        }
        return imageURLs;
    }

    public static String replaceImageWithCachedVersion(String htmlContent, String imageURL, String imageCachePath) {
        Log.d(TAG, "replaceImageWithCachedVersion: trying to replace " + imageURL + " --> " + imageCachePath);
        return htmlContent.replaceAll(imageURL, imageCachePath);
    }

    public static String getCacheImagePath(String extStoragePath, Long articleId, String imageURL) {
        Log.d(TAG, "getCacheImagePath: articleId=" + articleId);
        int fileExt = imageURL.lastIndexOf(".");

        if (fileExt < 0) {
            Log.d(TAG, "getCacheImagePath: no valid file extension found, returning null");
            return null;
        }
        String fileExtName = imageURL.substring(fileExt);
        if (fileExtName.contains("/") || fileExtName.length() > MAXIMUM_FILE_EXT_LENGTH) {
            Log.d(TAG, "getCacheImagePath: suspicious file extension in image URL " + imageURL + ", returning null");
            return null;
        }

        String localImageName = ImageCacheUtils.md5(imageURL) + fileExtName;
        Log.d(TAG, "getCacheImagePath: localImageName=" + localImageName + " for URL " + imageURL);
        String localImagePath = extStoragePath + "/" + IMAGE_CACHE_DIR + "/" + articleId + "/" + localImageName;
        Log.d(TAG, "getCacheImagePath: localImagePath=" + localImagePath);
        return localImagePath;
    }

    public static boolean doesImageCacheDirExistElseCreate(Long articleId) {
        String articleImageCacheDirName = getExternalStoragePath() + "/" + IMAGE_CACHE_DIR + "/" + articleId.toString();
        Log.d(TAG, "doesImageCacheDirExistElseCreate: articleImageCacheDirName=" + articleImageCacheDirName);
        File f = new File(articleImageCacheDirName);
        if (f.exists() && f.isDirectory()) {
            Log.d(TAG, "doesImageCacheDirExistElseCreate: image cache dir for articleId=" + articleId + " already exists");
            return true;
        } else {
            boolean isDirCreated = new File(articleImageCacheDirName).mkdirs();
            Log.d(TAG, "doesImageCacheDirExistElseCreate: isDirCreated=" + isDirCreated);
            return isDirCreated;
        }
    }

    public static void downloadImageToCache(String imageURL, String destination, Long articleId) {
        Log.d(TAG, "downloadImageToCache: imageURL=" + imageURL + " destination=" + destination);

        File dest = new File(destination);
        if (dest.exists()) {
            Log.d(TAG, "downloadImageToCache: file already exists, skipping");
            return;
        }

        doesImageCacheDirExistElseCreate(articleId);

        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient();
        }
        Request request = new Request.Builder().url(imageURL).build();
        Response response = null;
        try {
            response = okHttpClient.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File downloadFile = new File(destination);
        BufferedSink sink = null;
        try {
            sink = Okio.buffer(Okio.sink(downloadFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        try {
            if (response == null) {
                sink.close();
                return;
            } else {
                sink.writeAll(response.body().source());
                sink.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "downloadImageToCache: function finished for imageURL=" + imageURL + " destination=" + destination);
    }

    public static String getExternalStoragePath() {
        if (externalStoragePath == null) {
            String returnPath = null;
            File[] extStorage;
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                extStorage = App.getInstance().getExternalFilesDirs(null);
            } else {
                extStorage = new File[] {App.getInstance().getExternalFilesDir(null)};
            }
            if (extStorage == null) {
                Log.w(TAG, "onCreate: getExternalFilesDirs() returned null or is not readable");
            } else {
                for (int i = 0; i < extStorage.length; i++) {
                    Log.d(TAG, "getExternalStoragePath: extStorage[i].getPath()=" + extStorage[i].getPath());
                    returnPath = extStorage[i].getPath(); // TODO uses the last path in the array, which is USUALLY the sd card
                }
            }
            Log.d(TAG, "getExternalStoragePath: returnPath=" + returnPath);
            externalStoragePath = returnPath;
            return returnPath;
        } else {
            return externalStoragePath;
        }
    }

    public static boolean isExternalStorageReadable() {
        if(externalStoragePath==null) {
            getExternalStoragePath();
        }
        // now, if it is still null, return false
        if(externalStoragePath == null) return false;

        File f = new File(externalStoragePath);
        return f != null && f.exists() && f.canRead();
    }

    public static boolean isExternalStorageWritable() {
        if(externalStoragePath==null) {
            getExternalStoragePath();
        }
        // now, if it is still null, return false
        if(externalStoragePath == null) return false;

        File f = new File(externalStoragePath);
        return f != null && f.exists() && f.canWrite();
    }

    /**
     * http://stackoverflow.com/a/6847711/709697
     */
    public static String md5(String s) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes(Charset.forName("US-ASCII")), 0, s.length());
            byte[] magnitude = digest.digest();
            BigInteger bi = new BigInteger(1, magnitude);
            String hash = String.format("%0" + (magnitude.length << 1) + "x", bi);
            return hash;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        Log.w(TAG, "md5: failed to calc md5 for " + s + ", returning empty string");
        return "";
    }
}
