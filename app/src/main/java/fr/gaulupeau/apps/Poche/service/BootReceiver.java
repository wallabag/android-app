package fr.gaulupeau.apps.Poche.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import fr.gaulupeau.apps.Poche.events.BootCompletedEvent;
import fr.gaulupeau.apps.Poche.events.EventHelper;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BootReceiver", "Boot completed");

            EventHelper.postEvent(new BootCompletedEvent());
        }
    }

}
