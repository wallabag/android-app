package fr.gaulupeau.apps.Poche.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.events.ActionResultEvent;
import fr.gaulupeau.apps.Poche.events.BackgroundOperationEvent;
import fr.gaulupeau.apps.Poche.events.UpdateFeedsFinishedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateFeedsStartedEvent;
import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class EventProcessor {

    private static final String TAG = EventProcessor.class.getSimpleName();

    private static final int NOTIFICATION_ID_UPDATE_FEEDS_ONGOING = 1;

    private Context context;
    private Handler handler;
    private NotificationManager notificationManager;

    private Long currentOperationID; // TODO: replace with ActionRequest?

    public EventProcessor(Context context) {
        this.context = context;
    }

    public void start() {
        EventBus.getDefault().register(this);
    }

    public void stop() {
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(sticky = true)
    public void onUpdateFeedsStartedEvent(UpdateFeedsStartedEvent event) {
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
        checkOperationID(event);

        getNotificationManager().cancel(TAG, NOTIFICATION_ID_UPDATE_FEEDS_ONGOING);

        emptyOperationID();
    }

    @Subscribe
    public void onActionResultEvent(ActionResultEvent event) {
        Notification notification = null;

        ActionRequest request = event.getRequest();
        String actionString = request.getAction().toString();

        ActionResult result = event.getResult();

        // TODO: fix: debug only implementation

        if(result != null) {
            if(result.isSuccess()) {
                NotificationCompat.Builder notificationBuilder =
                        new NotificationCompat.Builder(getContext())
                                .setSmallIcon(R.drawable.ic_done_24dp)
                                .setContentTitle("Action finished")
                                .setContentText(actionString)
                                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                                        String.format("Action \"%s\" finished", actionString)));

                notification = notificationBuilder.build();

                showToast("Operation completed", Toast.LENGTH_LONG);
            } else {
                // TODO: implement
                ActionResult.ErrorType errorType = result.getErrorType();
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
                        new NotificationCompat.Builder(getContext())
                                .setSmallIcon(R.drawable.ic_stop_24dp)
                                .setContentTitle("Action failed")
                                .setContentText(actionString);

                if(result.getMessage() != null) {
                    notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(
                            String.format("Action: %s\nError: %s", actionString, result.getMessage())));

                    showToast(String.format(
                            "\"%s\" error detected; message: \"%s\"",
                            errorType, result.getMessage()), Toast.LENGTH_LONG);
                } else {
                    showToast(String.format("\"%s\" error detected", errorType), Toast.LENGTH_LONG);
                }

                notification = notificationBuilder.build();
            }
        }

        if(notification != null) {
            getNotificationManager().notify(TAG, 0, notification);
        }
    }

    private Context getContext() {
        return context;
    }

    private Handler getHandler() {
        if(handler == null) {
            handler = new Handler(getContext().getMainLooper());
        }

        return handler;
    }

    private NotificationManager getNotificationManager() {
        if(notificationManager == null) {
            notificationManager = (NotificationManager)getContext()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
        }

        return notificationManager;
    }

    private void showToast(final String text, final int duration) {
        getHandler().post(new Runnable() {
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
