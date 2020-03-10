package fr.gaulupeau.apps.Poche.events;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.Updater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;
import fr.gaulupeau.apps.Poche.service.AlarmHelper;
import fr.gaulupeau.apps.Poche.service.NotificationActionReceiver;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;
import fr.gaulupeau.apps.Poche.ui.IconUnreadWidget;
import fr.gaulupeau.apps.Poche.ui.preferences.SettingsActivity;

import static fr.gaulupeau.apps.Poche.ui.NotificationsHelper.CHANNEL_ID_DOWNLOADING_ARTICLES;
import static fr.gaulupeau.apps.Poche.ui.NotificationsHelper.CHANNEL_ID_ERRORS;
import static fr.gaulupeau.apps.Poche.ui.NotificationsHelper.CHANNEL_ID_SYNC;

// TODO: fix getters sync (AFAIK, not so important yet)
public class EventProcessor {

    private static final String TAG = EventProcessor.class.getSimpleName();

    private static final int NOTIFICATION_ID_OTHER = 0;
    private static final int NOTIFICATION_ID_SYNC_QUEUE_ONGOING = 1;
    private static final int NOTIFICATION_ID_UPDATE_ARTICLES_ONGOING = 2;
    private static final int NOTIFICATION_ID_SWEEP_DELETED_ARTICLES_ONGOING = 3;
    private static final int NOTIFICATION_ID_DOWNLOAD_FILE_ONGOING = 4;
    private static final int NOTIFICATION_ID_FETCH_IMAGES_ONGOING = 5;

    private static final EnumSet<ArticlesChangedEvent.ChangeType> CHANGE_SET_UNREAD_WIDGET = EnumSet.of(
            ArticlesChangedEvent.ChangeType.UNSPECIFIED,
            ArticlesChangedEvent.ChangeType.ADDED,
            ArticlesChangedEvent.ChangeType.DELETED,
            ArticlesChangedEvent.ChangeType.ARCHIVED,
            ArticlesChangedEvent.ChangeType.UNARCHIVED);

    private Context context;
    private Settings settings;
    private Handler mainHandler;
    private NotificationManager notificationManager;

    private boolean delayedNetworkChangedTask;

    private NotificationCompat.Builder syncQueueNotificationBuilder;
    private NotificationCompat.Builder updateArticlesNotificationBuilder;
    private NotificationCompat.Builder sweepDeletedArticlesNotificationBuilder;
    private NotificationCompat.Builder fetchImagesNotificationBuilder;

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

        Updater.UpdateType updateType = settings.getAutoSyncType() == 0
                ? Updater.UpdateType.FAST : Updater.UpdateType.FULL;

