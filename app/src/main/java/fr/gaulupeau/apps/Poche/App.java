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

    private static class SettingsHolder {
        static final Settings SETTINGS = createSettings();

        static Settings createSettings() {
            Settings settings = new Settings(instance);
            settings.initPreferences();

            return settings;
        }
    }

    public static App getInstance() {
        return instance;
    }

    public static Settings getSettings() {
        return SettingsHolder.SETTINGS;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this);
        }

        EventBus.builder()
                .sendNoSubscriberEvent(false)
                .sendSubscriberExceptionEvent(false)
                .throwSubscriberException(BuildConfig.DEBUG)
                .addIndex(new EventBusIndex())
                .installDefaultEventBus();

        new EventProcessor(this).start();

        DbConnection.setContext(this);
    }

}
