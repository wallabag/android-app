package fr.gaulupeau.apps.Poche;

import android.app.Application;

import com.facebook.stetho.Stetho;

import org.greenrobot.eventbus.EventBus;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.events.EventProcessor;

public class App extends Application {

    private static App instance;

    private Settings settings;

    @Override
    public void onCreate() {
        super.onCreate();

        if(BuildConfig.DEBUG) Stetho.initializeWithDefaults(this);

        EventBus.builder()
                .sendNoSubscriberEvent(false)
                .sendSubscriberExceptionEvent(false)
                .throwSubscriberException(BuildConfig.DEBUG)
                .addIndex(new EventBusIndex())
                .installDefaultEventBus();

        Settings.init(this);
        settings = new Settings(this);
        settings.initPreferences();

        new EventProcessor(this).start();

        DbConnection.setContext(this);

        instance = this;
    }

    public Settings getSettings() {
        return settings;
    }

    public static App getInstance() {
        return instance;
    }

}
