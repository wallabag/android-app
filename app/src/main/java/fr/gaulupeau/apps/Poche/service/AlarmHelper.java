package fr.gaulupeau.apps.Poche.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import static fr.gaulupeau.apps.Poche.data.Settings.enableComponent;

public class AlarmHelper {

    public static void setAlarm(Context context, long interval, boolean enableReceiver) {
        // TODO: maybe use last update time to calculate initial delay

        if(enableReceiver) {
            enableComponent(context, BootReceiver.class, true);
            enableComponent(context, AlarmReceiver.class, true);
        }

        getAlarmManager(context).setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + interval/2,
                interval,
                getAlarmIntent(context));
    }

    public static void unsetAlarm(Context context, boolean disableReceiver) {
        getAlarmManager(context).cancel(getAlarmIntent(context));

        if(disableReceiver) {
            enableComponent(context, BootReceiver.class, false);
            enableComponent(context, AlarmReceiver.class, false);
        }
    }

    public static void updateAlarmInterval(Context context, long interval) {
        unsetAlarm(context, false);
        setAlarm(context, interval, false);
    }

    public static PendingIntent getAlarmIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    private static AlarmManager getAlarmManager(Context context) {
        return (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    }

}
