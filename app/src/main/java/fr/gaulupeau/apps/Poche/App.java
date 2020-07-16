package fr.gaulupeau.apps.Poche;

import android.app.Application;

import com.facebook.stetho.Stetho;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.data.Settings;

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
    }

}
