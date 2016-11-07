package fr.gaulupeau.apps.Poche.events;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.FeedUpdater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;
import fr.gaulupeau.apps.Poche.service.AlarmHelper;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;
import fr.gaulupeau.apps.Poche.ui.IconUnreadWidget;
import fr.gaulupeau.apps.Poche.ui.preferences.SettingsActivity;

// TODO: fix getters sync (AFAIK, not so important yet)
public class EventProcessor {

    private static final String TAG = EventProcessor.class.getSimpleName();

    private static final int NOTIFICATION_ID_OTHER = 0;
    private static final int NOTIFICATION_ID_UPDATE_FEEDS_ONGOING = 1;
    private static final int NOTIFICATION_ID_FETCH_IMAGES_ONGOING = 2;
    private static final int NOTIFICATION_ID_SYNC_QUEUE_ONGOING = 3;

    private Context context;
    private Settings settings;
    private Handler mainHandler;
    private NotificationManager notificationManager;

    private Long currentOperationID; // TODO: replace with ActionRequest?

    private boolean delayedNetworkChangedTask;

    public EventProcessor(Context context) {
        this.context = context;
    }

    public void start() {
        EventBus.getDefault().register(this);
    }

    public void stop() {
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onBootCompletedEvent(BootCompletedEvent event) {
        Log.d(TAG, "onBootCompletedEvent() started");

        Settings settings = getSettings();
        if(settings.isAutoSyncEnabled()) {
            Log.d(TAG, "onBootCompletedEvent() setting an alarm");

            AlarmHelper.setAlarm(getContext(), settings.getAutoSyncInterval(), false);
        }
    }

    @Subscribe
    public void onAlarmReceivedEvent(AlarmReceivedEvent event) {
        Log.d(TAG, "onAlarmReceivedEvent() started");

        Settings settings = getSettings();
        if(!settings.isAutoSyncEnabled()) {
            Log.w(TAG, "onAlarmReceivedEvent() alarm received even though auto-sync is off");
            return;
        }

        if(!settings.isConfigurationOk()) {
            Log.d(TAG, "onAlarmReceivedEvent() configuration is not ok: skipping");
            return;
        }

        if(!WallabagConnection.isNetworkAvailable()) {
            Log.d(TAG, "alarmReceived() no network, skipping auto-sync");
            // TODO: set another closer alarm?
            return;
        }

        int updateTypeVal = settings.getAutoSyncType();
        FeedUpdater.FeedType feedType = updateTypeVal == 0 ? FeedUpdater.FeedType.Main : null;
        FeedUpdater.UpdateType updateType = updateTypeVal == 0 ? FeedUpdater.UpdateType.Fast : null;

        Context context = getContext();
        // TODO: if the queue sync operation fails, the update feed operation should not be started
        if(settings.isOfflineQueuePending()) {
            ServiceHelper.syncQueue(context, true);
        }
        ServiceHelper.updateFeed(context, feedType, updateType, null, true);
    }

    @Subscribe
    public void onConnectivityChangedEvent(ConnectivityChangedEvent event) {
        Log.d(TAG, "onConnectivityChangedEvent() started");

        networkChanged(false);
    }

    @Subscribe
    public void onOfflineQueueChangedEvent(OfflineQueueChangedEvent event) {
        Log.d(TAG, "onOfflineQueueChangedEvent() started");

        Long queueLength = event.getQueueLength();

        Log.d(TAG, "onOfflineQueueChangedEvent() offline queue length: " + queueLength);

        boolean queueIsEmpty = queueLength != null && queueLength == 0;

        Settings settings = getSettings();
        settings.setOfflineQueuePending(!queueIsEmpty);

        if(event.isTriggeredByOperation() && WallabagConnection.isNetworkAvailable()) {
            ServiceHelper.syncQueue(getContext(), false, true, queueLength);
        } else if(settings.isAutoSyncQueueEnabled()) {
            enableConnectivityChangeReceiver(!queueIsEmpty);
        }
    }

    @Subscribe
    public void onFeedsChangedEvent(FeedsChangedEvent event) {
        Log.d(TAG, "onFeedsChangedEvent() started");

        if(event.isMainFeedChanged()) {
            Log.d(TAG, "onFeedsChangedEvent() triggering update for IconUnreadWidget");
            IconUnreadWidget.triggerWidgetUpdate(getContext());
        }
    }

    @Subscribe(sticky = true)
    public void onUpdateFeedsStartedEvent(UpdateFeedsStartedEvent event) {
        Log.d(TAG, "onUpdateFeedsStartedEvent() started");

        setOperationID(event);

        Context context = getContext();

        FeedUpdater.FeedType feedType = event.getFeedType();
        String detailedMessage;
        if(feedType == null) {
            detailedMessage = context.getString(R.string.notification_updatingAllFeeds);
        } else {
            detailedMessage = context.getString(R.string.notification_updatingSpecificFeed,
                    context.getString(feedType.getLocalizedResourceID()));
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_action_refresh)
                .setContentTitle(context.getString(R.string.notification_updatingFeeds))
                .setContentText(detailedMessage)
                .setOngoing(true);

        getNotificationManager().notify(TAG, NOTIFICATION_ID_UPDATE_FEEDS_ONGOING,
                notificationBuilder.setProgress(0, 0, true).build());
    }

    @Subscribe
    public void onUpdateFeedsFinishedEvent(UpdateFeedsFinishedEvent event) {
        Log.d(TAG, "onUpdateFeedsFinishedEvent() started");

        checkOperationID(event);

        getNotificationManager().cancel(TAG, NOTIFICATION_ID_UPDATE_FEEDS_ONGOING);

        Settings settings = getSettings();

        if(event.getResult().isSuccess()) {
            if(!getSettings().isFirstSyncDone()) {
                settings.setFirstSyncDone(true);
            }

            if(settings.isImageCacheEnabled()) {
                ServiceHelper.fetchImages(getContext());
            }
        }

        emptyOperationID();
    }

    @Subscribe(sticky = true)
    public void onFetchImagesStartedEvent(FetchImagesStartedEvent event) {
        Log.d(TAG, "onFetchImagesStartedEvent() started");

        setOperationID(event);

        Context context = getContext();

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_action_refresh)
                .setContentTitle(context.getString(R.string.notification_downloadingImages))
                .setOngoing(true);

        getNotificationManager().notify(TAG, NOTIFICATION_ID_FETCH_IMAGES_ONGOING,
                notificationBuilder.setProgress(0, 0, true).build());
    }

