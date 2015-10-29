package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.content.SharedPreferences;

import fr.gaulupeau.apps.InThePoche.BuildConfig;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class Settings {

    private static final String PREFS_NAME = "InThePoche"; // keeping prefname for backwards compat

    public static final String URL = "pocheUrl";
    public static final String USER_ID = "APIUsername";
    public static final String TOKEN = "APIToken";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String VERSION_CODE = "version_code";

    private SharedPreferences pref;

    public Settings(Context context) {
        pref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setString(String key, String value) {
        pref.edit().putString(key, value).commit();
    }

    public String getUrl() {
        return pref.getString(URL, null);
    }

    public String getKey(String key) {
        return pref.getString(key, null);
    }

    public void setAppVersion(int versionCode) {
        pref.edit().putInt(VERSION_CODE, versionCode).commit();
    }

    public int getPrevAppVersion() {
        return pref.getInt(VERSION_CODE, BuildConfig.VERSION_CODE);
    }

    public boolean hasUpdateChecher() {
        return pref.getInt("update_checker", -1) != -1;
    }
}
