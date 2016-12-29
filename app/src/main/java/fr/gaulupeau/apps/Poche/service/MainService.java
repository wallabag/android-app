package fr.gaulupeau.apps.Poche.service;

import android.content.Intent;
import android.os.Process;
import android.util.Log;
import android.util.Pair;

import com.di72nn.stuff.wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.QueueHelper;
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
            case ARCHIVE:
            case UNARCHIVE:
            case FAVORITE:
            case UNFAVORITE:
            case DELETE:
            case ADD_LINK:
                Long queueChangedLength = serveSimpleRequest(actionRequest);
                if(queueChangedLength != null) {
                    postEvent(new OfflineQueueChangedEvent(queueChangedLength, true));
                }
                break;

            case SYNC_QUEUE: {
                SyncQueueStartedEvent startEvent = new SyncQueueStartedEvent(actionRequest);
                postStickyEvent(startEvent);
                Pair<ActionResult, Long> syncResult = null;
                try {
                    syncResult = syncOfflineQueue(actionRequest);
                    result = syncResult.first;
                } finally {
                    removeStickyEvent(startEvent);
                    if(result == null) result = new ActionResult(ActionResult.ErrorType.UNKNOWN);
                    postEvent(new SyncQueueFinishedEvent(actionRequest, result,
                            syncResult != null ? syncResult.second : null));
                }
                break;
            }

            case UPDATE_FEED: {
                UpdateFeedsStartedEvent startEvent = new UpdateFeedsStartedEvent(actionRequest);
                postStickyEvent(startEvent);
                try {
                    result = updateFeed(actionRequest);
                } finally {
                    removeStickyEvent(startEvent);
                    if(result == null) result = new ActionResult(ActionResult.ErrorType.UNKNOWN);
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
                case ARCHIVE:
                case UNARCHIVE:
                    if(queueHelper.archiveArticle(actionRequest.getArticleID(),
                            action == ActionRequest.Action.ARCHIVE)) {
                        queueChangedLength = queueHelper.getQueueLength();
                    }
                    break;

                case FAVORITE:
                case UNFAVORITE:
                    if(queueHelper.favoriteArticle(actionRequest.getArticleID(),
                            action == ActionRequest.Action.FAVORITE)) {
                        queueChangedLength = queueHelper.getQueueLength();
                    }
                    break;

                case DELETE:
                    if(queueHelper.deleteArticle(actionRequest.getArticleID())) {
                        queueChangedLength = queueHelper.getQueueLength();
                    }
                    break;

                case ADD_LINK:
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
            return new Pair<>(new ActionResult(ActionResult.ErrorType.NO_NETWORK), null);
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

                        if(getWallabagServiceWrapper().archiveArticle(
                                articleID, action == QueueHelper.QI_ACTION_ARCHIVE) == null) {
                            itemResult = new ActionResult(ActionResult.ErrorType.NOT_FOUND);
                        }
                        break;
                    }

                    case QueueHelper.QI_ACTION_FAVORITE:
                    case QueueHelper.QI_ACTION_UNFAVORITE: {
                        articleItem = true;

                        if(getWallabagServiceWrapper().favoriteArticle(articleID,
                                action == QueueHelper.QI_ACTION_FAVORITE) == null) {
                            itemResult = new ActionResult(ActionResult.ErrorType.NOT_FOUND);
                        }
                        break;
                    }

                    case QueueHelper.QI_ACTION_DELETE: {
                        articleItem = true;

                        if(getWallabagServiceWrapper().deleteArticle(articleID) == null) {
                            itemResult = new ActionResult(ActionResult.ErrorType.NOT_FOUND);
                        }
                        break;
                    }

                    case QueueHelper.QI_ACTION_ADD_LINK: {
                        String link = item.getExtra();
                        if(link != null && !link.isEmpty()) {
                            if(getWallabagServiceWrapper().addArticle(link) == null) {
                                itemResult = new ActionResult(ActionResult.ErrorType.NEGATIVE_RESPONSE);
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
            } catch(UnsuccessfulResponseException | IOException e) {
                ActionResult r = processException(e, "syncOfflineQueue()");
                if(!r.isSuccess()) itemResult = r;
            } catch(Exception e) {
                Log.e(TAG, "syncOfflineQueue() item processing exception", e);

                itemResult = new ActionResult();
                itemResult.setErrorType(ActionResult.ErrorType.UNKNOWN);
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
                    case TEMPORARY:
                    case NO_NETWORK:
                        stop = true;
                        break;
                    case INCORRECT_CONFIGURATION:
                    case INCORRECT_CREDENTIALS:
                        stop = true;
                        break;
                    case UNKNOWN:
                        stop = true;
                        break;
                    case NEGATIVE_RESPONSE:
                        mask = true; // ?
                        break;
                }

                if(stop) {
                    result.updateWith(itemResult);
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
            DaoSession daoSession = getDaoSession();
            daoSession.getDatabase().beginTransaction();
            try {
                event = new FeedUpdater(getWallabagServiceWrapper()).update(feedType, updateType);

                daoSession.getDatabase().setTransactionSuccessful();
            } catch(UnsuccessfulResponseException | IOException e) {
                ActionResult r = processException(e, "updateFeed()");
                result.updateWith(r);
            } catch(Exception e) {
                Log.e(TAG, "updateFeed() exception", e);

                result.setErrorType(ActionResult.ErrorType.UNKNOWN);
                result.setMessage(e.toString());
            } finally {
                daoSession.getDatabase().endTransaction();
            }
        } else {
            result.setErrorType(ActionResult.ErrorType.NO_NETWORK);
        }

        if(event != null && event.isAnythingChanged()) {
            postEvent(event);
        }

        Log.d(TAG, "updateFeed() finished");
        return result;
    }

}
