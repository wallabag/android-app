package fr.gaulupeau.apps.Poche.service;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.QueueHelper;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.entity.DaoSession;
import fr.gaulupeau.apps.Poche.entity.QueueItem;
import fr.gaulupeau.apps.Poche.events.ActionResultEvent;
import fr.gaulupeau.apps.Poche.events.AddLinkFinishedEvent;
import fr.gaulupeau.apps.Poche.events.AddLinkStartedEvent;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueFinishedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueStartedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateFeedsStartedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateFeedsFinishedEvent;
import fr.gaulupeau.apps.Poche.network.FeedUpdater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectCredentialsException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class BGService extends IntentService {

    private static final String TAG = BGService.class.getSimpleName();

    // TODO: rename these so it is obvious to use getters instead?
    private Settings settings;

    private DaoSession daoSession;
    private WallabagService wallabagService;

    public BGService() {
        super(BGService.class.getSimpleName());
        setIntentRedelivery(true);

        Log.d(TAG, "BGService() created");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent() started");

        // this seems to make UI more responsive right after a call to the service.
        // well, this seemed to help better before OperationsHelper introduction
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        ActionRequest actionRequest = ActionRequest.fromIntent(intent);
        ActionResult result = null;

        switch(actionRequest.getAction()) {
            case SyncQueue: {
                SyncQueueStartedEvent startEvent = new SyncQueueStartedEvent(actionRequest);
                postStickyEvent(startEvent);
                try {
                    result = syncOfflineQueue(actionRequest);
                } finally {
                    removeStickyEvent(startEvent);
                    if(result == null) result = new ActionResult(ActionResult.ErrorType.Unknown);
                    postEvent(new SyncQueueFinishedEvent(actionRequest, result));
                }
                break;
            }

            case AddLink: {
                AddLinkStartedEvent startEvent = new AddLinkStartedEvent(actionRequest);
                postStickyEvent(startEvent);
                try {
                    result = addLink(actionRequest);
                } finally {
                    removeStickyEvent(startEvent);
                    if(result == null) result = new ActionResult(ActionResult.ErrorType.Unknown);
                    postEvent(new AddLinkFinishedEvent(actionRequest, result));
                }
                break;
            }

            case Archive:
            case Unarchive:
                result = archiveArticle(actionRequest);
                break;

            case Favorite:
            case Unfavorite:
                result = favoriteArticle(actionRequest);
                break;

            case Delete:
                result = deleteArticle(actionRequest);
                break;

            case UpdateFeed: {
                UpdateFeedsStartedEvent startEvent = new UpdateFeedsStartedEvent(actionRequest);
                postStickyEvent(startEvent);
                try {
                    result = updateFeed(actionRequest);
                } finally {
                    removeStickyEvent(startEvent);
                    if(result == null) result = new ActionResult(ActionResult.ErrorType.Unknown);
                    postEvent(new UpdateFeedsFinishedEvent(actionRequest, result));
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

    private ActionResult syncOfflineQueue(ActionRequest actionRequest) {
        Log.d(TAG, "syncOfflineQueue() started");

        if(!WallabagConnection.isNetworkAvailable()) {
            Log.i(TAG, "syncOfflineQueue() not on-line; exiting");
            return new ActionResult(ActionResult.ErrorType.NoNetwork);
        }

        ActionResult result = new ActionResult();
        Long queueChangedLength = null;

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

                ActionResult itemResult = null;
                int action = item.getAction();
                switch(action) {
                    case QueueHelper.QI_ACTION_ARCHIVE:
                    case QueueHelper.QI_ACTION_UNARCHIVE: {
                        articleItem = true;
                        boolean archive = action == QueueHelper.QI_ACTION_ARCHIVE;

                        itemResult = archiveArticleRemote(articleID, archive);
                        break;
                    }

                    case QueueHelper.QI_ACTION_FAVORITE:
                    case QueueHelper.QI_ACTION_UNFAVORITE: {
                        articleItem = true;
                        boolean favorite = action == QueueHelper.QI_ACTION_FAVORITE;

                        itemResult = favoriteArticleRemote(articleID, favorite);
                        break;
                    }

                    case QueueHelper.QI_ACTION_DELETE: {
                        articleItem = true;

                        itemResult = deleteArticleRemote(articleID);
                        break;
                    }

                    case QueueHelper.QI_ACTION_ADD_LINK: {
                        String link = item.getExtra();
                        if(link == null || link.isEmpty()) {
                            Log.w(TAG, "syncOfflineQueue() item has no link; skipping");
                        }

                        itemResult = addLinkRemote(link);
                        break;
                    }
                }

                if(itemResult == null || itemResult.isSuccess()) {
                    doneQueueItems.add(item);
                } else if(itemResult.getErrorType() != null) {
                    ActionResult.ErrorType itemError = itemResult.getErrorType();

                    Log.i(TAG, "syncOfflineQueue() itemError: " + itemError);

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
                        result.setErrorType(itemError);
                        Log.i(TAG, "syncOfflineQueue() the itemError is a showstopper; breaking");
                        break;
                    }
                    if(mask && articleItem) {
                        maskedArticles.add(articleID);
                    }
                } else { // should not happen
                    Log.w(TAG, "syncOfflineQueue() errorType is not present in itemResult");
                }

                Log.d(TAG, "syncOfflineQueue() finished processing queue item");
            }

            if(!doneQueueItems.isEmpty()) {
                queueHelper.dequeueItems(doneQueueItems);
            }

            if(queueHelper.isQueueChanged()) {
                queueChangedLength = queueHelper.getQueueLength();
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        if(queueChangedLength != null) {
            postEvent(new OfflineQueueChangedEvent(queueChangedLength));
        }

        Log.d(TAG, "syncOfflineQueue() finished");
        return result;
    }

    // TODO: reuse code in {archive,favorite,delete}Article{,Remote}

    private ActionResult archiveArticle(ActionRequest actionRequest) {
        int articleID = actionRequest.getArticleID(); // TODO: check: not null
        boolean archive = actionRequest.getAction() == ActionRequest.Action.Archive;

        Log.d(TAG, String.format("archiveArticle(%d, %s) started", articleID, archive));

        ActionResult result = new ActionResult();
        Long queueChangedLength = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            if(queueHelper.archiveArticle(articleID, archive)) {
                if(WallabagConnection.isNetworkAvailable()) {
                    result.updateWith(archiveArticleRemote(articleID, archive));
                } else {
                    result.setErrorType(ActionResult.ErrorType.NoNetwork);
                }

                if(!result.isSuccess()) {
                    queueHelper.enqueueArchiveArticle(articleID, archive);
                }

                Log.d(TAG, "archiveArticle() synced: " + result.isSuccess());
            } else {
                Log.d(TAG, "archiveArticle(): QueueHelper reports there's nothing to do");
            }

            if(queueHelper.isQueueChanged()) {
                queueChangedLength = queueHelper.getQueueLength();
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        if(queueChangedLength != null) {
            postEvent(new OfflineQueueChangedEvent(queueChangedLength));
        }

        Log.d(TAG, "archiveArticle() finished");
        return result;
    }

    private ActionResult archiveArticleRemote(int articleID, boolean archive) {
        ActionResult result = null;

        try {
            if(!getWallabagService().toggleArchive(articleID)) {
                result = new ActionResult(ActionResult.ErrorType.NegativeResponse);
            }
        } catch(RequestException | IOException e) {
            ActionResult r = processException(e, "archiveArticleRemote()");
            if(!r.isSuccess()) result = r;
        }

        return result;
    }

    private ActionResult favoriteArticle(ActionRequest actionRequest) {
        int articleID = actionRequest.getArticleID(); // TODO: check: not null
        boolean favorite = actionRequest.getAction() == ActionRequest.Action.Favorite;

        Log.d(TAG, String.format("favoriteArticle(%d, %s) started", articleID, favorite));

        ActionResult result = new ActionResult();
        Long queueChangedLength = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            if(queueHelper.favoriteArticle(articleID, favorite)) {
                if(WallabagConnection.isNetworkAvailable()) {
                    result.updateWith(favoriteArticleRemote(articleID, favorite));
                } else {
                    result.setErrorType(ActionResult.ErrorType.NoNetwork);
                }

                if(!result.isSuccess()) {
                    queueHelper.enqueueFavoriteArticle(articleID, favorite);
                }

                Log.d(TAG, "favoriteArticle() synced: " + result.isSuccess());
            } else {
                Log.d(TAG, "favoriteArticle(): QueueHelper reports there's nothing to do");
            }

            if(queueHelper.isQueueChanged()) {
                queueChangedLength = queueHelper.getQueueLength();
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        if(queueChangedLength != null) {
            postEvent(new OfflineQueueChangedEvent(queueChangedLength));
        }

        Log.d(TAG, "favoriteArticle() finished");
        return result;
    }

    private ActionResult favoriteArticleRemote(int articleID, boolean favorite) {
        ActionResult result = null;

        try {
            if(!getWallabagService().toggleFavorite(articleID)) {
                result = new ActionResult(ActionResult.ErrorType.NegativeResponse);
            }
        } catch(RequestException | IOException e) {
            ActionResult r = processException(e, "favoriteArticleRemote()");
            if(!r.isSuccess()) result = r;
        }

        return result;
    }

    private ActionResult deleteArticle(ActionRequest actionRequest) {
        int articleID = actionRequest.getArticleID(); // TODO: check: not null
        Log.d(TAG, String.format("deleteArticle(%d) started", articleID));

        ActionResult result = new ActionResult();
        Long queueChangedLength = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            if(queueHelper.deleteArticle(articleID)) {
                if(WallabagConnection.isNetworkAvailable()) {
                    result.updateWith(deleteArticleRemote(articleID));
                } else {
                    result.setErrorType(ActionResult.ErrorType.NoNetwork);
                }

                if(!result.isSuccess()) {
                    queueHelper.enqueueDeleteArticle(articleID);
                }

                Log.d(TAG, "deleteArticle() synced: " + result.isSuccess());
            } else {
                Log.d(TAG, "deleteArticle(): QueueHelper reports there's nothing to do");
            }

            if(queueHelper.isQueueChanged()) {
                queueChangedLength = queueHelper.getQueueLength();
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        if(queueChangedLength != null) {
            postEvent(new OfflineQueueChangedEvent(queueChangedLength));
        }

        Log.d(TAG, "deleteArticle() finished");
        return result;
    }

    private ActionResult deleteArticleRemote(int articleID) {
        ActionResult result = null;

        try {
            if(!getWallabagService().deleteArticle(articleID)) {
                result = new ActionResult(ActionResult.ErrorType.NegativeResponse);
            }
        } catch(RequestException | IOException e) {
            ActionResult r = processException(e, "deleteArticleRemote()");
            if(!r.isSuccess()) result = r;
        }

        return result;
    }

    private ActionResult addLink(ActionRequest actionRequest) {
        String link = actionRequest.getLink();
        Log.d(TAG, String.format("addLink(%s) started", link));

        ActionResult result = new ActionResult();
        Long queueChangedLength = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            if(queueHelper.addLink(link)) {
                if(WallabagConnection.isNetworkAvailable()) {
                    result.updateWith(addLinkRemote(link));
                } else {
                    result.setErrorType(ActionResult.ErrorType.NoNetwork);
                }

                if(!result.isSuccess()) {
                    queueHelper.enqueueAddLink(link);
                }

                Log.d(TAG, "addLink() synced: " + result.isSuccess());
            } else {
                Log.d(TAG, "addLink(): QueueHelper reports there's nothing to do");
            }

            if(queueHelper.isQueueChanged()) {
                queueChangedLength = queueHelper.getQueueLength();
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        if(queueChangedLength != null) {
            postEvent(new OfflineQueueChangedEvent(queueChangedLength));
        }

        Log.d(TAG, "addLink() finished");
        return result;
    }

    private ActionResult addLinkRemote(String link) {
        ActionResult result = null;

        try {
            if(!getWallabagService().addLink(link)) {
                result = new ActionResult(ActionResult.ErrorType.NegativeResponse);
            }
        } catch(RequestException | IOException e) {
            ActionResult r = processException(e, "addLinkRemote()");
            if(!r.isSuccess()) result = r;
        }

        return result;
    }

    private ActionResult updateFeed(ActionRequest actionRequest) {
        FeedUpdater.FeedType feedType = actionRequest.getFeedUpdateFeedType();
        FeedUpdater.UpdateType updateType = actionRequest.getFeedUpdateUpdateType();

        Log.d(TAG, String.format("updateFeed(%s, %s) started", feedType, updateType));

        ActionResult result = new ActionResult();

        if(WallabagConnection.isNetworkAvailable()) {
            Settings settings = getSettings();
            FeedUpdater feedUpdater = new FeedUpdater(
                    settings.getUrl(),
                    settings.getFeedsUserID(),
                    settings.getFeedsToken(),
                    settings.getHttpAuthUsername(),
                    settings.getHttpAuthPassword(),
                    settings.getWallabagServerVersion());

            // TODO: check: do we need a separate transaction here (since FeedUpdater creates one)?
            // I'll leave it just yet, should not hurt anyway
            DaoSession daoSession = getDaoSession();
            daoSession.getDatabase().beginTransaction();
            try {
                feedUpdater.update(feedType, updateType);

                daoSession.getDatabase().setTransactionSuccessful();
            } catch(XmlPullParserException e) {
                Log.e(TAG, "updateFeed() XmlPullParserException", e);
                result.setErrorType(ActionResult.ErrorType.Unknown);
                result.setMessage("Error while parsing feed"); // TODO: string resource
            } catch(RequestException | IOException e) {
                ActionResult r = processException(e, "updateFeed()");
                if(!r.isSuccess()) result = r;
            } finally {
                daoSession.getDatabase().endTransaction();
            }
        } else {
            result.setErrorType(ActionResult.ErrorType.NoNetwork);
        }

        postEvent(new FeedsChangedEvent(feedType)); // TODO: fix: other feeds may be affected too
        // TODO: also post ArticleChangedEvents?
        // TODO: do not post events if nothing changed

        Log.d(TAG, "updateFeed() finished");
        return result;
    }

    private ActionResult processException(Exception e, String scope) {
        ActionResult result = new ActionResult();

        Log.w(TAG, String.format("%s %s", scope, e.getClass().getName()), e);

        if(e instanceof RequestException) {
            if(e instanceof IncorrectCredentialsException) {
                result.setErrorType(ActionResult.ErrorType.IncorrectCredentials);
            } else if(e instanceof IncorrectConfigurationException) {
                result.setErrorType(ActionResult.ErrorType.IncorrectConfiguration);
            } else {
                result.setErrorType(ActionResult.ErrorType.Unknown);
                result.setMessage(e.getMessage());
            }
        } else if(e instanceof IOException) { // TODO: differentiate errors: timeouts and stuff
            if(e instanceof java.net.UnknownHostException && getSettings().isConfigurationOk()) {
                result.setErrorType(ActionResult.ErrorType.Temporary);
            } else {
                result.setErrorType(ActionResult.ErrorType.Unknown); // TODO: separate Temporary errors
                result.setMessage(e.toString());
            }
            // IOExceptions in most cases mean temporary error,
            // in some cases may mean that the action was completed anyway.
        } else { // other exceptions meant to be handled outside
            result.setErrorType(ActionResult.ErrorType.Unknown);
        }

        return result;
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

    private WallabagService getWallabagService() {
        if(wallabagService == null) {
            Settings settings = getSettings();
            wallabagService = WallabagService.fromSettings(settings);
        }

        return wallabagService;
    }

}
