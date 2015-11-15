package fr.gaulupeau.apps.Poche;

import android.app.Application;

import com.facebook.stetho.Stetho;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;

/**
 * @author Victor Häggqvist
 * @since 10/19/15
 */
public class App extends Application {

    private static App instance;

    private Settings settings;

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG)
            Stetho.initializeWithDefaults(this);

        DbConnection.setContext(this);
        settings = new Settings(this);

        WallabagConnection.init(this);

        instance = this;
    }

    public Settings getSettings() {
        return settings;
    }

    public static App getInstance() {
        return instance;
    }

}