    @Subscribe
    public void onFetchImagesFinishedEvent(FetchImagesFinishedEvent event) {
        Log.d(TAG, "onFetchImagesFinishedEvent() started");

        checkOperationID(event);

        getNotificationManager().cancel(TAG, NOTIFICATION_ID_FETCH_IMAGES_ONGOING);

        emptyOperationID();
    }

    @Subscribe(sticky = true)
    public void onSyncQueueStartedEvent(SyncQueueStartedEvent event) {
        Log.d(TAG, "onSyncQueueStartedEvent() started");

        setOperationID(event);

        ActionRequest request = event.getRequest();
        if(request.getRequestType() != ActionRequest.RequestType.ManualByOperation
                || (request.getQueueLength() != null && request.getQueueLength() > 1)) {
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getContext())
                    .setSmallIcon(R.drawable.ic_action_refresh)
                    .setContentTitle(getContext().getString(R.string.notification_syncingQueue))
                    .setOngoing(true);

            getNotificationManager().notify(TAG, NOTIFICATION_ID_SYNC_QUEUE_ONGOING,
                    notificationBuilder.setProgress(0, 0, true).build());
        }
    }

    @Subscribe
    public void onSyncQueueFinishedEvent(SyncQueueFinishedEvent event) {
        Log.d(TAG, "onSyncQueueFinishedEvent() started");

        checkOperationID(event);

        getNotificationManager().cancel(TAG, NOTIFICATION_ID_SYNC_QUEUE_ONGOING);

        ActionResult result = event.getResult();
        if((result != null && !result.isSuccess())
                || (event.getQueueLength() == null || event.getQueueLength() > 0)) {
            enableConnectivityChangeReceiver(true);
        }

        emptyOperationID();
    }

    @Subscribe
    public void onActionResultEvent(ActionResultEvent event) {
        Log.d(TAG, "onActionResultEvent() started");

        Notification notification = null;

        ActionRequest request = event.getRequest();
        String actionString = request.getAction().toString();

        Log.d(TAG, "onActionResultEvent() action: " + actionString);

        if(request.getAction() == ActionRequest.Action.AddLink && request.isHeadless()) {
            showToast(getContext().getString(R.string.addLink_success_text), Toast.LENGTH_SHORT);
        }

        ActionResult result = event.getResult();

        if(result != null) {
            if(result.isSuccess()) {
                Log.d(TAG, "onActionResultEvent() result is success");
            } else {
                ActionResult.ErrorType errorType = result.getErrorType();

                Log.d(TAG, "onActionResultEvent() result is not success; errorType: " + errorType);
                Log.d(TAG, "onActionResultEvent() result message: " + result.getMessage());

                switch(errorType) {
                    case Temporary:
                    case NoNetwork:
                        // don't show it to user at all or make it suppressible
                        // optionally schedule auto-retry
                        // TODO: not important: implement
                        break;

                    case IncorrectConfiguration:
                    case IncorrectCredentials: {
                        // notify user -- user must fix something before retry
                        // maybe suppress notification if:
                        //  - the action was not requested by user (that probably implies the second case), or
                        //  - notification was already shown in the past
                        // no auto-retry

                        Settings settings = getSettings();
                        if(settings.isConfigurationOk()) {
                            // TODO: we probably want to automatically test connection

                            settings.setConfigurationOk(false);
                        }

                        if(request.getRequestType() != ActionRequest.RequestType.Auto
                                || !settings.isConfigurationErrorShown()) {
                            settings.setConfigurationErrorShown(true);

                            Context context = getContext();

                            Intent intent = new Intent(context, SettingsActivity.class);
                            PendingIntent contentIntent = PendingIntent.getActivity(
                                    context, 0, intent, 0);

                            NotificationCompat.Builder notificationBuilder =
                                    new NotificationCompat.Builder(context)
                                            .setSmallIcon(R.drawable.ic_warning_24dp)
                                            .setContentTitle(context.getString(R.string.notification_error))
                                            .setContentText(context.getString(
                                                    errorType == ActionResult.ErrorType.IncorrectCredentials
                                                            ? R.string.notification_incorrectCredentials
                                                            : R.string.notification_incorrectConfiguration))
                                            .setContentIntent(contentIntent);

                            notification = notificationBuilder.build();
                        }
                        break;
                    }

                    case Unknown: {
                        // this is undecided yet
                        // show notification + schedule auto-retry
                        // TODO: decide on behavior

                        Context context = getContext();
                        NotificationCompat.Builder notificationBuilder =
                                new NotificationCompat.Builder(context)
                                        .setSmallIcon(R.drawable.ic_warning_24dp)
                                        .setContentTitle(context.getString(R.string.notification_error))
                                        .setContentText(context.getString(R.string.notification_unknownError));

                        if(result.getMessage() != null) {
                            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText(context.getString(R.string.notification_expandedError,
                                            result.getMessage())));
                        }

                        notification = notificationBuilder.build();
                        break;
                    }

                    case NegativeResponse:
                        // server acknowledged the operation but failed/refused to performed it;
                        // detection of such response is not implemented on client yet
                        Log.w(TAG, "onActionResultEvent() got a NegativeResponse; that was not expected");
                        break;
                }
            }
        } else {
            Log.d(TAG, "onActionResultEvent() result is null");
        }

        if(notification != null) {
            Log.d(TAG, "onActionResultEvent() notification is not null; showing it");

            getNotificationManager().notify(TAG, NOTIFICATION_ID_OTHER, notification);
        }
    }

    @Subscribe
    public void onLinkUploadedEvent(LinkUploadedEvent event) {
        Log.d(TAG, "onLinkUploadedEvent() started");

        ActionResult result = event.getResult();
        if(result == null || result.isSuccess()) {
            Log.d(TAG, "onLinkUploadedEvent() result is null or success");

            if(getSettings().isAutoDownloadNewArticlesEnabled()
                    && !getSettings().isOfflineQueuePending()) {
                Log.d(TAG, "onLinkUploadedEvent() autoDlNew enabled, triggering fast update");

                ServiceHelper.updateFeed(getContext(),
                        FeedUpdater.FeedType.Main, FeedUpdater.UpdateType.Fast, null, true);
            }
        }
    }

    private void networkChanged(boolean delayed) {
        if(!delayed && delayedNetworkChangedTask) return;

        if(!WallabagConnection.isNetworkAvailable()) return;

        Settings settings = getSettings();

        if(settings.isOfflineQueuePending() && settings.isConfigurationOk()) {
            if(delayed) {
                Log.d(TAG, "networkChanged() requesting SyncQueue operation");

                ServiceHelper.syncQueue(getContext(), true);

                delayedNetworkChangedTask = false;
            } else {
                getMainHandler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        networkChanged(true);
                    }
                }, 3000);

                delayedNetworkChangedTask = true;
            }
        }
    }

    private void enableConnectivityChangeReceiver(boolean enable) {
        if(getSettings().isAutoSyncQueueEnabled()) {
            Log.d(TAG, "enableConnectivityChangeReceiver() enable connectivity change receiver: " + enable);

            Settings.enableConnectivityChangeReceiver(getContext(), enable);
        }
    }

    private Context getContext() {
        return context;
    }

    private Settings getSettings() {
        if(settings == null) {
            settings = new Settings(getContext());
        }

        return settings;
    }

    private Handler getMainHandler() {
        if(mainHandler == null) {
            mainHandler = new Handler(getContext().getMainLooper());
        }

        return mainHandler;
    }

    private NotificationManager getNotificationManager() {
        if(notificationManager == null) {
            notificationManager = (NotificationManager)getContext()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
        }

        return notificationManager;
    }

    private void showToast(final String text, final int duration) {
        getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getContext(), text, duration).show();
            }
        });
    }

    private void setOperationID(BackgroundOperationEvent bgEvent) {
        setOperationID(bgEvent.getOperationID());
    }

    private void setOperationID(Long operationID) {
        if(currentOperationID != null) {
            throw new RuntimeException("currentOperationID was not reset");
        }

        currentOperationID = operationID;
    }

    private void checkOperationID(BackgroundOperationEvent bgEvent) {
        checkOperationID(bgEvent.getOperationID());
    }

    private void checkOperationID(ActionRequest actionRequest) {
        checkOperationID(actionRequest.getOperationID());
    }

    private void checkOperationID(Long operationID) {
        if(currentOperationID == null && operationID == null) return;

        if((currentOperationID == null) != (operationID == null)
                || !currentOperationID.equals(operationID)) {
            throw new RuntimeException("operationID does not match currentOperationID");
        }
    }

    private void emptyOperationID() {
        currentOperationID = null;
    }

}
