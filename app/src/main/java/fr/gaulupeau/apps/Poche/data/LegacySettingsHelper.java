package fr.gaulupeau.apps.Poche.data;

import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;

import fr.gaulupeau.apps.InThePoche.R;

class LegacySettingsHelper {

    static final String PREFS_NAME = "InThePoche";

    static final String URL = "pocheUrl";
    static final String CUSTOM_SSL_SETTINGS = "custom_ssl_settings";
    static final String FONT_SIZE = "font_size";
    static final String SERIF_FONT = "serif_font";
    static final String USERNAME = "username";
    static final String PASSWORD = "password";
    static final String HTTP_AUTH_USERNAME = "http_auth_username";
    static final String HTTP_AUTH_PASSWORD = "http_auth_password";
    static final String THEME = "theme";
    static final String TTS_VISIBLE = "tts.visible";
    static final String TTS_OPTIONS_VISIBLE = "tts.options.visible";
    static final String TTS_SPEED = "tts.speed";
    static final String TTS_PITCH = "tts.pitch";
    static final String TTS_ENGINE = "tts.engine";
    static final String TTS_VOICE = "tts.voice";
    static final String TTS_LANGUAGE_VOICE = "tts.language_voice:";
    static final String TTS_AUTOPLAY_NEXT = "tts.autoplay_next";

    static final String AUTOSYNC_ENABLED = "autosync.enabled";
    static final String AUTOSYNC_INTERVAL = "autosync.interval";
    static final String AUTOSYNC_TYPE = "autosync.type";
    static final String AUTOSYNC_QUEUE_ENABLED = "autosync_queue.enabled";

    static final String CONFIGURATION_IS_OK = "configuration_is_ok";
    static final String CONFIGURATION_ERROR_WAS_SHOWN = "configuration_error_was_shown";
    static final String FIRST_SYNC_DONE = "first_sync_done";
    static final String PENDING_OFFLINE_QUEUE = "offline_queue.pending";

