package fr.gaulupeau.apps.Poche.data;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.network.ConnectivityChangeReceiver;
import fr.gaulupeau.apps.Poche.service.WallabagJobService;
import fr.gaulupeau.apps.Poche.ui.HttpSchemeHandlerActivity;
import fr.gaulupeau.apps.Poche.ui.Themes;
import fr.gaulupeau.apps.Poche.ui.preferences.ConnectionWizardActivity;

public class Settings {

    private static final String TAG = Settings.class.getSimpleName();

    private static final int PREFERENCES_VERSION = 1;

    private static Map<String, Integer> preferenceKeysMap;

    private Context context;
    private SharedPreferences pref;

    public static void init(Context c) {
        preferenceKeysMap = new HashMap<>();

        try {
            for(Field field: R.string.class.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if(Modifier.isStatic(modifiers)
                        && !Modifier.isPrivate(modifiers)
                        && field.getType().equals(int.class)) {
                    try {
                        if(field.getName().startsWith("pref_key_")) {
                            int resID = field.getInt(null);
                            addToMap(c, resID);
                        }
                    } catch(IllegalArgumentException | IllegalAccessException e) {
                        Log.e(TAG, "init() exception", e);
                    }
                }
            }
        } catch(Exception e) {
            Log.e(TAG, "init() exception", e);
        }
    }

    public static int getPrefKeyIDByValue(String value) {
        if(value == null || value.isEmpty()) return -1;

        Integer id = preferenceKeysMap.get(value);

        return id != null ? id : -1;
    }

    private static void addToMap(Context context, int resID) {
        preferenceKeysMap.put(context.getString(resID), resID);
    }

    public static boolean checkFirstRunInit(Context context) {
        Settings settings = App.getInstance().getSettings();

        if(settings.isFirstRun()) {
            settings.setFirstRun(false);

            ConnectionWizardActivity.runWizard(context, false);

            return true;
        }

        return false;
    }

