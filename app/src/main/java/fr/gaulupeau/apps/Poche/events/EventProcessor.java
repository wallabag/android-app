package fr.gaulupeau.apps.Poche.events;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
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
import fr.gaulupeau.apps.Poche.service.ServiceHelper;

// TODO: fix getters sync (AFAIK, not so important yet)
public class EventProcessor {

    private static final String TAG = EventProcessor.class.getSimpleName();

    private static final int NOTIFICATION_ID_OTHER = 0;
    private static final int NOTIFICATION_ID_UPDATE_FEEDS_ONGOING = 1;
    private static final int NOTIFICATION_ID_SYNC_QUEUE_ONGOING = 2;

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

    @Subscribe // TODO: check thread
    public void onConnectivityChangedEvent(ConnectivityChangedEvent event) {
        Log.d(TAG, "onConnectivityChangedEvent() started");

        networkChanged(false);
    }

    @Subscribe
    public void onOfflineQueueChangedEvent(OfflineQueueChangedEvent event) {
        Log.d(TAG, "onOfflineQueueChangedEvent() started");

        Long queueLength = event.getQueueLength();

        Log.d(TAG, "onOfflineQueueChangedEvent() offline queue length: " + queueLength);

        getSettings().setBoolean(Settings.PENDING_OFFLINE_QUEUE,
                queueLength == null || queueLength != 0);
    }

    @Subscribe(sticky = true)
    public void onUpdateFeedsStartedEvent(UpdateFeedsStartedEvent event) {
        Log.d(TAG, "onUpdateFeedsStartedEvent() started");

        setOperationID(event);

        FeedUpdater.FeedType feedType = event.getFeedType();

        // TODO: fix notification content
        String detailedMessage;
        if(feedType == null) detailedMessage = "Updating all feeds";
        else detailedMessage = String.format("Updating %s feed", feedType);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getContext())
                .setSmallIcon(R.drawable.ic_action_refresh)
                .setContentTitle("Updating feeds")
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

        emptyOperationID();
    }

    @Subscribe(sticky = true)
    public void onSyncQueueStartedEvent(SyncQueueStartedEvent event) {
        Log.d(TAG, "onSyncQueueStartedEvent() started");

        setOperationID(event);

        // TODO: fix notification content
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getContext())
                .setSmallIcon(R.drawable.ic_action_refresh)
                .setContentTitle("Syncing queue")
//                .setContentText("Syncing queue")
                .setOngoing(true);

        getNotificationManager().notify(TAG, NOTIFICATION_ID_SYNC_QUEUE_ONGOING,
                notificationBuilder.setProgress(0, 0, true).build());
    }

    @Subscribe
    public void onSyncQueueFinishedEvent(SyncQueueFinishedEvent event) {
        Log.d(TAG, "onSyncQueueFinishedEvent() started");

        checkOperationID(event);

        getNotificationManager().cancel(TAG, NOTIFICATION_ID_SYNC_QUEUE_ONGOING);

        emptyOperationID();
    }

    @Subscribe
    public void onActionResultEvent(ActionResultEvent event) {
        Log.d(TAG, "onActionResultEvent() started");

        Notification notification = null;

        ActionRequest request = event.getRequest();
        String actionString = request.getAction().toString();

        Log.d(TAG, "onActionResultEvent() action: " + actionString);

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
                        // schedule auto-retry
                        // TODO: not important: implement
                        // currently "Temporary" may be false-positive -- the error may not be temporary
                        break;
                    case IncorrectConfiguration:
                    case IncorrectCredentials: {
                        // notify user -- user must fix something before retry
                        // maybe suppress notification if:
                        //  - the action was not requested by user (that probably implies the second case), and
                        //  - notification was already shown in the past
                        // no auto-retry
                        // TODO: important: implement the logic
                        if(request.getRequestType() == ActionRequest.RequestType.Manual) {
                            NotificationCompat.Builder notificationBuilder =
                                    new NotificationCompat.Builder(getContext())
                                            .setSmallIcon(R.drawable.ic_stop_24dp)
                                            .setContentTitle("Action failed")
                                            .setContentText(errorType == ActionResult.ErrorType.IncorrectCredentials
                                                            ? "Incorrect credentials"
                                                            : "Incorrect configuration");

                            notification = notificationBuilder.build();
                        }
                        break;
                    }
                    case Unknown: {
                        // this is undecided yet
                        // show notification + schedule auto-retry
                        // TODO: decide on behavior
                        NotificationCompat.Builder notificationBuilder =
                                new NotificationCompat.Builder(getContext())
                                        .setSmallIcon(R.drawable.ic_stop_24dp)
                                        .setContentTitle("Action failed")
                                        .setContentText("Unknown error");

                        if (result.getMessage() != null) {
                            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText("Error: " + result.getMessage()));
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

    private void networkChanged(boolean delayed) {
        if(!delayed && delayedNetworkChangedTask) return;

        if(!WallabagConnection.isNetworkOnline()) return;

        Settings settings = getSettings();

        if(settings.getBoolean(Settings.PENDING_OFFLINE_QUEUE, false)
                /*&& settings.getBoolean(Settings.CONFIGURATION_IS_FINE, false)*/) {
            if(delayed) {
                Log.d(TAG, "onConnectivityChangedEvent() requesting SyncQueue operation");

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
