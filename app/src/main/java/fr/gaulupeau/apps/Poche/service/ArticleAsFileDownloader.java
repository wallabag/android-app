package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.greenrobot.greendao.DaoException;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.StorageHelper;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.events.DownloadFileFinishedEvent;
import fr.gaulupeau.apps.Poche.events.DownloadFileStartedEvent;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import wallabag.apiwrapper.CompatibilityHelper;
import wallabag.apiwrapper.NotFoundPolicy;
import wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class ArticleAsFileDownloader extends BaseWorker {

    private static final String TAG = ArticleAsFileDownloader.class.getSimpleName();

    public ArticleAsFileDownloader(Context context) {
        super(context);
    }

    public ActionResult download(ActionRequest actionRequest) {
        return downloadAsFile(actionRequest);
    }

    private ActionResult downloadAsFile(ActionRequest actionRequest) {
        Article article = null;
        try {
            article = getDaoSession().getArticleDao().queryBuilder()
                    .where(ArticleDao.Properties.ArticleId.eq(actionRequest.getArticleID()))
                    .build().unique();
        } catch (DaoException e) {
            Log.w(TAG, "onHandleIntent()", e);
        }

        if (article == null) {
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

            if (result == null) result = new ActionResult(ActionResult.ErrorType.UNKNOWN);

            postEvent(new DownloadFileFinishedEvent(actionRequest, result, article,
                    downloadResult != null ? downloadResult.second : null));
        }

        return result;
    }

    private Pair<ActionResult, File> downloadAsFile(ActionRequest actionRequest, Article article) {
        Log.d(TAG, String.format("downloadAsFile() started; action: %s, articleID: %s",
                actionRequest.getAction(), actionRequest.getArticleID()));

        int articleID = actionRequest.getArticleID();

        if (!WallabagConnection.isNetworkAvailable()) {
            Log.i(TAG, "downloadAsFile() not on-line; exiting");
            return new Pair<>(new ActionResult(ActionResult.ErrorType.NO_NETWORK), null);
        }

        BufferedSource source = null;
        File resultFile = null;
        try {
            if (CompatibilityHelper.isExportArticleSupported(getWallabagService())) {
                source = getWallabagService().exportArticle(
                        articleID, actionRequest.getDownloadFormat(), NotFoundPolicy.THROW)
                        .source();
            } else {
                Log.d(TAG, "downloadAsFile() downloading via API is not supported");
            }

            String fileExt = actionRequest.getDownloadFormat().toString().toLowerCase(Locale.US);

            // TODO: remove fallback
            if (source == null) {
                Log.i(TAG, "Failed to get article via API, falling back to plain URL");

                WallabagWebService service = getWallabagWebService();
                WallabagWebService.ConnectionTestResult connectionTestResult
                        = service.testConnection();
                Log.d(TAG, "downloadAsFile() connectionTestResult: " + connectionTestResult);
                if (connectionTestResult != WallabagWebService.ConnectionTestResult.OK) {
                    Log.w(TAG, "downloadAsFile() testing connection failed with value "
                            + connectionTestResult);

                    ActionResult.ErrorType errorType;
                    switch (connectionTestResult) {
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

                if (!response.isSuccessful()) {
                    return new Pair<>(new ActionResult(ActionResult.ErrorType.UNKNOWN,
                            "Response code: " + response.code()
                                    + ", response message: " + response.message()), null);
                }

                source = response.body().source();
            }

            String articleTitle = article.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_");
            String exportFileName = articleTitle + "." + fileExt;

            String exportDir = StorageHelper.getExternalStoragePath();
            File file = new File(exportDir, exportFileName);
            Log.d(TAG, "Saving file " + file.getAbsolutePath());

            BufferedSink sink = null;
            try {
                sink = Okio.buffer(Okio.sink(file));
                sink.writeAll(source);
            } finally {
                if (sink != null) {
                    try {
                        sink.close();
                    } catch (IOException e) {
                        Log.w(TAG, "downloadAsFile() IOException while closing sink", e);
                    }
                }
            }

            resultFile = file;
        } catch (IncorrectConfigurationException | UnsuccessfulResponseException | IOException e) {
            ActionResult r = processException(e, "downloadAsFile()");
            if (!r.isSuccess()) return new Pair<>(r, null);
        } finally {
            if (source != null) {
                try {
                    source.close();
                } catch (IOException ignored) {}
            }
        }

        return new Pair<>(new ActionResult(), resultFile);
    }

    protected WallabagWebService getWallabagWebService() {
        Settings settings = getSettings();
        return WallabagWebService.fromSettings(settings);
    }

}
