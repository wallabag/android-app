package fr.gaulupeau.apps.Poche.data;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.ui.HttpSchemeHandlerActivity;

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

    public static final String CONFIGURATION_IS_OK = "configuration_is_ok";

    public static final String PENDING_OFFLINE_QUEUE = "offline_queue.pending";

    public static final String AUTOSYNC_ENABLED = "autosync.enabled";
    public static final String AUTOSYNC_INTERVAL = "autosync.interval";
    public static final String AUTOSYNC_TYPE = "autosync.type";

    public static final int WALLABAG_WIDGET_MAX_UNREAD_COUNT = 999;

    private SharedPreferences pref;

    public static long autoSyncOptionIndexToInterval(int index) {
        switch(index) {
            case 0: return AlarmManager.INTERVAL_FIFTEEN_MINUTES;
            case 1: return AlarmManager.INTERVAL_HALF_HOUR;
            case 2: return AlarmManager.INTERVAL_HOUR;
            case 3: return AlarmManager.INTERVAL_HALF_DAY;
            default: return AlarmManager.INTERVAL_DAY;
        }
    }

    public static int autoSyncIntervalToOptionIndex(long interval) {
        switch((int)interval) {
            case (int)AlarmManager.INTERVAL_FIFTEEN_MINUTES: return 0;
            case (int)AlarmManager.INTERVAL_HALF_HOUR: return 1;
            case (int)AlarmManager.INTERVAL_HOUR: return 2;
            case (int)AlarmManager.INTERVAL_HALF_DAY: return 3;
            case (int)AlarmManager.INTERVAL_DAY: return 4;
        }

        return -1;
    }

    public Settings(Context context) {
        pref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setString(String key, String value) {
        pref.edit().putString(key, value).apply();
    }

    public void setInt(String key, int value) {
        pref.edit().putInt(key, value).apply();
    }

    public void setLong(String key, long value) {
        pref.edit().putLong(key, value).apply();
    }

    public void setFloat(String key, float value) {
        pref.edit().putFloat(key, value).apply();
    }

    public void setBoolean(String key, boolean value) {
        pref.edit().putBoolean(key, value).apply();
    }

    public String getUrl() {
        return pref.getString(URL, null);
    }

    public boolean isConfigurationOk() {
        return pref.getBoolean(CONFIGURATION_IS_OK, false);
    }

    public void setConfigurationOk(boolean ok) {
        setBoolean(CONFIGURATION_IS_OK, ok);
    }

    public String getString(String key) {
        return pref.getString(key, null);
    }

    public String getString(String key, String defValue) {
        return pref.getString(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return pref.getInt(key, defValue);
    }

    public long getLong(String key, long defValue) {
        return pref.getLong(key, defValue);
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
