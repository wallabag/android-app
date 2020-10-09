package fr.gaulupeau.apps.Poche.service;

import android.app.Notification;

import androidx.core.app.NotificationCompat;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.ui.NotificationsHelper;

public class SecondaryService extends TaskService {

    public SecondaryService() {
        super(SecondaryService.class.getSimpleName());
    }

    @Override
    protected int getForegroundNotificationId() {
        return 101;
    }

    @Override
    protected Notification getForegroundNotification() {
        NotificationsHelper.initNotificationChannels();

        return new NotificationCompat.Builder(
                this, NotificationsHelper.CHANNEL_ID_BACKGROUND_OPERATIONS)
                .setSmallIcon(R.drawable.ic_action_refresh)
                .setContentTitle(getString(R.string.notification_backgroundOperations))
                .build();
    }

}