    public static void enableConnectivityChangeReceiver(Context context, boolean enable) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            enableComponent(context, ConnectivityChangeReceiver.class, enable);
        } else {
            WallabagJobService.enable(context, enable);
        }
    }

    // TODO: reuse in setHandleHttpScheme
    // be careful: this method only enables disabled by default components,
    // it won't disable enabled by default ones
    public static void enableComponent(Context context, Class<?> cls, boolean enable) {
        ComponentName componentName = new ComponentName(context, cls);
        context.getPackageManager().setComponentEnabledSetting(componentName, enable
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
    }

    public static boolean getDefaultCustomSSLSettingsValue() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
    }

    public Settings(Context context) {
        this.context = context.getApplicationContext();
        pref = PreferenceManager.getDefaultSharedPreferences(this.context);
    }

    public void initPreferences() {
        int prefVersion = getInt(R.string.pref_key_internal_preferencesVersion, -1);

        if(prefVersion == PREFERENCES_VERSION) { // preferences are up to date
            return;
        }

        if(prefVersion == -1) { // preferences are not set
            PreferenceManager.setDefaultValues(context, R.xml.preferences, true);

            if(LegacySettingsHelper.migrateLegacySettings(context, pref)) {
                setFirstRun(false);
                setConfigurationOk(false);
            } else { // preferences are not migrated -- set some default values
                if(getDefaultCustomSSLSettingsValue()) {
                    pref.edit().putBoolean(context.getString(
                            R.string.pref_key_connection_advanced_customSSLSettings), true)
                            .apply();
                }

                Themes.Theme theme = android.os.Build.MODEL.equals("NOOK")
                        ? Themes.Theme.LightContrast : Themes.Theme.Light;
                pref.edit().putString(context.getString(R.string.pref_key_ui_theme), theme.toString())
                        .apply();
            }

            SharedPreferences.Editor prefEditor = pref.edit();

            if(!contains(R.string.pref_key_tts_speed)) {
                prefEditor.putFloat(context.getString(R.string.pref_key_tts_speed), 1);
            }
            if(!contains(R.string.pref_key_tts_pitch)) {
                prefEditor.putFloat(context.getString(R.string.pref_key_tts_pitch), 1);
            }

            prefEditor.putInt(context.getString(R.string.pref_key_internal_preferencesVersion),
                    PREFERENCES_VERSION);

            prefEditor.apply();
        }
    }

    public SharedPreferences getSharedPreferences() {
        return pref;
    }

    public boolean contains(String key) {
        return pref.contains(key);
    }

    public boolean contains(int keyResourceID) {
        return contains(context.getString(keyResourceID));
    }

    public boolean getBoolean(String key, boolean defValue) {
        return pref.getBoolean(key, defValue);
    }

    public boolean getBoolean(int keyResourceID, boolean defValue) {
        return getBoolean(context.getString(keyResourceID), defValue);
    }

    public void setBoolean(String key, boolean value) {
        pref.edit().putBoolean(key, value).apply();
    }

    public void setBoolean(int keyResourceID, boolean value) {
        setBoolean(context.getString(keyResourceID), value);
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defValue) {
        return pref.getString(key, defValue);
    }

    public String getString(int keyResourceID) {
        return getString(keyResourceID, null);
    }

    public String getString(int keyResourceID, String defValue) {
        return getString(context.getString(keyResourceID), defValue);
    }

    public void setString(String key, String defValue) {
        pref.edit().putString(key, defValue).apply();
    }

    public void setString(int keyResourceID, String value) {
        setString(context.getString(keyResourceID), value);
    }

    public int getInt(String key, int defValue) {
        return pref.getInt(key, defValue);
    }

    public int getInt(int keyResourceID, int defValue) {
        return getInt(context.getString(keyResourceID), defValue);
    }

    public void setInt(String key, int value) {
        pref.edit().putInt(key, value).apply();
    }

    public void setInt(int keyResourceID, int value) {
        setInt(context.getString(keyResourceID), value);
    }

    public long getLong(String key, long defValue) {
        return pref.getLong(key, defValue);
    }

    public long getLong(int keyResourceID, long defValue) {
        return getLong(context.getString(keyResourceID), defValue);
    }

    public void setLong(String key, long value) {
        pref.edit().putLong(key, value).apply();
    }

    public void setLong(int keyResourceID, long value) {
        setLong(context.getString(keyResourceID), value);
    }

    public float getFloat(String key, float defValue) {
        return pref.getFloat(key, defValue);
    }

    public float getFloat(int keyResourceID, float defValue) {
        return getFloat(context.getString(keyResourceID), defValue);
    }

    public void setFloat(String key, float value) {
        pref.edit().putFloat(key, value).apply();
    }

    public void setFloat(int keyResourceID, float value) {
        setFloat(context.getString(keyResourceID), value);
    }

    public String getUrl() {
        return getString(R.string.pref_key_connection_url);
    }

    public void setUrl(String url) {
        setString(R.string.pref_key_connection_url, url);
    }

    public int getWallabagServerVersion() {
        return getInt(R.string.pref_key_connection_serverVersion, -1);
    }

    public void setWallabagServerVersion(int version) {
        setInt(R.string.pref_key_connection_serverVersion, version);
    }

    public String getUsername() {
        return getString(R.string.pref_key_connection_username);
    }

    public void setUsername(String username) {
        setString(R.string.pref_key_connection_username, username);
    }

    public String getPassword() {
        return getString(R.string.pref_key_connection_password);
    }

    public void setPassword(String password) {
        setString(R.string.pref_key_connection_password, password);
    }

    public String getFeedsUserID() {
        return getString(R.string.pref_key_connection_feedsUserID);
    }

    public void setFeedsUserID(String feedsUserID) {
        setString(R.string.pref_key_connection_feedsUserID, feedsUserID);
    }

    public String getFeedsToken() {
        return getString(R.string.pref_key_connection_feedsToken);
    }

    public void setFeedsToken(String feedsToken) {
        setString(R.string.pref_key_connection_feedsToken, feedsToken);
    }

    public boolean isAcceptAllCertificates() {
        return getBoolean(R.string.pref_key_connection_advanced_acceptAllCertificates, false);
    }

    public void setAcceptAllCertificates(boolean value) {
        setBoolean(R.string.pref_key_connection_advanced_acceptAllCertificates, value);
    }

    public boolean isCustomSSLSettings() {
        return getBoolean(R.string.pref_key_connection_advanced_customSSLSettings, false);
    }

    public void setCustomSSLSettings(boolean value) {
        setBoolean(R.string.pref_key_connection_advanced_customSSLSettings, value);
    }

    public String getHttpAuthUsername() {
        return getString(R.string.pref_key_connection_advanced_httpAuthUsername);
    }

    public void setHttpAuthUsername(String httpAuthUsername) {
        setString(R.string.pref_key_connection_advanced_httpAuthUsername, httpAuthUsername);
    }

    public String getHttpAuthPassword() {
        return getString(R.string.pref_key_connection_advanced_httpAuthPassword);
    }

    public void setHttpAuthPassword(String httpAuthPassword) {
        setString(R.string.pref_key_connection_advanced_httpAuthPassword, httpAuthPassword);
    }

    public int getArticleFontSize() {
        return getInt(R.string.pref_key_ui_article_fontSize, 100);
    }

    public void setArticleFontSize(int fontSize) {
        setInt(R.string.pref_key_ui_article_fontSize, fontSize);
    }

    public boolean isArticleFontSerif() {
        return getBoolean(R.string.pref_key_ui_article_fontSerif, false);
    }

    public void setArticleFontSerif(boolean value) {
        setBoolean(R.string.pref_key_ui_article_fontSerif, value);
    }

    public int getArticlesListLimit() {
        return getInt(R.string.pref_key_ui_lists_limit, 100);
    }

    public void setArticlesListLimit(int limit) {
        setInt(R.string.pref_key_ui_lists_limit, limit);
    }

    public Themes.Theme getTheme() {
        String themeName = getString(R.string.pref_key_ui_theme);

        Themes.Theme theme = null;
        if(themeName != null) {
            try {
                theme = Themes.Theme.valueOf(themeName);
            } catch(IllegalArgumentException ignored) {}
        }

        return theme != null ? theme : Themes.Theme.Light;
    }

    public void setTheme(Themes.Theme theme) {
        setString(R.string.pref_key_ui_theme, theme.toString());
    }

    public boolean isVolumeButtonsScrollingEnabled() {
        return getBoolean(R.string.pref_key_ui_volumeButtonsScrolling_enabled, false);
    }

    public void setVolumeButtonsScrollingEnabled(boolean value) {
        setBoolean(R.string.pref_key_ui_volumeButtonsScrolling_enabled, value);
    }

    public boolean isTapToScrollEnabled() {
        return getBoolean(R.string.pref_key_ui_tapToScroll_enabled, false);
    }

    public void setTapToScrollEnabled(boolean value) {
        setBoolean(R.string.pref_key_ui_tapToScroll_enabled, value);
    }

    public float getScreenScrollingPercent() {
        return getFloat(R.string.pref_key_ui_screenScrolling_percent, 95);
    }

    public void setScreenScrollingPercent(float percent) {
        setFloat(R.string.pref_key_ui_screenScrolling_percent, percent);
    }

    public boolean isScreenScrollingSmooth() {
        return getBoolean(R.string.pref_key_ui_screenScrolling_smooth, true);
    }

    public void setScreenScrollingSmooth(boolean value) {
        setBoolean(R.string.pref_key_ui_screenScrolling_smooth, value);
    }

    public boolean isTtsVisible() {
        return getBoolean(R.string.pref_key_tts_visible, false);
    }

    public void setTtsVisible(boolean value) {
        setBoolean(R.string.pref_key_tts_visible, value);
    }

    public boolean isTtsOptionsVisible() {
        return getBoolean(R.string.pref_key_tts_optionsVisible, false);
    }

    public void setTtsOptionsVisible(boolean value) {
        setBoolean(R.string.pref_key_tts_optionsVisible, value);
    }

    public float getTtsSpeed() {
        return getFloat(R.string.pref_key_tts_speed, 1);
    }

    public void setTtsSpeed(float speed) {
        setFloat(R.string.pref_key_tts_speed, speed);
    }

    public float getTtsPitch() {
        return getFloat(R.string.pref_key_tts_pitch, 1);
    }

    public void setTtsPitch(float pitch) {
        setFloat(R.string.pref_key_tts_pitch, pitch);
    }

    public String getTtsEngine() {
        return getString(R.string.pref_key_tts_engine, "");
    }

    public void setTtsEngine(String engine) {
        setString(R.string.pref_key_tts_engine, engine);
    }

    public String getTtsVoice() {
        return getString(R.string.pref_key_tts_voice, "");
    }

    public void setTtsVoice(String voice) {
        setString(R.string.pref_key_tts_voice, voice);
    }

    public String getTtsLanguageVoice(String language) {
        return getString(context.getString(R.string.pref_key_tts_languageVoice_prefix) + language, "");
    }

    public void setTtsLanguageVoice(String language, String voice) {
        setString(context.getString(R.string.pref_key_tts_languageVoice_prefix) + language, voice);
    }

    public boolean isTtsAutoplayNext() {
        return getBoolean(R.string.pref_key_tts_autoplayNext, false);
    }

    public void setTtsAutoplayNext(boolean value) {
        setBoolean(R.string.pref_key_tts_autoplayNext, value);
    }

    public boolean isAutoSyncEnabled() {
        return getBoolean(R.string.pref_key_autoSync_enabled, false);
    }

    public void setAutoSyncEnabled(boolean value) {
        setBoolean(R.string.pref_key_autoSync_enabled, value);
    }

    public long getAutoSyncInterval() {
        return getLong(R.string.pref_key_autoSync_interval, AlarmManager.INTERVAL_DAY);
    }

    public void setAutoSyncInterval(long interval) {
        setLong(R.string.pref_key_autoSync_interval, interval);
    }

    public int getAutoSyncType() {
        return getInt(R.string.pref_key_autoSync_type, 0);
    }

    public void setAutoSyncType(int type) {
        setInt(R.string.pref_key_autoSync_type, type);
    }

    public boolean isAutoSyncQueueEnabled() {
        return getBoolean(R.string.pref_key_autoSyncQueue_enabled, false);
    }

    public void setAutoSyncQueueEnabled(boolean value) {
        setBoolean(R.string.pref_key_autoSyncQueue_enabled, value);
    }

    public boolean isAutoDownloadNewArticlesEnabled() {
        return getBoolean(R.string.pref_key_autoDlNew_enabled, false);
    }

    public void setAutoDownloadNewArticlesEnabled(boolean value) {
        setBoolean(R.string.pref_key_autoDlNew_enabled, value);
    }

    public boolean isImageCacheEnabled() {
        return getBoolean(R.string.pref_key_imageCache_enabled, false);
    }

    public void setImageCacheEnabled(boolean value) {
        setBoolean(R.string.pref_key_imageCache_enabled, value);
    }

    public boolean isFirstRun() {
        return getBoolean(R.string.pref_key_internal_firstRun, true);
    }

    public void setFirstRun(boolean value) {
        setBoolean(R.string.pref_key_internal_firstRun, value);
    }

    public boolean isConfigurationOk() {
        return getBoolean(R.string.pref_key_internal_configurationIsOk, false);
    }

    public void setConfigurationOk(boolean ok) {
        setBoolean(R.string.pref_key_internal_configurationIsOk, ok);
    }

    public boolean isConfigurationErrorShown() {
        return getBoolean(R.string.pref_key_internal_configurationErrorShown, false);
    }

    public void setConfigurationErrorShown(boolean value) {
        setBoolean(R.string.pref_key_internal_configurationErrorShown, value);
    }

    public boolean isFirstSyncDone() {
        return getBoolean(R.string.pref_key_internal_firstSyncDone, false);
    }

    public void setFirstSyncDone(boolean value) {
        setBoolean(R.string.pref_key_internal_firstSyncDone, value);
    }

    public boolean isOfflineQueuePending() {
        return getBoolean(R.string.pref_key_internal_offlineQueue_pending, false);
    }

    public void setOfflineQueuePending(boolean value) {
        setBoolean(R.string.pref_key_internal_offlineQueue_pending, value);
    }

    public boolean isHandlingHttpScheme() {
        return context.getPackageManager()
                .getComponentEnabledSetting(getHttpSchemeHandlingComponent())
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public void setHandleHttpScheme(boolean handleHttpScheme) {
        if(handleHttpScheme == isHandlingHttpScheme()) return;

        int flag = (handleHttpScheme ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        context.getPackageManager().setComponentEnabledSetting(
                getHttpSchemeHandlingComponent(), flag, PackageManager.DONT_KILL_APP);
    }

    private ComponentName getHttpSchemeHandlingComponent() {
        return new ComponentName(context, HttpSchemeHandlerActivity.class);
    }

}