    static boolean migrateLegacySettings(Context cx, SharedPreferences pref) {
        SharedPreferences legacyPref = cx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if(!legacyPref.contains("pocheUrl")) return false;

        SharedPreferences.Editor prefEditor = pref.edit();
        migrateStringPref(cx, URL, R.string.pref_key_connection_url, legacyPref, prefEditor);
        migrateStringPref(cx, USERNAME, R.string.pref_key_connection_username, legacyPref, prefEditor);
        migrateStringPref(cx, PASSWORD, R.string.pref_key_connection_password, legacyPref, prefEditor);

        migrateStringPref(cx, HTTP_AUTH_USERNAME,
                R.string.pref_key_connection_advanced_httpAuthUsername, legacyPref, prefEditor);
        migrateStringPref(cx, HTTP_AUTH_PASSWORD,
                R.string.pref_key_connection_advanced_httpAuthPassword, legacyPref, prefEditor);

        migrateBooleanPref(cx, CUSTOM_SSL_SETTINGS,
                R.string.pref_key_connection_advanced_customSSLSettings, legacyPref, prefEditor);

        if(legacyPref.contains(FONT_SIZE)) {
            int fontSize = legacyPref.getInt(FONT_SIZE, 100);
            if(fontSize < 5) fontSize = 100; // old compatibility check
            prefEditor.putInt(cx.getString(R.string.pref_key_ui_article_fontSize), fontSize);
        }
        migrateBooleanPref(cx, SERIF_FONT, R.string.pref_key_ui_article_fontSerif, legacyPref, prefEditor);

        migrateStringPref(cx, THEME, R.string.pref_key_ui_theme, legacyPref, prefEditor);

        migrateBooleanPref(cx, TTS_VISIBLE, R.string.pref_key_tts_visible, legacyPref, prefEditor);
        migrateBooleanPref(cx, TTS_OPTIONS_VISIBLE, R.string.pref_key_tts_optionsVisible, legacyPref, prefEditor);
        migrateFloatPref(cx, TTS_SPEED, R.string.pref_key_tts_speed, legacyPref, prefEditor, 1);
        migrateFloatPref(cx, TTS_PITCH, R.string.pref_key_tts_pitch, legacyPref, prefEditor, 1);
        migrateStringPref(cx, TTS_ENGINE, R.string.pref_key_tts_engine, legacyPref, prefEditor);
        migrateStringPref(cx, TTS_VOICE, R.string.pref_key_tts_voice, legacyPref, prefEditor);
        for(String key: legacyPref.getAll().keySet()) {
            if(key != null && key.startsWith(TTS_LANGUAGE_VOICE)) {
                String newKey = cx.getString(R.string.pref_key_tts_languageVoice_prefix)
                        + key.substring(TTS_LANGUAGE_VOICE.length());
                prefEditor.putString(newKey, legacyPref.getString(key, null));
            }
        }
        migrateBooleanPref(cx, TTS_AUTOPLAY_NEXT, R.string.pref_key_tts_autoplayNext, legacyPref, prefEditor);

        // some of the next preferences were used only in development
        migrateBooleanPref(cx, CONFIGURATION_IS_OK,
                R.string.pref_key_internal_configurationIsOk, legacyPref, prefEditor);
        migrateBooleanPref(cx, CONFIGURATION_ERROR_WAS_SHOWN,
                R.string.pref_key_internal_configurationErrorShown, legacyPref, prefEditor);
        migrateBooleanPref(cx, FIRST_SYNC_DONE,
                R.string.pref_key_internal_firstSyncDone, legacyPref, prefEditor);
        migrateBooleanPref(cx, PENDING_OFFLINE_QUEUE,
                R.string.pref_key_internal_offlineQueue_pending, legacyPref, prefEditor);

        migrateBooleanPref(cx, AUTOSYNC_ENABLED,
                R.string.pref_key_autoSync_enabled, legacyPref, prefEditor);
        migrateLongPref(cx, AUTOSYNC_INTERVAL,
                R.string.pref_key_autoSync_interval, legacyPref, prefEditor, AlarmManager.INTERVAL_DAY);
        migrateIntPref(cx, AUTOSYNC_TYPE, R.string.pref_key_autoSync_type, legacyPref, prefEditor);
        migrateBooleanPref(cx, AUTOSYNC_QUEUE_ENABLED,
                R.string.pref_key_autoSyncQueue_enabled, legacyPref, prefEditor);

        prefEditor.apply();

        return true;
    }

    private static void migrateStringPref(Context context, String srcKey, int dstKey,
                                          SharedPreferences src, SharedPreferences.Editor dst) {
        if(src.contains(srcKey)) {
            dst.putString(context.getString(dstKey), src.getString(srcKey, null));
        }
    }

    private static void migrateBooleanPref(Context context, String srcKey, int dstKey,
                                           SharedPreferences src, SharedPreferences.Editor dst) {
        if(src.contains(srcKey)) {
            dst.putBoolean(context.getString(dstKey), src.getBoolean(srcKey, false));
        }
    }

    private static void migrateIntPref(Context context, String srcKey, int dstKey,
                                       SharedPreferences src, SharedPreferences.Editor dst) {
        migrateIntPref(context, srcKey, dstKey, src, dst, 0);
    }

    private static void migrateIntPref(Context context, String srcKey, int dstKey,
                                       SharedPreferences src, SharedPreferences.Editor dst,
                                       int defaultValue) {
        if(src.contains(srcKey)) {
            dst.putInt(context.getString(dstKey), src.getInt(srcKey, defaultValue));
        }
    }

    private static void migrateLongPref(Context context, String srcKey, int dstKey,
                                        SharedPreferences src, SharedPreferences.Editor dst,
                                        long defaultValue) {
        if(src.contains(srcKey)) {
            dst.putLong(context.getString(dstKey), src.getLong(srcKey, defaultValue));
        }
    }

    private static void migrateFloatPref(Context context, String srcKey, int dstKey,
                                         SharedPreferences src, SharedPreferences.Editor dst,
                                         float defaultValue) {
        if(src.contains(srcKey)) {
            dst.putFloat(context.getString(dstKey), src.getFloat(srcKey, defaultValue));
        }
    }

}
