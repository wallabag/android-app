package fr.gaulupeau.apps.Poche.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class Settings {

    private static final String PREFS_NAME = "InThePoche"; // keeping prefname for backwards compat

    public static final String URL = "pocheUrl";
    public static final String USER_ID = "APIUsername";
    public static final String TOKEN = "APIToken";
    public static final String ALL_CERTS = "all_certs";
    public static final String FONT_SIZE = "font_size";
    public static final String SERIF_FONT = "serif_font";
    public static final String LIST_LIMIT = "list_limit";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String HTTP_AUTH_USERNAME = "http_auth_username";
    public static final String HTTP_AUTH_PASSWORD = "http_auth_password";
    public static final String THEME = "theme";
    public static final String CONFIGURE_OPTIONAL_DIALOG_SHOWN = "configure_optional_dialog_shown";
    public static final String WALLABAG_VERSION = "wallabag_version";
    public static final String WALLABAG_LOGIN_FORM_V1 = "<form method=\"post\" action=\"?login\" name=\"loginform\">";
    public static final String WALLABAG_LOGOUT_LINK_V1 = "href=\"./?logout\"";
    public static final String WALLABAG_LOGIN_FORM_V2 = "<form action=\"/login_check\" method=\"post\" name=\"loginform\">";
    public static final String WALLABAG_LOGOUT_LINK_V2 = "href=\"/logout\"";

    private SharedPreferences pref;

    public Settings(Context context) {
        pref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setString(String key, String value) {
        pref.edit().putString(key, value).commit();
    }

    public void setInt(String key, int value) {
        pref.edit().putInt(key, value).commit();
    }

    public void setBoolean(String key, boolean value) {
        pref.edit().putBoolean(key, value).commit();
    }

    public String getUrl() {
        return pref.getString(URL, null);
    }

    public String getKey(String key) {
        return pref.getString(key, null);
    }

    public String getString(String key, String defValue) {
        return pref.getString(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return pref.getInt(key, defValue);
    }

    public boolean getBoolean(String key, boolean defValue) {
        return pref.getBoolean(key, defValue);
    }

}
