package fr.gaulupeau.apps.Poche.service;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;

import org.greenrobot.greendao.query.LazyList;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.QueueHelper;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.dao.ArticleDao;
import fr.gaulupeau.apps.Poche.data.dao.DaoSession;
import fr.gaulupeau.apps.Poche.data.dao.entities.Article;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.events.ActionResultEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.FetchImagesStartedEvent;
import fr.gaulupeau.apps.Poche.events.LinkUploadedEvent;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.OfflineQueueChangedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueFinishedEvent;
import fr.gaulupeau.apps.Poche.events.SyncQueueStartedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateFeedsStartedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateFeedsFinishedEvent;
import fr.gaulupeau.apps.Poche.network.FeedUpdater;
import fr.gaulupeau.apps.Poche.network.ImageCacheUtils;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectCredentialsException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class BGService extends IntentService {

    private static class RequestQueueState {

        private LinkedHashMap<Intent, ActionRequest> queue = new LinkedHashMap<>(1);

        private Integer topPriority;

        RequestQueueState() {}

        synchronized void add(Intent intent, ActionRequest request) {
            if(queue.put(intent, request) == null) {
                int requestPriority = request.getPriority();
                if(topPriority == null || requestPriority > topPriority) {
                    topPriority = requestPriority;
                }
            } else {
                Log.w(TAG, "RequestQueueState.add() already contained the intent!");
            }
        }

        synchronized ActionRequest remove(Intent intent) {
            ActionRequest actionRequest = queue.remove(intent);
            if(actionRequest != null) {
                updateState();
            }

            return actionRequest;
        }

        synchronized boolean hasHigherPriorityRequests(int priority) {
            return topPriority != null && priority < topPriority;
        }

        private void updateState() {
            if(queue.isEmpty()) {
                topPriority = null;
                return;
            }

            int priority = Integer.MIN_VALUE;
            for(ActionRequest request: queue.values()) {
                if(request.getPriority() > priority) {
                    priority = request.getPriority();
                }
            }

            topPriority = priority;
        }

    }

    private static final String TAG = BGService.class.getSimpleName();

    private final RequestQueueState queueState = new RequestQueueState();

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
    public void onStart(Intent intent, int startId) {
        Log.d(TAG, "onStart() started");

        queueState.add(intent, ActionRequest.fromIntent(intent));

        super.onStart(intent, startId);

        Log.d(TAG, "onStart() finished");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent() started");

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        handle(queueState.remove(intent));

        Log.d(TAG, "onHandleIntent() finished");
    }

    private void handle(ActionRequest actionRequest) {
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

            case FetchImages: {
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

        if(event != null && event.isAnythingChanged()) {
            postEvent(event);
        }

        Log.d(TAG, "updateFeed() finished");
        return result;
    }

    private void fetchImages(ActionRequest actionRequest) {
        Log.d(TAG, "fetchImages() started");

        if(!ImageCacheUtils.isExternalStorageWritable()) {
            Log.w(TAG, "fetchImages() external storage is not writable");
            return;
        }

        if(checkPriorityAndReschedule(actionRequest)) {
            Log.i(TAG, "fetchImages() has higher priority work, rescheduled");
            return;
        }

        // TODO: probably need to save results in a separate transaction

        ArticleDao articleDao = getDaoSession().getArticleDao();
        LazyList<Article> articleList = articleDao.queryBuilder()
                .where(ArticleDao.Properties.ImagesDownloaded.eq(false))
                .orderAsc(ArticleDao.Properties.ArticleId).listLazyUncached();

        for(Article article: articleList) {
            if(checkPriorityAndReschedule(actionRequest)) {
                Log.i(TAG, "fetchImages() has higher priority work, rescheduled");
                break;
            }

            Log.d(TAG, "fetchImages() processing article " + article.getArticleId());

            ImageCacheUtils.cacheImages(article.getArticleId().longValue(), article.getContent());

            article.setImagesDownloaded(true);
            articleDao.update(article);
        }

        articleList.close();

        Log.d(TAG, "fetchImages() finished");
    }

    private boolean checkPriorityAndReschedule(ActionRequest actionRequest) {
        if(hasHigherPriorityRequests(actionRequest.getPriority())) {
            rescheduleRequest(actionRequest);
            return true;
        }

        return false;
    }

    private boolean hasHigherPriorityRequests(int priority) {
        return queueState.hasHigherPriorityRequests(priority);
    }

    private void rescheduleRequest(ActionRequest request) {
        ServiceHelper.startService(getApplicationContext(), request);
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
        } else if(e instanceof IOException) {
            boolean handled = false;

            if(getSettings().isConfigurationOk()) {
                if(e instanceof java.net.UnknownHostException
                        || e instanceof java.net.ConnectException // TODO: maybe filter by message
                        || e instanceof java.net.SocketTimeoutException) {
                    result.setErrorType(ActionResult.ErrorType.Temporary);
                    handled = true;
                } else if(e instanceof javax.net.ssl.SSLException
                        && e.getMessage() != null
                        && e.getMessage().contains("Connection timed out")) {
                    result.setErrorType(ActionResult.ErrorType.Temporary);
                    handled = true;
                }
            }

            if(!handled) {
                result.setErrorType(ActionResult.ErrorType.Unknown);
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
