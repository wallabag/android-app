package fr.gaulupeau.apps.Poche.network;

import android.os.Environment;
import android.util.Log;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by strubbl on 04.11.16.
 */


public class ImageCacheUtils {
    private static final String TAG = ImageCacheUtils.class.getSimpleName();
    private static final String IMAGE_CACHE_DIR = "imagecache";

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
        return htmlContent.replace(imageURL, imageCachePath);
    }

    public static String getCacheImagePath(String extStoragePath, Long articleId, String imageURL) {
        String localImageName = ImageCacheUtils.md5(imageURL) + imageURL.substring(imageURL.lastIndexOf("."));
        Log.d(TAG, "getCacheImagePath: localImageName=" + localImageName + " for URL " + imageURL);
        String localImagePath = extStoragePath + "/" + IMAGE_CACHE_DIR + "/" + articleId + localImageName;
        Log.d(TAG, "getCacheImagePath: localImagePath=" + localImagePath);
        return localImagePath;
    }

    /* Checks if external storage is available to at least read
    *  copied from https://developer.android.com/training/basics/data-storage/files.html#WriteExternalStorage */
    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available for read and write
    *  copied from https://developer.android.com/training/basics/data-storage/files.html#WriteExternalStorage */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
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
