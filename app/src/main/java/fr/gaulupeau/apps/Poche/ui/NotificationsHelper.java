package fr.gaulupeau.apps.Poche.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;

public class NotificationsHelper {

    public static final String CHANNEL_GROUP_ID_TTS = "tts";
    public static final String CHANNEL_GROUP_ID_SYNC = "sync";
    public static final String CHANNEL_GROUP_ID_OTHER = "other";

    public static final String CHANNEL_ID_TTS = "fr.gaulupeau.apps.Poche.tts";
    public static final String CHANNEL_ID_SYNC = "sync";
    public static final String CHANNEL_ID_DOWNLOADING_ARTICLES = "downloading_articles";
    public static final String CHANNEL_ID_BACKGROUND_OPERATIONS = "background_operations";
    public static final String CHANNEL_ID_ERRORS = "errors";

    private static boolean notificationChannelsInitialized;

    public static void initNotificationChannels() {
        if (notificationChannelsInitialized) return;

        synchronized (NotificationsHelper.class) {
            if (notificationChannelsInitialized) return;

            createNotificationChannels(App.getInstance());

            notificationChannelsInitialized = true;
        }
    }

    private static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

            NotificationChannelGroup channelGroupTts = new NotificationChannelGroup(
                    CHANNEL_GROUP_ID_TTS,
                    context.getString(R.string.notification_channel_group_name_tts));
            notificationManager.createNotificationChannelGroup(channelGroupTts);

            NotificationChannelGroup channelGroupSync = new NotificationChannelGroup(
                    CHANNEL_GROUP_ID_SYNC,
                    context.getString(R.string.notification_channel_group_name_sync));
            notificationManager.createNotificationChannelGroup(channelGroupSync);

            NotificationChannelGroup channelGroupOther = new NotificationChannelGroup(
                    CHANNEL_GROUP_ID_OTHER,
                    context.getString(R.string.notification_channel_group_name_other));
            notificationManager.createNotificationChannelGroup(channelGroupOther);

            List<NotificationChannel> channels = new ArrayList<>();

            NotificationChannel channel;

            channel = new NotificationChannel(
                    CHANNEL_ID_TTS, context.getString(R.string.notification_channel_name_tts),
                    NotificationManager.IMPORTANCE_NONE
            );
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setGroup(channelGroupTts.getId());
            channels.add(channel);

            channel = new NotificationChannel(
                    CHANNEL_ID_SYNC, context.getString(R.string.notification_channel_name_sync),
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setGroup(channelGroupSync.getId());
            channels.add(channel);

            channel = new NotificationChannel(
                    CHANNEL_ID_DOWNLOADING_ARTICLES, context.getString(R.string.notification_channel_name_downloading_articles),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setGroup(channelGroupSync.getId());
            channels.add(channel);

            channel = new NotificationChannel(
                    CHANNEL_ID_BACKGROUND_OPERATIONS, context.getString(R.string.notification_channel_name_background_operations),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setGroup(channelGroupOther.getId());
            channels.add(channel);

            channel = new NotificationChannel(
                    CHANNEL_ID_ERRORS, context.getString(R.string.notification_channel_name_error),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setGroup(channelGroupOther.getId());
            channels.add(channel);

            notificationManager.createNotificationChannels(channels);
        }
    }

}
