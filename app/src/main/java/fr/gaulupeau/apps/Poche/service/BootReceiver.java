package fr.gaulupeau.apps.Poche.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import fr.gaulupeau.apps.Poche.events.BootCompletedEvent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BootReceiver", "Boot completed");

            EventBus.getDefault().post(new BootCompletedEvent());
        }
    }

}
