package fr.gaulupeau.apps.Poche.data;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.network.ConnectivityChangeReceiver;
import fr.gaulupeau.apps.Poche.service.WallabagJobService;
import fr.gaulupeau.apps.Poche.ui.HttpSchemeHandlerActivity;
import fr.gaulupeau.apps.Poche.ui.Sortable;
import fr.gaulupeau.apps.Poche.ui.Themes;
import fr.gaulupeau.apps.Poche.ui.preferences.ConnectionWizardActivity;

public class Settings {

    private static final String TAG = Settings.class.getSimpleName();

    private static final String DB_FILENAME = "wallabag";

    private static final int PREFERENCES_VERSION = 100;

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

            ConnectionWizardActivity.runWizard(context, false, false, true);

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

    public Settings(Context context) {
        this.context = context.getApplicationContext();
        pref = PreferenceManager.getDefaultSharedPreferences(this.context);
    }

    public void initPreferences() {
        int prefVersion = getInt(R.string.pref_key_internal_preferencesVersion, -1);

        if(prefVersion == PREFERENCES_VERSION) { // preferences are up to date
            return;
        }

        SharedPreferences.Editor prefEditor = pref.edit();

        if(prefVersion == -1) { // preferences are not set
            PreferenceManager.setDefaultValues(context, R.xml.preferences, true);

            if(LegacySettingsHelper.migrateLegacySettings(context, prefEditor)) {
                setFirstRun(false);
                setConfigurationOk(false);
            } else { // preferences are not migrated -- set some default values
                boolean isEreader = Build.MODEL.equals("NOOK") || Build.MANUFACTURER.equals("Onyx");
                Themes.Theme theme = isEreader ? Themes.Theme.E_INK : Themes.Theme.LIGHT;
                prefEditor.putString(context.getString(R.string.pref_key_ui_theme), theme.toString());
            }

            if(!contains(R.string.pref_key_tts_speed)) {
                prefEditor.putFloat(context.getString(R.string.pref_key_tts_speed), 1);
            }
            if(!contains(R.string.pref_key_tts_pitch)) {
                prefEditor.putFloat(context.getString(R.string.pref_key_tts_pitch), 1);
            }
        } else if(prefVersion < 100) { // v1.*
            prefEditor.putBoolean(context.getString(R.string.pref_key_internal_firstRun), true);
            prefEditor.putBoolean(context.getString(R.string.pref_key_internal_configurationIsOk), false);
        }

        prefEditor.putInt(context.getString(R.string.pref_key_internal_preferencesVersion),
                PREFERENCES_VERSION);

        prefEditor.apply();
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

    public String getApiClientID() {
        return getString(R.string.pref_key_connection_api_clientID);
    }

    public void setApiClientID(String apiClientID) {
        setString(R.string.pref_key_connection_api_clientID, apiClientID);
    }

    public String getApiClientSecret() {
        return getString(R.string.pref_key_connection_api_clientSecret);
    }

    public void setApiClientSecret(String apiClientSecret) {
        setString(R.string.pref_key_connection_api_clientSecret, apiClientSecret);
    }

    public String getApiRefreshToken() {
        return getString(R.string.pref_key_connection_api_refreshToken);
    }

    public void setApiRefreshToken(String apiRefreshToken) {
        setString(R.string.pref_key_connection_api_refreshToken, apiRefreshToken);
    }

    public String getApiAccessToken() {
        return getString(R.string.pref_key_connection_api_accessToken);
    }

    public void setApiAccessToken(String apiAccessToken) {
        setString(R.string.pref_key_connection_api_accessToken, apiAccessToken);
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

    public boolean isArticleTextAlignmentJustify() {
        return getBoolean(R.string.pref_key_ui_article_textAlignment_justify, true);
    }

    public void setArticleTextAlignmentJustify(boolean value) {
        setBoolean(R.string.pref_key_ui_article_textAlignment_justify, value);
    }

    public String getHandlePreformattedTextOption() {
        return getString(R.string.pref_key_ui_article_handlePreformattedText, "pre-overflow");
    }

    public void setHandlePreformattedTextOption(String value) {
        setString(R.string.pref_key_ui_article_handlePreformattedText, value);
    }

    public boolean isFullscreenArticleView() {
        return getBoolean(R.string.pref_key_ui_article_fullscreen, false);
    }

    public void setFullScreenArticleView(boolean value) {
        setBoolean(R.string.pref_key_ui_article_fullscreen, value);
    }

    public int getReadingSpeed() {
        return getInt(R.string.pref_key_ui_readingSpeed, 200);
    }

    public void setReadingSpeed(int readingSpeed) {
        setInt(R.string.pref_key_ui_readingSpeed, readingSpeed);
    }

    public boolean isKeepScreenOn() {
        return getBoolean(R.string.pref_key_ui_keepScreenOn, false);
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        setBoolean(R.string.pref_key_ui_keepScreenOn, keepScreenOn);
    }

    public boolean getSwipeArticles() {
        return getBoolean(R.string.pref_key_ui_swipeArticles, false);
    }

    public void setSwipeArticles(boolean swipeArticles) {
        setBoolean(R.string.pref_key_ui_swipeArticles, swipeArticles);
    }

    public Sortable.SortOrder getListSortOrder() {
        String sortOrderParam = getString(R.string.pref_key_ui_lists_sortOrder);

        Sortable.SortOrder sortOrder = null;
        if(sortOrderParam != null) {
            try {
                sortOrder = Sortable.SortOrder.valueOf(sortOrderParam);
            } catch(IllegalArgumentException ignored) {}
        }

        return sortOrder != null ? sortOrder : Sortable.SortOrder.DESC;
    }

    public void setListSortOrder(Sortable.SortOrder sortOrder) {
        setString(R.string.pref_key_ui_lists_sortOrder, sortOrder.toString());
    }

    public Sortable.SortOrder getTagListSortOrder() {
        String sortOrderParam = getString(R.string.pref_key_ui_tagList_sortOrder);

        Sortable.SortOrder sortOrder = null;
        if(sortOrderParam != null) {
            try {
                sortOrder = Sortable.SortOrder.valueOf(sortOrderParam);
            } catch(IllegalArgumentException ignored) {}
        }

        return sortOrder != null ? sortOrder : Sortable.SortOrder.ASC;
    }

    public void setTagListSortOrder(Sortable.SortOrder sortOrder) {
        setString(R.string.pref_key_ui_tagList_sortOrder, sortOrder.toString());
    }

    public Themes.Theme getTheme() {
        String themeName = getString(R.string.pref_key_ui_theme);

        Themes.Theme theme = null;
        if(themeName != null) {
            try {
                theme = Themes.Theme.valueOf(themeName);
            } catch(IllegalArgumentException ignored) {}
        }

        return theme != null ? theme : Themes.Theme.LIGHT;
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

    public boolean isPreviewImageEnabled() {
        return getBoolean(R.string.pref_key_ui_previewImage_enabled, true);
    }

    public void setPreviewImageEnabled(boolean value) {
        setBoolean(R.string.pref_key_ui_previewImage_enabled, value);
    }

    public boolean isAnnotationsEnabled() {
        return getBoolean(R.string.pref_key_ui_annotations_enabled, false);
    }

    public void setAnnotationsEnabled(boolean value) {
        setBoolean(R.string.pref_key_ui_annotations_enabled, value);
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

    public boolean isDisableTouchEnabled() {
        return getBoolean(R.string.pref_key_ui_disableTouch_enabled, false);
    }

    public void setDisableTouchEnabled(boolean value) {
        setBoolean(R.string.pref_key_ui_disableTouch_enabled, value);
    }

    public boolean isDisableTouchLastState() {
        return getBoolean(R.string.pref_key_ui_disableTouch_lastState, false);
    }

    public void setDisableTouchLastState(boolean value) {
        setBoolean(R.string.pref_key_ui_disableTouch_lastState, value);
    }

    public int getDisableTouchKeyCode() {
        return getInt(R.string.pref_key_ui_disableTouch_keyCode, KeyEvent.KEYCODE_CAMERA);
    }

    public void setDisableTouchKeyCode(int keyCode) {
        setInt(R.string.pref_key_ui_disableTouch_keyCode, keyCode);
    }

    public int getScrolledOverBottom() {
        return getInt(R.string.pref_key_ui_scrollOverBottom, 3);
    }

    public void setScrolledOverBottom(int scrolls) {
        setInt(R.string.pref_key_ui_scrollOverBottom, scrolls);
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

    public boolean isSweepingAfterFastSyncEnabled() {
        return getBoolean(R.string.pref_key_sync_sweepingAfterFastSync_enabled, false);
    }

    public void setSweepingAfterFastSyncEnabled(boolean value) {
        setBoolean(R.string.pref_key_sync_sweepingAfterFastSync_enabled, value);
    }

    public boolean isAutoSyncOnStartupEnabled() {
        return getBoolean(R.string.pref_key_autoSync_onStartup_enabled, true);
    }

    public void setAutoSyncOnStartupEnabled(boolean value) {
        setBoolean(R.string.pref_key_autoSync_onStartup_enabled, value);
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

    public boolean isImageCacheEnabled() {
        return getBoolean(R.string.pref_key_imageCache_enabled, false);
    }

    public void setImageCacheEnabled(boolean value) {
        setBoolean(R.string.pref_key_imageCache_enabled, value);
    }

    public String getDbPath() {
        return getString(R.string.pref_key_storage_dbPath);
    }

    public void setDbPath(String dbPath) {
        setString(R.string.pref_key_storage_dbPath, dbPath);
    }

    public boolean isAppendWallabagMentionEnabled() {
        return getBoolean(R.string.pref_key_misc_appendWallabagMention_enabled, true);
    }

    public void setAppendWallabagMentionEnabled(boolean value) {
        setBoolean(R.string.pref_key_misc_appendWallabagMention_enabled, value);
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

    public long getLatestUpdatedItemTimestamp() {
        return getLong(R.string.pref_key_internal_update_latestUpdatedItemTimestamp, 0);
    }

    public void setLatestUpdatedItemTimestamp(long timestamp) {
        setLong(R.string.pref_key_internal_update_latestUpdatedItemTimestamp, timestamp);
    }

    public long getLatestUpdateRunTimestamp() {
        return getLong(R.string.pref_key_internal_update_latestUpdateRunTimestamp, 0);
    }

    public void setLatestUpdateRunTimestamp(long timestamp) {
        setLong(R.string.pref_key_internal_update_latestUpdateRunTimestamp, timestamp);
    }

    public String getDbPathForDbHelper() {
        String dbPath = getDbPath();
        return (TextUtils.isEmpty(dbPath) ? "" : (dbPath + "/")) + DB_FILENAME;
    }

    private String getFullDbPath(String dbPath) {
        // empty path == internal storage
        if(TextUtils.isEmpty(dbPath)) {
            return context.getDatabasePath(DB_FILENAME).getAbsolutePath();
        }

        return dbPath + "/" + DB_FILENAME;
    }

    public boolean moveDb(String dstPath) {
        String srcPath = getFullDbPath(getDbPath());
        dstPath = getFullDbPath(dstPath);
        Log.d(TAG, "moveDb() srcPath: " + srcPath);
        Log.d(TAG, "moveDb() dstPath: " + dstPath);

        String[] filesEndings = new String[] {"", "-shm", "-wal"};

        for(String ending: filesEndings) {
            Log.d(TAG, "moveDb() moving file with ending: " + ending);
            StorageHelper.CopyFileResult result
                    = StorageHelper.copyFile(srcPath + ending, dstPath + ending);
            Log.d(TAG, "moveDb() result: " + result);

            if(result != StorageHelper.CopyFileResult.OK
                    && result != StorageHelper.CopyFileResult.SRC_DOES_NOT_EXIST) {
                return false;
            }
        }

        for(String ending: filesEndings) {
            Log.d(TAG, "moveDb() deleting file with ending: " + ending);
            boolean result = StorageHelper.deleteFile(srcPath + ending);
            Log.d(TAG, "moveDb() deleted: " + result);
        }

        return true;
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