        OperationsHelper.syncAndUpdate(getContext(), settings, updateType, true);
    }

    @Subscribe
    public void onConnectivityChangedEvent(ConnectivityChangedEvent event) {
        Log.d(TAG, "onConnectivityChangedEvent() started");

        if(event.isNoConnectivity()) {
            Log.d(TAG, "onConnectivityChangedEvent() isNoConnectivity is true; ignoring event");
            return;
        }

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
            OperationsHelper.syncQueue(getContext(), false, true);
        } else if(settings.isAutoSyncQueueEnabled()) {
            enableConnectivityChangeReceiver(!queueIsEmpty);
        }
    }

    @Subscribe
    public void onFeedsChangedEvent(FeedsChangedEvent event) {
        Log.d(TAG, "onFeedsChangedEvent() started");

        if(!Collections.disjoint(event.getMainFeedChanges(), CHANGE_SET_UNREAD_WIDGET)) {
            Log.d(TAG, "onFeedsChangedEvent() triggering update for IconUnreadWidget");
            IconUnreadWidget.triggerWidgetUpdate(getContext());
        }
    }

    @Subscribe(sticky = true)
    public void onUpdateArticlesStartedEvent(UpdateArticlesStartedEvent event) {
        Log.d(TAG, "onUpdateArticlesStartedEvent() started");

        Context context = getContext();

        String detailedMessage = context.getString(
                event.getRequest().getUpdateType() != Updater.UpdateType.FAST
                        ? R.string.notification_updatingArticles_full
                        : R.string.notification_updatingArticles_fast);

        detailedMessage = prependAppName(detailedMessage);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
                .setSmallIcon(R.drawable.ic_action_refresh)
                .setContentTitle(context.getString(R.string.notification_updatingArticles))
                .setContentText(detailedMessage)
                .setOngoing(true);

        getNotificationManager().notify(TAG, NOTIFICATION_ID_UPDATE_ARTICLES_ONGOING,
                notificationBuilder.setProgress(0, 0, true).build());

        updateArticlesNotificationBuilder = notificationBuilder;
    }

    @Subscribe
    public void onUpdateArticlesProgressEvent(UpdateArticlesProgressEvent event) {
        Log.d(TAG, "onUpdateArticlesProgressEvent() started");

        if(updateArticlesNotificationBuilder != null
                && event.getCurrent() != 0 /* don't show empty progressbar */) {
            getNotificationManager().notify(TAG, NOTIFICATION_ID_UPDATE_ARTICLES_ONGOING,
                    updateArticlesNotificationBuilder
                            .setProgress(event.getTotal(), event.getCurrent(), false)
                            .build());
        }
    }

    @Subscribe
    public void onUpdateArticlesFinishedEvent(UpdateArticlesFinishedEvent event) {
        Log.d(TAG, "onUpdateArticlesFinishedEvent() started");

        getNotificationManager().cancel(TAG, NOTIFICATION_ID_UPDATE_ARTICLES_ONGOING);

        updateArticlesNotificationBuilder = null;
    }

    @Subscribe(sticky = true)
    public void onSweepDeletedArticlesStartedEvent(SweepDeletedArticlesStartedEvent event) {
        Log.d(TAG, "onSweepDeletedArticlesStartedEvent() started");

        Context context = getContext();

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
                .setSmallIcon(R.drawable.ic_action_refresh)
                .setContentTitle(context.getString(R.string.notification_sweepingDeletedArticles))
                .setOngoing(true);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            notificationBuilder.setContentText(context.getString(R.string.app_name));
        }

        getNotificationManager().notify(TAG, NOTIFICATION_ID_SWEEP_DELETED_ARTICLES_ONGOING,
                notificationBuilder.setProgress(0, 0, true).build());

        sweepDeletedArticlesNotificationBuilder = notificationBuilder;
    }

    @Subscribe
    public void onSweepDeletedArticlesProgressEvent(SweepDeletedArticlesProgressEvent event) {
        Log.d(TAG, "onSweepDeletedArticlesProgressEvent() started");

        if(sweepDeletedArticlesNotificationBuilder != null
                && event.getCurrent() != 0 /* don't show empty progressbar */) {
            getNotificationManager().notify(TAG, NOTIFICATION_ID_SWEEP_DELETED_ARTICLES_ONGOING,
                    sweepDeletedArticlesNotificationBuilder
                            .setProgress(event.getTotal(), event.getCurrent(), false)
                            .build());
        }
    }

    @Subscribe
    public void onSweepDeletedArticlesFinishedEvent(SweepDeletedArticlesFinishedEvent event) {
        Log.d(TAG, "onSweepDeletedArticlesFinishedEvent() started");

        getNotificationManager().cancel(TAG, NOTIFICATION_ID_SWEEP_DELETED_ARTICLES_ONGOING);

        sweepDeletedArticlesNotificationBuilder = null;
    }

    @Subscribe
    public void onFetchImagesProgressEvent(FetchImagesProgressEvent event) {
        Log.d(TAG, "onFetchImagesProgressEvent() started");

        if(fetchImagesNotificationBuilder == null) {
            Context context = getContext();

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
                    .setSmallIcon(R.drawable.ic_action_refresh)
                    .setContentTitle(context.getString(R.string.notification_downloadingImages))
                    .setOngoing(true);

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                notificationBuilder.setContentText(context.getString(R.string.app_name));
            }

            fetchImagesNotificationBuilder = notificationBuilder;
        }

        if(event.getCurrent() == 0) { // show indeterminate progressbar instead of empty one
            fetchImagesNotificationBuilder.setProgress(0, 0, true);
        } else {
            fetchImagesNotificationBuilder.setProgress(event.getTotal(), event.getCurrent(), false);
        }

        getNotificationManager().notify(TAG, NOTIFICATION_ID_FETCH_IMAGES_ONGOING,
                fetchImagesNotificationBuilder.build());
    }

    @Subscribe
    public void onFetchImagesFinishedEvent(FetchImagesFinishedEvent event) {
        Log.d(TAG, "onFetchImagesFinishedEvent() started");

        getNotificationManager().cancel(TAG, NOTIFICATION_ID_FETCH_IMAGES_ONGOING);

        fetchImagesNotificationBuilder = null;
    }

    @Subscribe
    public void onSyncQueueProgressEvent(SyncQueueProgressEvent event) {
        Log.d(TAG, "onSyncQueueProgressEvent() started");

        boolean showNotification = false;

        ActionRequest request = event.getRequest();

        int total = event.getTotal();

        if(total > 1) {
            showNotification = true;
        } else if(total == 1
                && request.getRequestType() != ActionRequest.RequestType.MANUAL_BY_OPERATION) {
            showNotification = true;
        }

        if(showNotification) {
            NotificationCompat.Builder notificationBuilder = syncQueueNotificationBuilder;

            if(notificationBuilder == null) {
                Context context = getContext();

                notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
                        .setSmallIcon(R.drawable.ic_action_refresh)
                        .setContentTitle(getContext().getString(R.string.notification_syncingQueue))
                        .setOngoing(true);

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    notificationBuilder.setContentText(context.getString(R.string.app_name));
                }

                syncQueueNotificationBuilder = notificationBuilder;
            }

            getNotificationManager().notify(TAG, NOTIFICATION_ID_SYNC_QUEUE_ONGOING,
                    notificationBuilder.setProgress(total, event.getCurrent(), false).build());
        }
    }

    @Subscribe
    public void onSyncQueueFinishedEvent(SyncQueueFinishedEvent event) {
        Log.d(TAG, "onSyncQueueFinishedEvent() started");

        getNotificationManager().cancel(TAG, NOTIFICATION_ID_SYNC_QUEUE_ONGOING);

        syncQueueNotificationBuilder = null;

        ActionResult result = event.getResult();
        if((result != null && !result.isSuccess())
                || (event.getQueueLength() == null || event.getQueueLength() > 0)) {
            enableConnectivityChangeReceiver(true);
        }
    }

    // TODO: better downloading notification
    @Subscribe(sticky = true)
    public void onDownloadFileStartedEvent(DownloadFileStartedEvent event) {
        Log.d(TAG, "onDownloadFileStartedEvent() started");

        Context context = getContext();

        String formatString = event.getRequest().getDownloadFormat().toString();

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADING_ARTICLES)
                .setContentTitle(context.getString(R.string.downloadAsFilePathStart))
                .setContentText(context.getString(R.string.downloadAsFileProgress, formatString))
                .setSmallIcon(R.drawable.ic_file_download_24dp)
                .setOngoing(true);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(
                context.getString(R.string.downloadAsFileProgressDetail,
                        event.getArticle().getTitle().replaceAll("[^a-zA-Z0-9.-]", " "),
                        formatString));
        notificationBuilder.setStyle(inboxStyle);

        getNotificationManager().notify(TAG, NOTIFICATION_ID_DOWNLOAD_FILE_ONGOING,
                notificationBuilder.setProgress(1, 0, true).build());
    }

    @Subscribe
    public void onDownloadFileFinishedEvent(DownloadFileFinishedEvent event) {
        Log.d(TAG, "onDownloadFileFinishedEvent() started");

        ActionResult result = event.getResult();
        if(result == null || result.isSuccess()) {
            Context context = getContext();

            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(context,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    event.getFile());
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    event.getRequest().getDownloadFormat().toString().toLowerCase(Locale.US));
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, 0);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADING_ARTICLES)
                    .setContentTitle(context.getString(R.string.downloadAsFileArticleDownloaded))
                    .setContentText(context.getString(R.string.downloadAsFileTouchToOpen))
                    .setSmallIcon(R.drawable.ic_file_download_24dp)
                    .setContentIntent(contentIntent);

            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(
                    context.getString(R.string.downloadAsFileArticleDownloadedDetail,
                            event.getArticle().getTitle().replaceAll("[^a-zA-Z0-9.-]", " ")));
            notificationBuilder.setStyle(inboxStyle);

            getNotificationManager().notify(TAG, NOTIFICATION_ID_DOWNLOAD_FILE_ONGOING,
                    notificationBuilder.build());
        } else {
            getNotificationManager().cancel(TAG, NOTIFICATION_ID_DOWNLOAD_FILE_ONGOING);
        }
    }

    @Subscribe
    public void onActionResultEvent(ActionResultEvent event) {
        Log.d(TAG, "onActionResultEvent() started");

        Notification notification = null;

        ActionRequest request = event.getRequest();
        String actionString = request.getAction().toString();

        Log.d(TAG, "onActionResultEvent() action: " + actionString);

        ActionResult result = event.getResult();

        boolean success = true;

        if(result != null) {
            if(!result.isSuccess()) success = false;
            Log.d(TAG, "onActionResultEvent() result is success: " + success);
        } else {
            Log.d(TAG, "onActionResultEvent() result is null");
        }

        if(success) {
            ActionRequest nextRequest = request.getNextRequest();

            if(nextRequest != null) {
                Log.d(TAG, "onActionResultEvent() starting nextRequest with action: "
                        + nextRequest.getAction());

                ServiceHelper.startService(getContext(), nextRequest);
            }
        } else {
            ActionResult.ErrorType errorType = result.getErrorType();

            Log.d(TAG, "onActionResultEvent() result is not success; errorType: " + errorType);
            Log.d(TAG, "onActionResultEvent() result message: " + result.getMessage());

            switch(errorType) {
                case TEMPORARY:
                case NO_NETWORK:
                    // don't show it to user at all or make it suppressible
                    // optionally schedule auto-retry
                    // TODO: not important: implement
                    break;

                case INCORRECT_CONFIGURATION:
                case INCORRECT_CREDENTIALS: {
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

                    if(request.getRequestType() != ActionRequest.RequestType.AUTO
                            || !settings.isConfigurationErrorShown()) {
                        settings.setConfigurationErrorShown(true);

                        Context context = getContext();

                        Intent intent = new Intent(context, SettingsActivity.class);
                        PendingIntent contentIntent = PendingIntent.getActivity(
                                context, 0, intent, 0);

                        String detailedText = context.getString(
                                errorType == ActionResult.ErrorType.INCORRECT_CREDENTIALS
                                        ? R.string.notification_incorrectCredentials
                                        : R.string.notification_incorrectConfiguration);

                        detailedText = prependAppName(detailedText);

                        NotificationCompat.Builder notificationBuilder =
                                new NotificationCompat.Builder(context, CHANNEL_ID_ERRORS)
                                        .setSmallIcon(R.drawable.ic_warning_24dp)
                                        .setContentTitle(context.getString(R.string.notification_error))
                                        .setContentText(detailedText)
                                        .setContentIntent(contentIntent);

                        notification = notificationBuilder.build();
                    }
                    break;
                }

                case SERVER_ERROR:
                case UNKNOWN: {
                    // this is undecided yet
                    // show notification + schedule auto-retry
                    // TODO: decide on behavior

                    boolean serverError = errorType == ActionResult.ErrorType.SERVER_ERROR;

                    Context context = getContext();

                    String detailedText = context.getString(serverError
                            ? R.string.notification_serverError
                            : R.string.notification_unknownError);
                    detailedText = prependAppName(detailedText);

                    NotificationCompat.Builder notificationBuilder =
                            new NotificationCompat.Builder(context, CHANNEL_ID_ERRORS)
                                    .setSmallIcon(R.drawable.ic_warning_24dp)
                                    .setContentTitle(context.getString(serverError
                                            ? R.string.notification_serverError
                                            : R.string.notification_error))
                                    .setContentText(detailedText);

                    String extra = "";
                    if(!TextUtils.isEmpty(result.getMessage())) {
                        extra += result.getMessage() + "\n";
                    }
                    if(result.getException() != null) {
                        StringWriter sw = new StringWriter();

                        sw.append(context.getString(R.string.notification_stacktrace)).append("\n");
                        result.getException().printStackTrace(new PrintWriter(sw));

                        extra += sw.toString();
                    }
                    if(!TextUtils.isEmpty(extra)) {
                        notificationBuilder.setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(context.getString(R.string.notification_expandedError, extra)));

                        extra = detailedText + "\n" + extra;

                        PendingIntent copyIntent = NotificationActionReceiver
                                .getCopyToClipboardPendingIntent(context,
                                        context.getString(R.string.notification_clipboardLabel), extra);
                        notificationBuilder.addAction(0,
                                context.getString(R.string.notification_copyToClipboard), copyIntent);
                    }

                    notification = notificationBuilder.build();
                    break;
                }

                case NOT_FOUND:
                    Log.w(TAG, "onActionResultEvent() got a NOT_FOUND");
                    break;

                case NEGATIVE_RESPONSE:
                    // server acknowledged the operation but failed/refused to performed it;
                    // detection of such response is not implemented on client yet
                    Log.w(TAG, "onActionResultEvent() got a NEGATIVE_RESPONSE; that was not expected");
                    break;
            }
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

            Settings settings = getSettings();
            if(settings.isAutoDownloadNewArticlesEnabled()
                    && !settings.isOfflineQueuePending()) {
                Log.d(TAG, "onLinkUploadedEvent() autoDlNew enabled, triggering fast update");

                OperationsHelper.updateArticles(getContext(), settings,
                        Updater.UpdateType.FAST, true, null);
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

                OperationsHelper.syncQueue(getContext(), true);

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

    private String prependAppName(String s) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // notifications in Android before Android Nougat do not contain their source app,
            // therefore we add it here manually
            s = getContext().getString(R.string.app_name) + " · " + s;
        }

        return s;
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

}
