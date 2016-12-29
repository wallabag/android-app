package fr.gaulupeau.apps.Poche.network;

import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.App;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class ImageCacheUtils {

    private static final String TAG = ImageCacheUtils.class.getSimpleName();

    private static final String IMAGE_CACHE_DIR = "imagecache";
    private static final int MAXIMUM_FILE_EXT_LENGTH = 5; // incl. the dot

    private static final Pattern IMG_URL_PATTERN
            = Pattern.compile("<img[\\w\\W]*?src=\"([^\"]+?)\"[\\w\\W]*?>");

    private static final Pattern[] responsiveParametersPatterns = {
            Pattern.compile("srcset=\".*\""),
            Pattern.compile("sizes=\".*\""),
            Pattern.compile("data-zoom-src=\".*\""),
    };

    private static OkHttpClient okHttpClient;
    private static String externalStoragePath;

    public static String replaceImagesInHtmlContent(String htmlContent, long articleId) {
        String extStorage = getExternalStoragePath();
        if(!ImageCacheUtils.isExternalStorageReadable()) {
            Log.w(TAG, "replaceImagesInHtmlContent: extStorage path is not readable");
            return htmlContent;
        }

        Log.d(TAG, "replaceImagesInHtmlContent: looking up local cached images in folder" +
                " and replacing them in htmlContent");
        List<String> imageURLs = findImageUrlsInHtml(htmlContent);
        Log.d(TAG, "replaceImagesInHtmlContent: imageURLs=" + imageURLs);

        if(imageURLs.isEmpty()) {
            Log.d(TAG, "replaceImagesInHtmlContent: no images found");
            return htmlContent;
        }

        StringBuilder sb = new StringBuilder(htmlContent);

        String articleCachePath = getArticleCachePath(extStorage, articleId);
        for(String imageURL: imageURLs) {
            String localImagePath = getCacheImagePath(articleCachePath, imageURL);
            if(localImagePath == null){
                continue;
            }
            File image = new File(localImagePath);
            if(image.exists() && image.canRead()) {
                Log.d(TAG, "replaceImagesInHtmlContent: replacing image " + imageURL
                        + " -> " + localImagePath);
                replaceAllInStringBuilder(sb, imageURL, localImagePath);
            } else {
                Log.d(TAG, "replaceImagesInHtmlContent: no cached version of " + imageURL
                        + " found at path " + localImagePath);
            }
        }

        if(TextUtils.equals(sb, htmlContent)) {
            Log.d(TAG, "onCreate: htmlContent is still the same, no image paths replaced");
            return htmlContent;
        }

        if(BuildConfig.DEBUG) {
            Log.v(TAG, "onCreate: htmlContent before removing responsive image params:\n" + sb);
        }

        htmlContent = removeResponsiveParameters(sb);

        if(BuildConfig.DEBUG) {
            Log.v(TAG, "onCreate: htmlContent with replaced image paths:\n" + htmlContent);
        }

        return htmlContent;
    }

    private static StringBuilder replaceAllInStringBuilder(
            StringBuilder sb, String src, String replacement) {
        int offset = 0;
        while((offset = sb.indexOf(src, offset)) != -1) {
            sb.replace(offset, offset + src.length(), replacement);
            offset += replacement.length();
        }
        return sb;
    }

    private static String removeResponsiveParameters(CharSequence source) {
        for(Pattern pattern: responsiveParametersPatterns) {
            Matcher m = pattern.matcher(source);

            if(!m.find()) continue; // don't allocate a new string if there are no matches

            source = m.replaceAll("");
        }

        return source.toString();
    }

    public static void cacheImages(long articleId, String articleContent) {
        Log.d(TAG, "cacheImages: articleId=" + articleId + " and is articleContent empty="
                + (articleContent == null || articleContent.isEmpty()));
        String extStoragePath = getExternalStoragePath();
        Log.d(TAG, "cacheImages: extStoragePath=" + extStoragePath);

        if(articleContent == null || extStoragePath == null || extStoragePath.isEmpty()) {
            Log.d(TAG, "cacheImages: returning, because an essential var is null");
            return;
        }
        // TODO: collect them all and process them in a thread with progress showing in status bar as notification
        List<String> imageURLs = findImageUrlsInHtml(articleContent);
        if(imageURLs.isEmpty()) return;

        String articleCachePath = getArticleCachePath(extStoragePath, articleId);
        if(!createArticleCacheDir(articleCachePath)) {
            Log.i(TAG, "cacheImages: couldn't create article cache dir");
            return;
        }

        for(String imageURL: imageURLs) {
            Log.d(TAG, "cacheImages: downloading " + imageURL);
            String destinationPath = getCacheImagePath(articleCachePath, imageURL);
            if(destinationPath == null) {
                Log.d(TAG, "cacheImages: destinationPath is null, skip downloading " + imageURL);
                continue;
            }

            downloadImageToCache(imageURL, destinationPath, articleId);
        }
    }

    public static List<String> findImageUrlsInHtml(String htmlContent) {
        List<String> imageURLs = new ArrayList<>();
        Matcher matcher = IMG_URL_PATTERN.matcher(htmlContent);
        while(matcher.find()) {
            String url = matcher.group(1);
            imageURLs.add(url);
            Log.d(TAG, "findImageUrlsInHtml: found image URL " + url);
        }
        return imageURLs;
    }

    public static String getArticleCachePath(String extStoragePath, long articleId) {
        Log.d(TAG, "getCacheArticlePath: articleId=" + articleId);
        String localArticlePath = extStoragePath + "/" + IMAGE_CACHE_DIR
                + "/" + articleId + "/";
        Log.d(TAG, "getCacheArticlePath: localArticlePath=" + localArticlePath);
        return localArticlePath;
    }

    public static String getCacheImagePath(String articleCachePath, String imageURL) {
        String localImageName = getCacheImageName(imageURL);
        Log.d(TAG, "getCacheImagePath: localImageName=" + localImageName + " for URL " + imageURL);
        if(localImageName == null) return null;
        String localImagePath = articleCachePath + localImageName;
        Log.d(TAG, "getCacheImagePath: localImagePath=" + localImagePath);
        return localImagePath;
    }

    public static String getCacheImageName(String imageURL) {
        Log.d(TAG, "getCacheImageName: imageURL=" + imageURL);
        int fileExt = imageURL.lastIndexOf(".");

        if(fileExt < 0) {
            Log.d(TAG, "getCacheImageName: no valid file extension found, returning null");
            return null;
        }
        String fileExtName = imageURL.substring(fileExt);
        if(fileExtName.contains("/") || fileExtName.length() > MAXIMUM_FILE_EXT_LENGTH) {
            Log.d(TAG, "getCacheImageName: suspicious file extension in image URL " + imageURL
                    + ", returning null");
            return null;
        }

        String localImageName = ImageCacheUtils.md5(imageURL) + fileExtName;
        Log.d(TAG, "getCacheImageName: localImageName=" + localImageName + " for URL " + imageURL);
        return localImageName;
    }

    public static boolean createArticleCacheDir(String articleCacheDir) {
        Log.d(TAG, "createArticleCacheDir: articleImageCacheDirName=" + articleCacheDir);
        File f = new File(articleCacheDir);
        if(f.exists() && f.isDirectory()) {
            Log.d(TAG, "createArticleCacheDir: image cache dir already exists");
            return true;
        } else {
            boolean isDirCreated = new File(articleCacheDir).mkdirs();
            Log.d(TAG, "createArticleCacheDir: isDirCreated=" + isDirCreated);
            return isDirCreated;
        }
    }

    public static void downloadImageToCache(String imageURL, String destination, long articleId) {
        Log.d(TAG, "downloadImageToCache: imageURL=" + imageURL + " destination=" + destination);

        File dest = new File(destination);
        if(dest.exists()) {
            Log.d(TAG, "downloadImageToCache: file already exists, skipping");
            return;
        }

        if(okHttpClient == null) {
            okHttpClient = WallabagConnection.createClient(false);
        }

        Request request = new Request.Builder().url(imageURL).build();
        Response response;
        try {
            response = okHttpClient.newCall(request).execute();
        } catch(IOException e) {
            Log.d(TAG, "IOException while requesting imageURL=" + imageURL
                    + " in articleID=" + articleId, e);
            return;
        }

        File downloadFile = new File(destination);

        BufferedSource source = response.body().source();
        BufferedSink sink = null;
        try {
            sink = Okio.buffer(Okio.sink(downloadFile));
            sink.writeAll(source);
        } catch(FileNotFoundException e) {
            Log.d(TAG, "downloadImageToCache: FileNotFoundException", e);
            return;
        } catch(IOException e) {
            Log.d(TAG, "downloadImageToCache: IOException while downloading imageURL=" + imageURL
                    + " in articleID=" + articleId, e);
        } finally {
            if(sink != null) {
                try {
                    sink.close();
                } catch(IOException e) {
                    Log.w(TAG, "downloadImageToCache: IOException while closing sink", e);
                }
            }
            if(source != null) {
                try {
                    source.close();
                } catch(IOException ignored) {}
            }
        }

        Log.d(TAG, "downloadImageToCache: function finished for imageURL=" + imageURL
                + " destination=" + destination);
    }

    public static String getExternalStoragePath() {
        if(externalStoragePath == null) {
            String returnPath = null;
            File[] extStorage = ContextCompat.getExternalFilesDirs(App.getInstance(), null);
            if(extStorage == null) {
                Log.w(TAG, "onCreate: getExternalFilesDirs() returned null or is not readable");
            } else {
                // TODO: better SD Card detection
                for(File extStorageDir: extStorage) {
                    Log.d(TAG, "getExternalStoragePath: extStorageDir.getPath()="
                            + extStorageDir.getPath());
                    returnPath = extStorageDir.getPath();
                }
            }
            Log.d(TAG, "getExternalStoragePath: returnPath=" + returnPath);
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

    public static String md5(String s) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch(NoSuchAlgorithmException e) {
            Log.d(TAG, "md5: NoSuchAlgorithmException", e);
            Log.w(TAG, "md5: failed to calc md5 for " + s + ", returning empty string");
            return "";
        }

        digest.update(s.getBytes());
        return new BigInteger(1, digest.digest()).toString(16);
    }

}
