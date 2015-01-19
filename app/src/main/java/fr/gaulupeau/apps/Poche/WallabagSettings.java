package fr.gaulupeau.apps.Poche;

import android.content.Context;
import android.content.SharedPreferences;

import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;

/**
 * Created by kevinmeyer on 19/01/15.
 */
public class WallabagSettings {
    public String wallabagURL;
    public String userID;
    public String userToken;

    private static final String keyWallabagURL = "pocheUrl";
    private static final String keyUserID = "APIUsername";
    private static final String keyUserToken = "APIToken";

    private SharedPreferences preferences;

    public WallabagSettings(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, 0);
    }

    public static WallabagSettings settingsFromDisk(Context context) {
        WallabagSettings settings = new WallabagSettings(context);
        settings.load();
        return settings;
    }

    public boolean isValid() {
        return !(wallabagURL.equals("http://")) && !(userID.equals("")) && !(userToken.equals(""));
    }

    public void load() {
        wallabagURL = preferences.getString(keyWallabagURL, "http://");
        userID = preferences.getString(keyUserID, "");
        userToken = preferences.getString(keyUserToken, "");
    }

    public void save() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(keyWallabagURL, wallabagURL);
        editor.putString(keyUserID, userID);
        editor.putString(keyUserToken, userToken);
        editor.commit();
    }
}
