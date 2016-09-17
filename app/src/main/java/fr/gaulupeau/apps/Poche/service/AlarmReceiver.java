package fr.gaulupeau.apps.Poche.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.greenrobot.eventbus.EventBus;

import fr.gaulupeau.apps.Poche.events.AlarmReceivedEvent;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        EventBus.getDefault().post(new AlarmReceivedEvent());
    }

}
