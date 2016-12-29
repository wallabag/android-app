package fr.gaulupeau.apps.Poche.service;

import android.content.Intent;
import android.os.Process;
import android.util.Log;
import android.util.Pair;

import org.greenrobot.greendao.DaoException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.events.ActionResultEvent;
import fr.gaulupeau.apps.Poche.events.DownloadFileFinishedEvent;
import fr.gaulupeau.apps.Poche.events.DownloadFileStartedEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesProgressEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesStartedEvent;
import fr.gaulupeau.apps.Poche.network.ImageCacheUtils;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class SecondaryService extends IntentServiceBase {

    private static final String TAG = SecondaryService.class.getSimpleName();

    public SecondaryService() {
        super(SecondaryService.class.getSimpleName());
        setIntentRedelivery(true);

        Log.d(TAG, "SecondaryService() created");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent() started");

        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        ActionRequest actionRequest = ActionRequest.fromIntent(intent);
        ActionResult result = null;

        switch(actionRequest.getAction()) {
            case DOWNLOAD_AS_FILE: {
                result = downloadAsFile(actionRequest);
                break;
            }

            case FETCH_IMAGES: {
                FetchImagesStartedEvent startEvent = new FetchImagesStartedEvent(actionRequest);
                postStickyEvent(startEvent);
                try {
                    fetchImages(actionRequest);
                } finally {
                    removeStickyEvent(startEvent);
                    postEvent(new FetchImagesFinishedEvent(actionRequest));
                }
                break;
            }

            default:
                Log.w(TAG, "Unknown action requested: " + actionRequest.getAction());
                break;
        }

        if(result != null) {
            postEvent(new ActionResultEvent(actionRequest, result));
        }

        Log.d(TAG, "onHandleIntent() finished");
    }

    private ActionResult downloadAsFile(ActionRequest actionRequest) {
        Article article = null;
        try {
            article = getDaoSession().getArticleDao().queryBuilder()
                    .where(ArticleDao.Properties.ArticleId.eq(actionRequest.getArticleID()))
                    .build().unique();
        } catch(DaoException e) {
            Log.w(TAG, "onHandleIntent()", e);
        }

        if(article == null) {
            return new ActionResult(ActionResult.ErrorType.UNKNOWN, "Couldn't find the article"); // TODO: string
        }

        ActionResult result = null;

        DownloadFileStartedEvent startEvent = new DownloadFileStartedEvent(actionRequest, article);
        postStickyEvent(startEvent);

        Pair<ActionResult, File> downloadResult = null;
        try {
            downloadResult = downloadAsFile(actionRequest, article);
            result = downloadResult.first;
        } finally {
            removeStickyEvent(startEvent);

            if(result == null) result = new ActionResult(ActionResult.ErrorType.UNKNOWN);

            postEvent(new DownloadFileFinishedEvent(actionRequest, result, article,
                    downloadResult != null ? downloadResult.second : null));
        }

        return result;
    }

    private Pair<ActionResult, File> downloadAsFile(ActionRequest actionRequest, Article article) {
        Log.d(TAG, String.format("downloadAsFile() started; action: %s, articleID: %s",
                actionRequest.getAction(), actionRequest.getArticleID()));

        int articleID = actionRequest.getArticleID();

        if(!WallabagConnection.isNetworkAvailable()) {
            Log.i(TAG, "downloadAsFile() not on-line; exiting");
            return new Pair<>(new ActionResult(ActionResult.ErrorType.NO_NETWORK), null);
        }

        WallabagWebService service = getWallabagWebService();

        File resultFile = null;
        try {
            WallabagWebService.ConnectionTestResult connectionTestResult
                    = service.testConnection();
            Log.d(TAG, "downloadAsFile() connectionTestResult: " + connectionTestResult);
            if(connectionTestResult != WallabagWebService.ConnectionTestResult.OK) {
                Log.w(TAG, "downloadAsFile() testing connection failed with value "
                        + connectionTestResult);

                ActionResult.ErrorType errorType;
                switch(connectionTestResult) {
                    case INCORRECT_URL:
                    case UNSUPPORTED_SERVER_VERSION:
                    case WALLABAG_NOT_FOUND:
                        errorType = ActionResult.ErrorType.INCORRECT_CONFIGURATION;
                        break;

                    case AUTH_PROBLEM:
                    case HTTP_AUTH:
                    case INCORRECT_CREDENTIALS:
                        errorType = ActionResult.ErrorType.INCORRECT_CREDENTIALS;
                        break;

                    default:
                        errorType = ActionResult.ErrorType.UNKNOWN;
                        break;
                }

                return new Pair<>(new ActionResult(errorType), null);
            }

            String articleTitle = article.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_");
            String exportType = actionRequest.getDownloadFormat().asString();
            String exportUrl = getSettings().getUrl() + "/export/" + articleID + "." + exportType;
            Log.d(TAG, "downloadAsFile() exportUrl=" + exportUrl);
            String exportFileName = articleTitle + "." + exportType;

            Request request = new Request.Builder()
                    .url(exportUrl)
                    .build();

            Response response = service.getClient().newCall(request).execute();

            if(!response.isSuccessful()) {
                return new Pair<>(new ActionResult(ActionResult.ErrorType.UNKNOWN,
                        "Response code: " + response.code()
                                + ", response message: " + response.message()), null);
            }

            // do we need it?
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Headers responseHeaders = response.headers();
                for (int i = 0, size = responseHeaders.size(); i < size; i++) {
                    Log.d(TAG, responseHeaders.name(i) + ": " + responseHeaders.value(i));
                }
            }

            File exportDir = getExternalFilesDir(null); // TODO: check
            File file = new File(exportDir, exportFileName);
            Log.d(TAG, "Saving file " + file.getAbsolutePath());

            BufferedSink sink = null;
            try {
                sink = Okio.buffer(Okio.sink(file));
                sink.writeAll(response.body().source());
            } finally {
                if(sink != null) {
                    try {
                        sink.close();
                    } catch(IOException ignored) {}
                }
                response.body().close();
            }

            resultFile = file;
        } catch(IncorrectConfigurationException | IOException e) {
            ActionResult r = processException(e, "downloadAsFile()");
            if(!r.isSuccess()) return new Pair<>(r, null);
        }

        return new Pair<>(new ActionResult(), resultFile);
    }

    private void fetchImages(ActionRequest actionRequest) {
        Log.d(TAG, "fetchImages() started");

        if(!ImageCacheUtils.isExternalStorageWritable()) {
            Log.w(TAG, "fetchImages() external storage is not writable");
            return;
        }

        ArticleDao articleDao = getDaoSession().getArticleDao();
        List<Article> articleList = articleDao.queryBuilder()
                .where(ArticleDao.Properties.ImagesDownloaded.eq(false))
                .orderAsc(ArticleDao.Properties.ArticleId).list(); // TODO: lazyList

        List<Integer> updatedArticles = new ArrayList<>(articleList.size());

        Log.d(TAG, "fetchImages() articleList.size()=" + articleList.size());
        int i = 0, totalNumber = articleList.size();
        for(Article article: articleList) {
            Log.d(TAG, "fetchImages() processing " + i++ + ". articleID=" + article.getArticleId());
            postEvent(new FetchImagesProgressEvent(actionRequest, i, totalNumber));

            ImageCacheUtils.cacheImages(article.getArticleId().longValue(), article.getContent());

            updatedArticles.add(article.getArticleId());

            Log.d(TAG, "fetchImages() processing article " + article.getArticleId() + " finished");
        }

        // TODO: update in bulk
        for(Integer articleID: updatedArticles) {
            try {
                Article article = articleDao.queryBuilder()
                        .where(ArticleDao.Properties.ArticleId.eq(articleID))
                        .unique();
                article.setImagesDownloaded(true);
                articleDao.update(article);
            } catch(DaoException e) {
                Log.e(TAG, "fetchImages() Exception while updating articles", e);
            }
        }

        Log.d(TAG, "fetchImages() finished");
    }

}
