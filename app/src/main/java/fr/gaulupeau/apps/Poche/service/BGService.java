package fr.gaulupeau.apps.Poche.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.QueueHelper;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.WallabagSettings;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;
import fr.gaulupeau.apps.Poche.entity.QueueItem;
import fr.gaulupeau.apps.Poche.events.ArticleChangedEvent;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.StartedUpdatingFeedsEvent;
import fr.gaulupeau.apps.Poche.events.StoppedUpdatingFeedsEvent;
import fr.gaulupeau.apps.Poche.network.FeedUpdater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectCredentialsException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;

public class BGService extends IntentService {

    enum ErrorType {
        Temporary, NoNetwork,
        IncorrectConfiguration, IncorrectCredentials,
        NegativeResponse, Unknown
    }

    private static class Result {

        private boolean success = true;
        private ErrorType errorType;
        private String message;

        public Result() {}

        public Result(ErrorType errorType) {
            this.success = errorType == null;
            this.errorType = errorType;
        }

        public Result(ErrorType errorType, String message) {
            this.errorType = errorType;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public ErrorType getErrorType() {
            return errorType;
        }

        public void setErrorType(ErrorType errorType) {
            this.errorType = errorType;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

    }

    public static final String ACTION_ADD_LINK = "wallabag.action.add_link";
    public static final String ACTION_ARCHIVE = "wallabag.action.archive";
    public static final String ACTION_UNARCHIVE = "wallabag.action.unarchive";
    public static final String ACTION_FAVORITE = "wallabag.action.favorite";
    public static final String ACTION_UNFAVORITE = "wallabag.action.unfavorite";
    public static final String ACTION_DELETE = "wallabag.action.delete";
    public static final String ACTION_SYNC_QUEUE = "wallabag.action.sync_queue";
    public static final String ACTION_UPDATE_FEED = "wallabag.action.update_feed";

    public static final String EXTRA_ARTICLE_ID = "wallabag.extra.article_id";
    public static final String EXTRA_LINK = "wallabag.extra.link";
    public static final String EXTRA_UPDATE_FEED_FEED_TYPE = "wallabag.extra.update_feed.feed_type";
    public static final String EXTRA_UPDATE_FEED_UPDATE_TYPE = "wallabag.extra.update_feed.update_type";

    private static final String TAG = BGService.class.getSimpleName();

    // TODO: rename these so it is obvious to use getters instead?
    private Handler handler;

    private Settings settings;

    private DaoSession daoSession;
    private ArticleDao articleDao;
    private WallabagService wallabagService;

    public BGService() {
        super(BGService.class.getSimpleName());

        Log.d(TAG, "BGService() created");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent() started");

        String action = intent.getAction();

        Result result = null;
        switch(action) {
            case ACTION_SYNC_QUEUE:
                result = syncOfflineQueue();
                break;

            case ACTION_ADD_LINK:
                result = addLink(intent.getStringExtra(EXTRA_LINK));
                break;

            case ACTION_ARCHIVE:
            case ACTION_UNARCHIVE:
                // TODO: check articleID
                result = archiveArticle(
                        intent.getIntExtra(EXTRA_ARTICLE_ID, -1), ACTION_ARCHIVE.equals(action));
                break;

            case ACTION_FAVORITE:
            case ACTION_UNFAVORITE:
                // TODO: check articleID
                result = favoriteArticle(
                        intent.getIntExtra(EXTRA_ARTICLE_ID, -1), ACTION_FAVORITE.equals(action));
                break;

            case ACTION_DELETE:
                // TODO: check articleID
                result = deleteArticle(intent.getIntExtra(EXTRA_ARTICLE_ID, -1));
                break;

            case ACTION_UPDATE_FEED: {
                FeedUpdater.FeedType feedType = null;
                FeedUpdater.UpdateType updateType = null;
                try {
                    String feedTypeString = intent.getStringExtra(EXTRA_UPDATE_FEED_FEED_TYPE);
                    if(feedTypeString != null) {
                        feedType = FeedUpdater.FeedType.valueOf(feedTypeString);
                    }

                    String updateTypeString = intent.getStringExtra(EXTRA_UPDATE_FEED_UPDATE_TYPE);
                    if(updateTypeString != null) {
                        updateType = FeedUpdater.UpdateType.valueOf(updateTypeString);
                    }
                } catch(IllegalArgumentException e) {
                    Log.w(TAG, "onHandleIntent() couldn't parse update feed parameters", e);
                    break;
                }

                result = updateFeed(feedType, updateType);
                break;
            }
        }

        Notification notification = null;

        if(result != null) {
            if(result.isSuccess()) {
                NotificationCompat.Builder notificationBuilder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.drawable.ic_done_24dp)
                                .setContentTitle("Action finished")
                                .setContentText(action)
                                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                                        String.format("Action \"%s\" finished", action)));

                notification = notificationBuilder.build();

                showToast("Operation completed", Toast.LENGTH_LONG); // TODO: remove: debug only
            } else {
                // TODO: implement
                ErrorType errorType = result.getErrorType();
                switch(errorType) {
                    case Temporary:
                    case NoNetwork:
                        // don't show it to user at all or make it suppressible
                        // schedule auto-retry
                        break;
                    case IncorrectConfiguration:
                    case IncorrectCredentials:
                        // notify user -- user must fix something before retry
                        // maybe suppress notification if:
                        //  - the action was not requested by user, and
                        //  - notification was already shown in the past.
                        // no auto-retry
                        break;
                    case Unknown:
                        // this is undecided yet
                        // show notification + schedule auto-retry
                        break;
                    case NegativeResponse:
                        // server acknowledged the operation but failed/refused to performed it;
                        // detection of such response is not implemented on client yet
                        break;
                }

                NotificationCompat.Builder notificationBuilder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.drawable.ic_stop_24dp)
                                .setContentTitle("Action failed")
                                .setContentText(action);

