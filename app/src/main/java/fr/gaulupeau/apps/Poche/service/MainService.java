package fr.gaulupeau.apps.Poche.service;

import android.content.Intent;
import android.os.Process;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.QueueHelper;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.events.ActionResultEvent;
import fr.gaulupeau.apps.Poche.events.LinkUploadedEvent;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueFinishedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueStartedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateFeedsStartedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateFeedsFinishedEvent;
import fr.gaulupeau.apps.Poche.network.FeedUpdater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectFeedException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class MainService extends IntentServiceBase {

    private static final String TAG = MainService.class.getSimpleName();

    public MainService() {
        super(MainService.class.getSimpleName());
        setIntentRedelivery(true);

        Log.d(TAG, "MainService() created");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent() started");

        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        ActionRequest actionRequest = ActionRequest.fromIntent(intent);
        ActionResult result = null;

        switch(actionRequest.getAction()) {
            case Archive:
            case Unarchive:
            case Favorite:
            case Unfavorite:
            case Delete:
            case AddLink:
                Long queueChangedLength = serveSimpleRequest(actionRequest);
                if(queueChangedLength != null) {
                    postEvent(new OfflineQueueChangedEvent(queueChangedLength, true));
                }
                break;

            case SyncQueue: {
                SyncQueueStartedEvent startEvent = new SyncQueueStartedEvent(actionRequest);
                postStickyEvent(startEvent);
                Pair<ActionResult, Long> syncResult = null;
                try {
                    syncResult = syncOfflineQueue(actionRequest);
                    result = syncResult.first;
                } finally {
                    removeStickyEvent(startEvent);
                    if(result == null) result = new ActionResult(ActionResult.ErrorType.Unknown);
                    postEvent(new SyncQueueFinishedEvent(actionRequest, result,
                            syncResult != null ? syncResult.second : null));
                }
                break;
            }

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

        if(result != null) {
            postEvent(new ActionResultEvent(actionRequest, result));
        }

        Log.d(TAG, "onHandleIntent() finished");
    }

    private Long serveSimpleRequest(ActionRequest actionRequest) {
        Log.d(TAG, String.format("serveSimpleRequest() started; action: %s, articleID: %s, link: %s",
                actionRequest.getAction(), actionRequest.getArticleID(), actionRequest.getLink()));

        Long queueChangedLength = null;

        DaoSession daoSession = getDaoSession();
        daoSession.getDatabase().beginTransaction();
        try {
            QueueHelper queueHelper = new QueueHelper(daoSession);

            ActionRequest.Action action = actionRequest.getAction();
            switch(action) {
                case Archive:
                case Unarchive:
                    if(queueHelper.archiveArticle(actionRequest.getArticleID(),
                            action == ActionRequest.Action.Archive)) {
                        queueChangedLength = queueHelper.getQueueLength();
                    }
                    break;

                case Favorite:
                case Unfavorite:
                    if(queueHelper.favoriteArticle(actionRequest.getArticleID(),
                            action == ActionRequest.Action.Favorite)) {
                        queueChangedLength = queueHelper.getQueueLength();
                    }
                    break;

                case Delete:
                    if(queueHelper.deleteArticle(actionRequest.getArticleID())) {
                        queueChangedLength = queueHelper.getQueueLength();
                    }
                    break;

                case AddLink:
                    if(queueHelper.addLink(actionRequest.getLink())) {
                        queueChangedLength = queueHelper.getQueueLength();
                    }
                    break;

                default:
                    Log.w(TAG, "serveSimpleRequest() action is not implemented: " + action);
                    break;
            }

            daoSession.getDatabase().setTransactionSuccessful();
        } finally {
            daoSession.getDatabase().endTransaction();
        }

        Log.d(TAG, "serveSimpleRequest() finished");
        return queueChangedLength;
    }

    private Pair<ActionResult, Long> syncOfflineQueue(ActionRequest actionRequest) {
        Log.d(TAG, "syncOfflineQueue() started");

        if(!WallabagConnection.isNetworkAvailable()) {
            Log.i(TAG, "syncOfflineQueue() not on-line; exiting");
            return new Pair<>(new ActionResult(ActionResult.ErrorType.NoNetwork), null);
        }

        ActionResult result = new ActionResult();
        boolean urlUploaded = false;

        DaoSession daoSession = getDaoSession();
        QueueHelper queueHelper = new QueueHelper(daoSession);

        List<QueueItem> queueItems = queueHelper.getQueueItems();

        List<QueueItem> completedQueueItems = new ArrayList<>(queueItems.size());
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
            try {
                int action = item.getAction();
                switch(action) {
                    case QueueHelper.QI_ACTION_ARCHIVE:
                    case QueueHelper.QI_ACTION_UNARCHIVE: {
                        articleItem = true;

                        if(!getWallabagService().toggleArchive(articleID)) {
                            itemResult = new ActionResult(ActionResult.ErrorType.NegativeResponse);
                        }
                        break;
                    }

                    case QueueHelper.QI_ACTION_FAVORITE:
                    case QueueHelper.QI_ACTION_UNFAVORITE: {
                        articleItem = true;

                        if(!getWallabagService().toggleFavorite(articleID)) {
                            itemResult = new ActionResult(ActionResult.ErrorType.NegativeResponse);
                        }
                        break;
                    }

                    case QueueHelper.QI_ACTION_DELETE: {
                        articleItem = true;

                        if(!getWallabagService().deleteArticle(articleID)) {
                            itemResult = new ActionResult(ActionResult.ErrorType.NegativeResponse);
                        }
                        break;
                    }

                    case QueueHelper.QI_ACTION_ADD_LINK: {
                        String link = item.getExtra();
                        if(link != null && !link.isEmpty()) {
                            if(!getWallabagService().addLink(link)) {
                                itemResult = new ActionResult(ActionResult.ErrorType.NegativeResponse);
                            }
                            if(itemResult == null || itemResult.isSuccess()) urlUploaded = true;
                        } else {
                            Log.w(TAG, "syncOfflineQueue() item has no link; skipping");
                        }
                        break;
                    }

                    default:
                        throw new IllegalArgumentException("Unknown action: " + action);
                }
            } catch(RequestException | IOException e) {
                ActionResult r = processException(e, "syncOfflineQueue()");
                if(!r.isSuccess()) itemResult = r;
            } catch(Exception e) {
                Log.e(TAG, "syncOfflineQueue() item processing exception", e);

                itemResult = new ActionResult();
                itemResult.setErrorType(ActionResult.ErrorType.Unknown);
                itemResult.setMessage(e.toString());
            }

            if(itemResult == null || itemResult.isSuccess()) {
                completedQueueItems.add(item);
            } else if(itemResult.getErrorType() != null) {
                ActionResult.ErrorType itemError = itemResult.getErrorType();

                Log.i(TAG, "syncOfflineQueue() itemError: " + itemError);

                boolean stop = false;
                boolean mask = false; // it seems masking is not used
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
                        stop = true;
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

        Long queueLength = null;

        if(!completedQueueItems.isEmpty()) {
            daoSession.getDatabase().beginTransaction();
            try {
                queueHelper.dequeueItems(completedQueueItems);

                queueLength = queueHelper.getQueueLength();

                daoSession.getDatabase().setTransactionSuccessful();
            } finally {
                daoSession.getDatabase().endTransaction();
            }
        }

        if(queueLength != null) {
            postEvent(new OfflineQueueChangedEvent(queueLength));
        } else {
            queueLength = (long)queueItems.size();
        }

        if(urlUploaded) {
            postEvent(new LinkUploadedEvent(new ActionResult()));
        }

        Log.d(TAG, "syncOfflineQueue() finished");
        return new Pair<>(result, queueLength);
    }

    private ActionResult updateFeed(ActionRequest actionRequest) {
        FeedUpdater.FeedType feedType = actionRequest.getFeedUpdateFeedType();
        FeedUpdater.UpdateType updateType = actionRequest.getFeedUpdateUpdateType();

        Log.d(TAG, String.format("updateFeed(%s, %s) started", feedType, updateType));

        ActionResult result = new ActionResult();
        ArticlesChangedEvent event = null;

        if(WallabagConnection.isNetworkAvailable()) {
            Settings settings = getSettings();
            FeedUpdater feedUpdater = new FeedUpdater(
                    settings.getUrl(),
                    settings.getFeedsUserID(),
                    settings.getFeedsToken(),
                    settings.getHttpAuthUsername(),
                    settings.getHttpAuthPassword(),
                    settings.getWallabagServerVersion());

            DaoSession daoSession = getDaoSession();
            daoSession.getDatabase().beginTransaction();
            try {
                event = feedUpdater.update(feedType, updateType);

                daoSession.getDatabase().setTransactionSuccessful();
            } catch(IncorrectFeedException e) {
                Log.e(TAG, "updateFeed() IncorrectFeedException", e);
                result.setErrorType(ActionResult.ErrorType.Unknown);
                result.setMessage("Error while parsing feed: " + e.getMessage()); // TODO: string resource
            } catch(RequestException | IOException e) {
                ActionResult r = processException(e, "updateFeed()");
                if(!r.isSuccess()) result = r;
            } finally {
                daoSession.getDatabase().endTransaction();
            }
        } else {
            result.setErrorType(ActionResult.ErrorType.NoNetwork);
        }

        if(event != null && event.isAnythingChanged()) {
            postEvent(event);
        }

        Log.d(TAG, "updateFeed() finished");
        return result;
    }

}
