package fr.gaulupeau.apps.Poche.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import fr.gaulupeau.apps.Poche.events.AlarmReceivedEvent;
import fr.gaulupeau.apps.Poche.events.EventHelper;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        EventHelper.postEvent(new AlarmReceivedEvent());
    }

}
