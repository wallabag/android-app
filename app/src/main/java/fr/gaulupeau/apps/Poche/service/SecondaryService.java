package fr.gaulupeau.apps.Poche.service;

import android.content.Intent;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import wallabag.apiwrapper.CompatibilityHelper;
import wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;

import org.greenrobot.greendao.DaoException;
import org.greenrobot.greendao.query.QueryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.StorageHelper;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.events.ActionResultEvent;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.DownloadFileFinishedEvent;
import fr.gaulupeau.apps.Poche.events.DownloadFileStartedEvent;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesProgressEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesStartedEvent;
import fr.gaulupeau.apps.Poche.network.ImageCacheUtils;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
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

        postEvent(new ActionResultEvent(actionRequest, result));

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

        BufferedSource source = null;
        File resultFile = null;
        try {
            if(CompatibilityHelper.isExportArticleSupported(
                    getWallabagServiceWrapper().getWallabagService())) {

                // TODO: fix: not synchronized
                source = getWallabagServiceWrapper().getWallabagService()
                        .exportArticle(articleID, actionRequest.getDownloadFormat()).source();
            } else {
                Log.d(TAG, "downloadAsFile() downloading via API is not supported");
            }

            String fileExt = actionRequest.getDownloadFormat().toString().toLowerCase(Locale.US);

            // TODO: remove fallback
            if(source == null) {
                Log.i(TAG, "Failed to get article via API, falling back to plain URL");

                WallabagWebService service = getWallabagWebService();
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

                String exportUrl = getSettings().getUrl() + "/export/" + articleID + "." + fileExt;
                Log.d(TAG, "downloadAsFile() exportUrl=" + exportUrl);
                Request request = new Request.Builder()
                        .url(exportUrl)
                        .build();

                Response response = service.getClient().newCall(request).execute();

                if(!response.isSuccessful()) {
                    return new Pair<>(new ActionResult(ActionResult.ErrorType.UNKNOWN,
                            "Response code: " + response.code()
                                    + ", response message: " + response.message()), null);
                }

                source = response.body().source();
            }

            String articleTitle = article.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_");
            String exportFileName = articleTitle + "." + fileExt;

            File exportDir = getExternalFilesDir(null); // TODO: check
            File file = new File(exportDir, exportFileName);
            Log.d(TAG, "Saving file " + file.getAbsolutePath());

            BufferedSink sink = null;
            try {
                sink = Okio.buffer(Okio.sink(file));
                sink.writeAll(source);
            } finally {
                if(sink != null) {
                    try {
                        sink.close();
                    } catch(IOException e) {
                        Log.w(TAG, "downloadAsFile() IOException while closing sink", e);
                    }
                }
            }

            resultFile = file;
        } catch(IncorrectConfigurationException | UnsuccessfulResponseException | IOException e) {
            ActionResult r = processException(e, "downloadAsFile()");
            if(!r.isSuccess()) return new Pair<>(r, null);
        } finally {
            if(source != null) {
                try {
                    source.close();
                } catch(IOException ignored) {}
            }
        }

        return new Pair<>(new ActionResult(), resultFile);
    }

    private void fetchImages(ActionRequest actionRequest) {
        Log.d(TAG, "fetchImages() started");

        if(!StorageHelper.isExternalStorageWritable()) {
            Log.w(TAG, "fetchImages() external storage is not writable");
            return;
        }

        ArticleDao articleDao = getDaoSession().getArticleDao();

        QueryBuilder<Article> queryBuilder = articleDao.queryBuilder()
                .where(ArticleDao.Properties.ImagesDownloaded.eq(false))
                .orderAsc(ArticleDao.Properties.ArticleId);

        int totalNumber = (int)queryBuilder.count();
        Log.d(TAG, "fetchImages() total number: " + totalNumber);

        if(totalNumber == 0) {
            Log.d(TAG, "fetchImages() nothing to do");
            return;
        }

        ArticlesChangedEvent event = new ArticlesChangedEvent();

        List<Integer> processedArticles = new ArrayList<>(totalNumber);
        Set<Integer> changedArticles = new HashSet<>(totalNumber);

        int dbQuerySize = 50;

        queryBuilder.limit(dbQuerySize);

        int offset = 0;

        while(true) {
            Log.d(TAG, "fetchImages() looping; offset: " + offset);

            List<Article> articleList = queryBuilder.list();

            if(articleList.isEmpty()) {
                Log.d(TAG, "fetchImages() no more articles");
                break;
            }

            int i = 0;
            for(Article article: articleList) {
                int index = offset + i++;
                Log.d(TAG, "fetchImages() processing " + index
                        + ". articleID: " + article.getArticleId());
                postEvent(new FetchImagesProgressEvent(actionRequest, index, totalNumber));

                String content = article.getContent();

                // append preview picture URL to content to fetch it too
                // should probably be handled separately
                if(!TextUtils.isEmpty(article.getPreviewPictureURL())) {
                    content = "<img src=\"" + article.getPreviewPictureURL() + "\"/>" + content;
                }

                if(ImageCacheUtils.cacheImages(article.getArticleId().longValue(), content)) {
                    changedArticles.add(article.getArticleId());
                }

                processedArticles.add(article.getArticleId());

                Log.d(TAG, "fetchImages() processing article " + article.getArticleId() + " finished");
            }

            offset += dbQuerySize;
            queryBuilder.offset(offset);
        }

        for(Integer articleID: processedArticles) {
            try {
                Article article = articleDao.queryBuilder()
                        .where(ArticleDao.Properties.ArticleId.eq(articleID))
                        .unique();

                if(article != null) {
                    article.setImagesDownloaded(true);
                    articleDao.update(article);

                    if(changedArticles.contains(articleID)) {
                        // maybe add another change type for unsuccessful articles?
                        event.addArticleChangeWithoutObject(article,
                                FeedsChangedEvent.ChangeType.FETCHED_IMAGES_CHANGED);
                    }
                }
            } catch(DaoException e) {
                Log.e(TAG, "fetchImages() Exception while updating articles", e);
            }
        }

        if(event.isAnythingChanged()) {
            postEvent(event);
        }

        Log.d(TAG, "fetchImages() finished");
    }

}