                if(result.getMessage() != null) {
                    notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(
                            String.format("Action: %s\nError: %s", action, result.getMessage())));

                    showToast(String.format(
                            "\"%s\" error detected; message: \"%s\"",
                            errorType, result.getMessage()), Toast.LENGTH_LONG); // TODO: remove: debug only
                } else {
                    showToast(String.format("\"%s\" error detected", errorType), Toast.LENGTH_LONG); // TODO: remove: debug only
                }

                notification = notificationBuilder.build();
            }
        }

        // TODO: debug only
        if(notification != null) {
            Context context = getApplicationContext();
            NotificationManager notificationManager = (NotificationManager)context
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            notificationManager.notify(TAG, 100500, notification);
        }

        Log.d(TAG, "onHandleIntent() finished");
    }

    private Result syncOfflineQueue() {
        Log.d(TAG, "syncOfflineQueue() started");

        if(!WallabagConnection.isNetworkOnline()) {
            Log.i(TAG, "syncOfflineQueue() not on-line; exiting");
            return new Result(ErrorType.NoNetwork);
        }

        ErrorType errorType = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            List<QueueItem> queueItems = queueHelper.getQueueItems();

            List<QueueItem> doneQueueItems = new ArrayList<>(queueItems.size());
            Set<Integer> maskedArticles = new HashSet<>();

            for(QueueItem item: queueItems) {
                Integer articleIdInteger = item.getArticleId();

                Log.d(TAG, String.format(
                        "syncOfflineQueue() processing: queue item ID: %d, article ID: \"%s\"",
                        item.getId(), articleIdInteger));

                if(articleIdInteger != null && maskedArticles.contains(articleIdInteger)) {
                    Log.d(TAG, String.format(
                            "syncOfflineQueue() article with ID: \"%d\" is masked; skipping",
                            articleIdInteger));
                    continue;
                }
                int articleID = articleIdInteger != null ? articleIdInteger : -1;

                boolean articleItem = false;

                ErrorType itemError = null;
                int action = item.getAction();
                switch(action) {
                    case QueueHelper.QI_ACTION_ARCHIVE:
                    case QueueHelper.QI_ACTION_UNARCHIVE: {
                        articleItem = true;
                        boolean archive = action == QueueHelper.QI_ACTION_ARCHIVE;

                        itemError = archiveArticleRemote(articleID, archive, false);
                        break;
                    }

                    case QueueHelper.QI_ACTION_FAVORITE:
                    case QueueHelper.QI_ACTION_UNFAVORITE: {
                        articleItem = true;
                        boolean favorite = action == QueueHelper.QI_ACTION_FAVORITE;

                        itemError = favoriteArticleRemote(articleID, favorite, false);
                        break;
                    }

                    case QueueHelper.QI_ACTION_DELETE: {
                        articleItem = true;

                        itemError = deleteArticleRemote(articleID, false);
                        break;
                    }

                    case QueueHelper.QI_ACTION_ADD_LINK: {
                        String link = item.getExtra();
                        if(link == null || link.isEmpty()) {
                            Log.w(TAG, "syncOfflineQueue() item has no link; skipping");
                        }

                        itemError = addLinkRemote(link, false);
                        break;
                    }
                }

                if(itemError == null) {
                    doneQueueItems.add(item);
                } else {
                    boolean stop = false;
                    boolean mask = false;
                    switch(itemError) {
                        case Temporary:
                        case NoNetwork:
                            stop = true;
                            break;
                        case IncorrectConfiguration:
                        case IncorrectCredentials:
                            stop = true;
                            break;
                        case Unknown:
                            mask = true; // ?
                            break;
                        case NegativeResponse:
                            mask = true; // ?
                            break;
                    }

                    if(stop) {
                        errorType = itemError;
                        Log.i(TAG, String.format(
                                "syncOfflineQueue() itemError (%s) is a showstopper; breaking",
                                itemError));
                        break;
                    }
                    if(mask && articleItem) {
                        maskedArticles.add(articleID);
                    }
                }

                Log.d(TAG, "syncOfflineQueue() finished processing queue item");
            }

            if(!doneQueueItems.isEmpty()) {
                queueHelper.dequeueItems(doneQueueItems);
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        Log.d(TAG, "syncOfflineQueue() finished");
        return new Result(errorType);
    }

    // TODO: reuse code in {archive,favorite,delete}Article{,Remote}

    private Result archiveArticle(int articleID, boolean archive) {
        Log.d(TAG, String.format("archiveArticle(%d, %s) started", articleID, archive));

        Article article = getArticle(articleID);
        if(article == null) {
            Log.w(TAG, "archiveArticle() article was not found");
            return new Result(); // not an error?
        }

        // local changes

        if(article.getArchive() != archive) {
            article.setArchive(archive);
            getArticleDao().update(article);

            getEventBus().post(new ArticleChangedEvent(article));
            // TODO: notify widget somehow (more specific event?)

            Log.d(TAG, "archiveArticle() article object updated");
        } else {
            Log.d(TAG, "archiveArticle(): article state was not changed");

            // TODO: check: do we need to continue with the sync part? Probably yes
        }

        // remote changes / queue

        ErrorType errorType = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            if(queueHelper.archiveArticle(articleID, archive)) {
                ErrorType r = archiveArticleRemote(articleID, archive, true);
                if(r != null) errorType = r;

                if(errorType != null) {
                    queueHelper.enqueueArchiveArticle(articleID, archive);
                }

                Log.d(TAG, "archiveArticle() synced: " + (errorType == null));
            } else {
                Log.d(TAG, "archiveArticle(): QueueHelper reports there's nothing to do");
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        Log.d(TAG, "archiveArticle() finished");
        return new Result(errorType);
    }

    private ErrorType archiveArticleRemote(int articleID, boolean archive, boolean checkNetwork) {
        ErrorType errorType = null;

        if(checkNetwork) {
            if(!WallabagConnection.isNetworkOnline()) return ErrorType.NoNetwork;
        }

        try {
            if(!getWallabagService().toggleArchive(articleID)) {
                errorType = ErrorType.NegativeResponse;
            }
        } catch(RequestException | IOException e) {
            errorType = processException(e, "archiveArticleRemote()");
        }

        return errorType;
    }

    private Result favoriteArticle(int articleID, boolean favorite) {
        Log.d(TAG, String.format("favoriteArticle(%d, %s) started", articleID, favorite));

        Article article = getArticle(articleID);
        if(article == null) {
            Log.w(TAG, "favoriteArticle() article was not found");
            return new Result(); // not an error?
        }

        // local changes

        if(article.getFavorite() != favorite) {
            article.setFavorite(favorite);
            getArticleDao().update(article);

            getEventBus().post(new ArticleChangedEvent(article));
            // TODO: notify widget somehow (more specific event?)

            Log.d(TAG, "favoriteArticle() article object updated");
        } else {
            Log.d(TAG, "favoriteArticle(): article state was not changed");

            // TODO: check: do we need to continue with the sync part? Probably yes
        }

        // remote changes / queue

        ErrorType errorType = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            if(queueHelper.favoriteArticle(articleID, favorite)) {
                ErrorType r = favoriteArticleRemote(articleID, favorite, true);
                if(r != null) errorType = r;

                if(errorType != null) {
                    queueHelper.enqueueFavoriteArticle(articleID, favorite);
                }

                Log.d(TAG, "favoriteArticle() synced: " + (errorType == null));
            } else {
                Log.d(TAG, "favoriteArticle(): QueueHelper reports there's nothing to do");
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        Log.d(TAG, "favoriteArticle() finished");
        return new Result(errorType);
    }

    private ErrorType favoriteArticleRemote(int articleID, boolean favorite, boolean checkNetwork) {
        ErrorType errorType = null;

        if(checkNetwork) {
            if(!WallabagConnection.isNetworkOnline()) return ErrorType.NoNetwork;
        }

        try {
            if(!getWallabagService().toggleFavorite(articleID)) {
                errorType = ErrorType.NegativeResponse;
            }
        } catch(RequestException | IOException e) {
            errorType = processException(e, "favoriteArticleRemote()");
        }

        return errorType;
    }

    private Result deleteArticle(int articleID) {
        Log.d(TAG, String.format("deleteArticle(%d) started", articleID));

        Article article = getArticle(articleID);
        if(article == null) {
            Log.w(TAG, "favoriteArticle() article was not found");
            return new Result(); // not an error?
        }

        // local changes

        getArticleDao().delete(article);

        getEventBus().post(new ArticleChangedEvent(article));
        // TODO: notify widget somehow (more specific event?)

        Log.d(TAG, "deleteArticle() article object deleted");

        // remote changes / queue

        ErrorType errorType = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            if(queueHelper.deleteArticle(articleID)) {
                ErrorType r = deleteArticleRemote(articleID, true);
                if(r != null) errorType = r;

                if(errorType != null) {
                    queueHelper.enqueueDeleteArticle(articleID);
                }

                Log.d(TAG, "deleteArticle() synced: " + (errorType == null));
            } else {
                Log.d(TAG, "deleteArticle(): QueueHelper reports there's nothing to do");
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        Log.d(TAG, "deleteArticle() finished");
        return new Result(errorType);
    }

    private ErrorType deleteArticleRemote(int articleID, boolean checkNetwork) {
        ErrorType errorType = null;

        if(checkNetwork) {
            if(!WallabagConnection.isNetworkOnline()) return ErrorType.NoNetwork;
        }

        try {
            if(!getWallabagService().deleteArticle(articleID)) {
                errorType = ErrorType.NegativeResponse;
            }
        } catch(RequestException | IOException e) {
            errorType = processException(e, "deleteArticleRemote()");
        }

        return errorType;
    }

    private Result addLink(String link) {
        Log.d(TAG, String.format("addLink(%s) started", link));

        // local changes
        // none

        // remote changes / queue

        ErrorType errorType = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            if(queueHelper.addLink(link)) {
                ErrorType r = addLinkRemote(link, true);
                if(r != null) errorType = r;

                if(errorType != null) {
                    queueHelper.enqueueAddLink(link);
                }

                Log.d(TAG, "addLink() synced: " + (errorType == null));
            } else {
                Log.d(TAG, "addLink(): QueueHelper reports there's nothing to do");
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        Log.d(TAG, "addLink() finished");
        return new Result(errorType);
    }

    private ErrorType addLinkRemote(String link, boolean checkNetwork) {
        ErrorType errorType = null;

        if(checkNetwork) {
            if(!WallabagConnection.isNetworkOnline()) return ErrorType.NoNetwork;
        }

        try {
            if(!getWallabagService().addLink(link)) {
                errorType = ErrorType.NegativeResponse;
            }
        } catch(RequestException | IOException e) {
            errorType = processException(e, "addLinkRemote()");
        }

        return errorType;
    }

    private Result updateFeed(FeedUpdater.FeedType feedType, FeedUpdater.UpdateType updateType) {
        Log.d(TAG, String.format("updateFeed(%s, %s) started", feedType, updateType));

        Result result = new Result();
        ErrorType errorType = null;

        if(WallabagConnection.isNetworkOnline()) {
            StartedUpdatingFeedsEvent startedUpdatingFeedsEvent = new StartedUpdatingFeedsEvent(feedType);
            getEventBus().postSticky(startedUpdatingFeedsEvent);

            // TODO: move notification stuff somewhere else
            Context context = getApplicationContext();
            NotificationManager notificationManager = (NotificationManager)context
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            String detailedMessage;
            if(feedType == null) detailedMessage = "Updating all feeds";
            else detailedMessage = String.format("Updating %s feed", feedType);
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.ic_action_refresh)
                    .setContentTitle("Updating feeds")
                    .setContentText(detailedMessage)
                    .setOngoing(true);

            notificationManager.notify(TAG, 100501, notificationBuilder.setProgress(0, 0, true).build());

            WallabagSettings wallabagSettings = WallabagSettings.settingsFromDisk(getSettings());
            FeedUpdater feedUpdater = new FeedUpdater(
                    wallabagSettings.wallabagURL,
                    wallabagSettings.userID, wallabagSettings.userToken,
                    getSettings().getInt(Settings.WALLABAG_VERSION, -1));

            // TODO: check: do we need a separate transaction here (since FeedUpdater creates one)?
            // I'll leave it just yet, should not hurt anyway
            DaoSession daoSession = getDaoSession();
            daoSession.getDatabase().beginTransaction();
            try {
                feedUpdater.update(feedType, updateType);

                daoSession.getDatabase().setTransactionSuccessful();
            } catch (FeedUpdater.FeedUpdaterException e) {
                Log.w(TAG, "updateFeed() FeedUpdaterException", e);
                // TODO: error type detection
                result.setMessage(e.getMessage());
                errorType = ErrorType.Unknown;
            } finally {
                daoSession.getDatabase().endTransaction();
            }

            notificationManager.cancel(TAG, 100501);

            getEventBus().post(new FeedsChangedEvent(feedType));
            getEventBus().removeStickyEvent(startedUpdatingFeedsEvent);
            getEventBus().post(new StoppedUpdatingFeedsEvent(feedType));
        } else {
            errorType = ErrorType.NoNetwork;
        }

        if(errorType != null) {
            result.setErrorType(errorType);
            result.setSuccess(false);
        }

        Log.d(TAG, "updateFeed() finished");
        return result;
    }

    private ErrorType processException(Exception e, String scope) {
        ErrorType errorType;

        // TODO: simplify logging?
        if(e instanceof RequestException) {
            if(e instanceof IncorrectCredentialsException) {
                errorType = ErrorType.IncorrectCredentials;
                Log.w(TAG, scope + " IncorrectCredentialsException", e);
            } else if(e instanceof IncorrectConfigurationException) {
                errorType = ErrorType.IncorrectConfiguration;
                Log.w(TAG, scope + " IncorrectConfigurationException", e);
            } else {
                errorType = ErrorType.Unknown;
                Log.w(TAG, scope + " RequestException", e);
            }
        } else if(e instanceof IOException) { // TODO: differentiate errors: timeouts and stuff
            errorType = ErrorType.Temporary;
            // IOExceptions in most cases mean temporary error,
            // in some cases may mean that the action was completed anyway
            Log.w(TAG, scope + " IOException", e);
        } else { // other exceptions meant to be handled outside
            errorType = ErrorType.Unknown;
            Log.w(TAG, scope + " Exception", e);
        }

        return errorType;
    }

    private Handler getHandler() {
        if(handler == null) {
            handler = new Handler(getMainLooper());
        }

        return handler;
    }

    private Settings getSettings() {
        if(settings == null) {
            settings = new Settings(this);
        }

        return settings;
    }

    private DaoSession getDaoSession() {
        if(daoSession == null) {
            daoSession = DbConnection.getSession();
        }

        return daoSession;
    }

    private ArticleDao getArticleDao() {
        if(articleDao == null) {
            articleDao = getDaoSession().getArticleDao();
        }

        return articleDao;
    }

    private WallabagService getWallabagService() {
        if(wallabagService == null) {
            Settings settings = getSettings();
            // TODO: check credentials? (throw an exception)
            wallabagService = new WallabagService(
                    settings.getUrl(),
                    settings.getKey(Settings.USERNAME),
                    settings.getKey(Settings.PASSWORD));
        }

        return wallabagService;
    }

    private EventBus getEventBus() {
        return EventBus.getDefault();
    }

    private void showToast(final String text, final int duration) {
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, duration).show();
            }
        });
    }

    private Article getArticle(int articleID) {
        return getArticleDao().queryBuilder()
                .where(ArticleDao.Properties.ArticleId.eq(articleID))
                .build().unique();
    }

}
