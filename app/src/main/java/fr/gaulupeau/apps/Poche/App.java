package fr.gaulupeau.apps.Poche;

import android.app.Application;

import com.facebook.stetho.Stetho;

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

        Settings.init(this);
        settings = new Settings(this);
        settings.initPreferences();

        DbConnection.setContext(this);

        new EventProcessor(this).start();

        instance = this;
    }

    public Settings getSettings() {
        return settings;
    }

    public static App getInstance() {
        return instance;
    }

}
