package fr.gaulupeau.apps.Poche.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.ui.HttpSchemeHandlerActivity;

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
    public static final String CUSTOM_SSL_SETTINGS = "custom_ssl_settings";
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
    public static final String TTS_VISIBLE = "tts.visible";
    public static final String TTS_OPTIONS_VISIBLE = "tts.options.visible";
    public static final String TTS_SPEED = "tts.speed";
    public static final String TTS_PITCH = "tts.pitch";
    public static final String TTS_ENGINE = "tts.engine";
    public static final String TTS_VOICE = "tts.voice";
    public static final String TTS_LANGUAGE_VOICE = "tts.language_voice:";
    public static final String TTS_AUTOPLAY_NEXT = "tts.autoplay_next";
    public static final int WALLABAG_WIDGET_MAX_UNREAD_COUNT = 999;

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

    public void setFloat(String key, float value) {
        pref.edit().putFloat(key, value).commit();
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

    public float getFloat(String key, float defValue) {
        return pref.getFloat(key, defValue);
    }

    public boolean getBoolean(String key, boolean defValue) {
        return pref.getBoolean(key, defValue);
    }

    public boolean contains(String key) {
        return pref.contains(key);
    }

    public boolean isHandlingHttpScheme() {
        return App.getInstance().getPackageManager()
                .getComponentEnabledSetting(getHttpSchemeHandlingComponent())
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public void setHandleHttpScheme(boolean handleHttpScheme) {
        if(handleHttpScheme == isHandlingHttpScheme()) return;

        int flag = (handleHttpScheme ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        App.getInstance().getPackageManager().setComponentEnabledSetting(
                getHttpSchemeHandlingComponent(), flag, PackageManager.DONT_KILL_APP);
    }

    private ComponentName getHttpSchemeHandlingComponent() {
        return new ComponentName(App.getInstance(), HttpSchemeHandlerActivity.class);
    }

}
