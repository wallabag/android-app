package fr.gaulupeau.apps.Poche;

import android.app.Application;

import com.facebook.stetho.Stetho;

import org.conscrypt.Conscrypt;
import org.greenrobot.eventbus.EventBus;

import java.security.Security;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.events.EventProcessor;
import fr.gaulupeau.apps.Poche.ui.NotificationsHelper;

public class App extends Application {

    private static App instance;

    private Settings settings;

    @Override
    public void onCreate() {
        super.onCreate();

        Security.insertProviderAt(Conscrypt.newProvider(), 1);

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

        NotificationsHelper.createNotificationChannels(this);

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
