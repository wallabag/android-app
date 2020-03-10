package fr.gaulupeau.apps.Poche.ui.preferences;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.QueueHelper;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.StorageHelper;
import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.EventHelper;
import fr.gaulupeau.apps.Poche.events.FeedsChangedEvent;
import fr.gaulupeau.apps.Poche.network.ClientCredentials;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.tasks.TestApiAccessTask;
import fr.gaulupeau.apps.Poche.service.AlarmHelper;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;
import fr.gaulupeau.apps.Poche.ui.BaseActionBarActivity;
import fr.gaulupeau.apps.Poche.ui.Themes;

public class SettingsActivity extends BaseActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragment
            implements Preference.OnPreferenceClickListener,
            Preference.OnPreferenceChangeListener,
            SharedPreferences.OnSharedPreferenceChangeListener,
            ConfigurationTestHelper.ResultHandler,
            ConfigurationTestHelper.GetCredentialsHandler {

        private static final String TAG = SettingsFragment.class.getSimpleName();

        private static final int[] SUMMARIES_TO_INITIATE = {
                R.string.pref_key_connection_url,
                R.string.pref_key_connection_advanced_httpAuthUsername,
                R.string.pref_key_connection_advanced_httpAuthPassword,
                R.string.pref_key_connection_username,
                R.string.pref_key_connection_password,
                R.string.pref_key_connection_api_clientID,
                R.string.pref_key_connection_api_clientSecret,
                R.string.pref_key_ui_theme,
                R.string.pref_key_ui_article_fontSize,
                R.string.pref_key_ui_screenScrolling_percent,
                R.string.pref_key_autoSync_interval,
                R.string.pref_key_autoSync_type,
                R.string.pref_key_storage_dbPath
        };

        private Settings settings;

        private boolean autoSyncChanged;
        private boolean oldAutoSyncEnabled;
        private long oldAutoSyncInterval;

        private boolean autoSyncQueueChanged;
        private boolean oldAutoSyncQueueEnabled;

        private boolean checkUserChanged;
        private String oldUrl;
        private String oldHttpAuthUsername;
        private String oldUsername;
        private String oldApiClientID;

        private boolean invalidateConfiguration;
        private boolean serviceWrapperReinitializationNeeded;

        private boolean imageCachingChanged;
        private boolean oldImageCacheEnabled;

        private boolean readingSpeedChanged;
        private int oldReadingSpeed;

        private boolean keepScreenOnChanged;
        private boolean oldkeepScreenOn;
        
        private ConfigurationTestHelper configurationTestHelper;

        public SettingsFragment() {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);

            settings = new Settings(App.getInstance());

            setOnClickListener(R.string.pref_key_connection_wizard);
            setOnClickListener(R.string.pref_key_connection_autofill);
            setOnClickListener(R.string.pref_key_sync_syncTypes_description);
            setOnClickListener(R.string.pref_key_ui_disableTouch_keyCode);
            setOnClickListener(R.string.pref_key_misc_wipeDB);
            setOnClickListener(R.string.pref_key_misc_localQueue_dumpToFile);
            setOnClickListener(R.string.pref_key_misc_localQueue_removeFirstItem);

            ListPreference themeListPreference = (ListPreference)findPreference(
                    getString(R.string.pref_key_ui_theme));
            if(themeListPreference != null) {
                Themes.Theme[] themes = Themes.Theme.values();
                String[] themeEntries = new String[themes.length];
                String[] themeEntryValues = new String[themes.length];
                for(int i = 0; i < themes.length; i++) {
                    themeEntries[i] = getString(themes[i].getNameId());
                    themeEntryValues[i] = themes[i].toString();
                }

                themeListPreference.setEntries(themeEntries);
                themeListPreference.setEntryValues(themeEntryValues);
            }

            ListPreference autoSyncIntervalListPreference = (ListPreference)findPreference(
                    getString(R.string.pref_key_autoSync_interval));
            if(autoSyncIntervalListPreference != null) {
                // may set arbitrary values on Android API 19+
                autoSyncIntervalListPreference.setEntries(new String[] {
                        getString(R.string.pref_option_autoSync_interval_15m),
                        getString(R.string.pref_option_autoSync_interval_30m),
                        getString(R.string.pref_option_autoSync_interval_1h),
                        getString(R.string.pref_option_autoSync_interval_12h),
                        getString(R.string.pref_option_autoSync_interval_24h)
                });
                autoSyncIntervalListPreference.setEntryValues(new String[] {
                        String.valueOf(AlarmManager.INTERVAL_FIFTEEN_MINUTES),
                        String.valueOf(AlarmManager.INTERVAL_HALF_HOUR),
                        String.valueOf(AlarmManager.INTERVAL_HOUR),
                        String.valueOf(AlarmManager.INTERVAL_HALF_DAY),
                        String.valueOf(AlarmManager.INTERVAL_DAY)
                });
            }

            Preference handleHttpSchemePreference = findPreference(
                    getString(R.string.pref_key_misc_handleHttpScheme));
            if(handleHttpSchemePreference != null) {
                handleHttpSchemePreference.setDefaultValue(settings.isHandlingHttpScheme());
                handleHttpSchemePreference.setOnPreferenceChangeListener(this);
            }

            ListPreference dbPathListPreference = (ListPreference)findPreference(
                    getString(R.string.pref_key_storage_dbPath));
            if(dbPathListPreference != null) {
                List<String> entriesList = new ArrayList<>(2);
                List<String> entryValuesList = new ArrayList<>(2);

                entriesList.add(getString(R.string.pref_name_storage_dbPath_internalStorage));
                entryValuesList.add("");

                if(StorageHelper.isExternalStorageWritable()) {
                    entriesList.add(getString(R.string.pref_name_storage_dbPath_externalStorage));
                    entryValuesList.add(StorageHelper.getExternalStoragePath());
                }

                dbPathListPreference.setEntries(entriesList.toArray(new String[0]));
                dbPathListPreference.setEntryValues(entryValuesList.toArray(new String[0]));

                dbPathListPreference.setOnPreferenceChangeListener(this);
            }

            for(int keyID: SUMMARIES_TO_INITIATE) {
                updateSummary(keyID);
            }
        }

        @Override
        public void onStart() {
            super.onStart();

            Log.d(TAG, "onStart() started");

            resetChanges();

            settings.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onStop() {
            Log.d(TAG, "onStop() started");

            settings.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

            if(configurationTestHelper != null) {
                configurationTestHelper.cancel();
                configurationTestHelper = null;
            }

            applyChanges();

            super.onStop();
        }

        private void resetChanges() {
            Log.d(TAG, "resetChanges() started");

            autoSyncChanged = false;
            oldAutoSyncEnabled = settings.isAutoSyncEnabled();
            oldAutoSyncInterval = settings.getAutoSyncInterval();

            autoSyncQueueChanged = false;
            oldAutoSyncQueueEnabled = settings.isAutoSyncQueueEnabled();

            checkUserChanged = false;
            oldUrl = settings.getUrl();
            oldHttpAuthUsername = settings.getHttpAuthUsername();
            oldUsername = settings.getUsername();
            oldApiClientID = settings.getApiClientID();

            imageCachingChanged = false;
            oldImageCacheEnabled = settings.isImageCacheEnabled();

            readingSpeedChanged = false;
            oldReadingSpeed = settings.getReadingSpeed();

            keepScreenOnChanged = false;
            oldkeepScreenOn = settings.isKeepScreenOn();
        }

        private void applyChanges() {
            Log.d(TAG, "applyChanges() started");

            if(autoSyncChanged) {
                autoSyncChanged = false;
                Log.d(TAG, "applyChanges() autoSyncChanged is true");

                boolean newAutoSyncEnabled = settings.isAutoSyncEnabled();
                long newAutoSyncInterval = settings.getAutoSyncInterval();
                if(newAutoSyncEnabled != oldAutoSyncEnabled) {
                    if(newAutoSyncEnabled) {
                        AlarmHelper.setAlarm(getActivity(), newAutoSyncInterval, true);
                    } else {
                        AlarmHelper.unsetAlarm(getActivity(), true);
                    }
                } else if(newAutoSyncEnabled) {
                    if(newAutoSyncInterval != oldAutoSyncInterval) {
                        AlarmHelper.updateAlarmInterval(getActivity(), newAutoSyncInterval);
                    }
                }
            }

            if(autoSyncQueueChanged) {
                autoSyncQueueChanged = false;
                Log.d(TAG, "applyChanges() autoSyncQueueChanged is true");

                boolean newAutoSyncQueueEnabled = settings.isAutoSyncQueueEnabled();
                if(newAutoSyncQueueEnabled != oldAutoSyncQueueEnabled) {
                    if(newAutoSyncQueueEnabled) {
                        if(settings.isOfflineQueuePending()) {
                            Settings.enableConnectivityChangeReceiver(getActivity(), true);
                        }
                    } else {
                        Settings.enableConnectivityChangeReceiver(getActivity(), false);
                    }
                }
            }

            if(checkUserChanged) {
                checkUserChanged = false;

                boolean userChanged = false;
                if(!TextUtils.equals(settings.getUrl(), oldUrl)
                        || !TextUtils.equals(settings.getUsername(), oldUsername)
                        || !TextUtils.equals(settings.getApiClientID(), oldApiClientID)) {
                    userChanged = true;
                } else if(!TextUtils.equals(settings.getHttpAuthUsername(), oldHttpAuthUsername)
                        && (settings.getUsername() == null || settings.getUsername().isEmpty())) {
                    userChanged = true;
                }

                if(userChanged) {
                    settings.setApiRefreshToken("");
                    settings.setApiAccessToken("");

                    OperationsHelper.wipeDB(settings);
                }
            }

            if(invalidateConfiguration) {
                invalidateConfiguration = false;

                Log.i(TAG, "applyChanges() setting isConfigurationOk(false)");
                settings.setConfigurationOk(false);
            }

            if(serviceWrapperReinitializationNeeded) {
                serviceWrapperReinitializationNeeded = false;

                Log.i(TAG, "applyChanges() calling WallabagConnection.resetWallabagService()");
                WallabagConnection.resetWallabagService();
            }

            if(imageCachingChanged) {
                imageCachingChanged = false;

                if(!oldImageCacheEnabled && settings.isImageCacheEnabled()
                        && settings.isFirstSyncDone()) {
                    Log.i(TAG, "applyChanges() image caching changed, starting image fetching");
                    OperationsHelper.fetchImages(App.getInstance());
                }
            }

            if(readingSpeedChanged) {
                readingSpeedChanged = false;

                if(oldReadingSpeed != settings.getReadingSpeed()) {
                    Log.i(TAG, "applyChanges() reading speed changed, posting event");

                    ArticlesChangedEvent event = new ArticlesChangedEvent();
                    event.invalidateAll(FeedsChangedEvent.ChangeType.ESTIMATED_READING_TIME_CHANGED);
                    EventHelper.postEvent(event);
                }
            }

            if(keepScreenOnChanged) {
                keepScreenOnChanged = false;

                if(!oldkeepScreenOn) {
                    Log.i(TAG, "applyChanges() keep screen on changed, keep screen on");
                }
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            Log.d(TAG, String.format("onPreferenceChange(key: %s, newValue: %s)",
                    preference.getKey(), newValue));

            int keyID = Settings.getPrefKeyIDByValue(preference.getKey());
            switch(keyID) {
                case R.string.pref_key_misc_handleHttpScheme:
                    settings.setHandleHttpScheme((Boolean)newValue);
                    break;

                case R.string.pref_key_storage_dbPath:
                    if(TextUtils.equals(settings.getDbPath(), (String)newValue)) {
                        Log.d(TAG, "onPreferenceChange() new DbPath is the same");
                    } else if(settings.moveDb((String)newValue)) { // TODO: do in a background thread
                        DbConnection.resetSession();

                        Toast.makeText(getActivity(), R.string.pref_name_storage_dbPath_dbMoved,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "onPreferenceChange() couldn't move DB; ignoring preference change");
                        return false;
                    }
                    break;
            }

            return true;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.d(TAG, "onSharedPreferenceChanged(" + key + ")");

            boolean themeChanged = false;

            int keyResID = Settings.getPrefKeyIDByValue(key);
            switch(keyResID) {
                case R.string.pref_key_ui_theme:
                    themeChanged = true;
                    break;

                case R.string.pref_key_autoSync_enabled:
                    autoSyncChanged = true;
                    break;

                case R.string.pref_key_autoSync_interval:
                    autoSyncChanged = true;
                    break;

                case R.string.pref_key_autoSyncQueue_enabled:
                    autoSyncQueueChanged = true;
                    break;

                case R.string.pref_key_connection_url:
                    Log.d(TAG, "onSharedPreferenceChanged() serviceWrapperReinitializationNeeded");
                    serviceWrapperReinitializationNeeded = true;
                case R.string.pref_key_connection_advanced_httpAuthUsername:
                case R.string.pref_key_connection_advanced_httpAuthPassword:
                case R.string.pref_key_connection_username:
                case R.string.pref_key_connection_password:
                case R.string.pref_key_connection_api_clientID:
                case R.string.pref_key_connection_api_clientSecret:
                    Log.i(TAG, "onSharedPreferenceChanged() invalidateConfiguration");
                    invalidateConfiguration = true;
                    break;

                case R.string.pref_key_imageCache_enabled:
                    imageCachingChanged = true;
                    break;

                case R.string.pref_key_ui_readingSpeed:
                    readingSpeedChanged = true;
                    break;

                case R.string.pref_key_ui_keepScreenOn:
                    keepScreenOnChanged = true;
                    break;
            }

            switch(keyResID) {
                case R.string.pref_key_connection_url:
                case R.string.pref_key_connection_advanced_httpAuthUsername:
                case R.string.pref_key_connection_username:
                case R.string.pref_key_connection_api_clientID:
                    checkUserChanged = true;
                    break;
            }

            // not optimal :/
            updateSummary(keyResID);

            if(themeChanged) {
                Log.d(TAG, "onSharedPreferenceChanged() theme changed");

                Themes.init();

                Activity activity = getActivity();
                if(activity != null) Themes.checkTheme(activity);
            }
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch(Settings.getPrefKeyIDByValue(preference.getKey())) {
                case R.string.pref_key_connection_wizard: {
                    Activity activity = getActivity();
                    if(activity != null) {
                        ConnectionWizardActivity.runWizard(activity, true);

                        activity.finish();
                    }

                    return true;
                }
                case R.string.pref_key_connection_autofill: {
                    configurationTestHelper = new ConfigurationTestHelper(
                            getActivity(), this, this, settings, false);
                    configurationTestHelper.test();

                    return true;
                }
                case R.string.pref_key_sync_syncTypes_description: {
                    Activity activity = getActivity();
                    if(activity != null) {
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.pref_name_sync_syncTypes)
                                .setMessage(R.string.pref_desc_sync_syncTypes_text)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }
                    return true;
                }
                case R.string.pref_key_ui_disableTouch_keyCode: {
                    showDisableTouchSetKeyCodeDialog();
                    return true;
                }
                case R.string.pref_key_misc_wipeDB: {
                    Activity activity = getActivity();
                    if(activity != null) {
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.pref_name_misc_wipeDB_confirmTitle)
                                .setMessage(R.string.pref_name_misc_wipeDB_confirmMessage)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        OperationsHelper.wipeDB(App.getInstance().getSettings());
                                    }
                                })
                                .setNegativeButton(R.string.negative_answer, null)
                                .show();
                    }
                    return true;
                }
                case R.string.pref_key_misc_localQueue_dumpToFile: {
                    dumpOfflineQueue();
                    return true;
                }
                case R.string.pref_key_misc_localQueue_removeFirstItem: {
                    removeFirstOfflineQueueItem();
                    return true;
                }
            }

            return false;
        }

        private void dumpOfflineQueue() {
            Activity activity = getActivity();
            if (activity == null) return;

            QueueHelper queueHelper = new QueueHelper(DbConnection.getSession());
            List<QueueItem> items = queueHelper.getQueueItems();
            if (items.isEmpty()) {
                Toast.makeText(activity, R.string.misc_localQueue_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            String string = getString(R.string.misc_localQueue_dumpToFile_header,
                    getString(R.string.issues_url)) + "\r\n\r\n"
                    + queueItemsToString(items);

            try {
                File file = StorageHelper.dumpQueueData(string);
                Toast.makeText(activity, getString(R.string.misc_localQueue_dumpToFile_result_dumped,
                        file.getAbsolutePath()), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.w(TAG, "Error during dumping offline queue", e);
                Toast.makeText(activity, getString(R.string.misc_localQueue_dumpToFile_result_error,
                        e.toString()), Toast.LENGTH_LONG).show();
            }
        }

        private String queueItemsToString(List<QueueItem> items) {
            String nl = "\r\n";
            String delim = "; ";

            StringBuilder sb = new StringBuilder();

            sb.append("id").append(delim)
                    .append("action").append(delim)
                    .append("articleId").append(delim)
                    .append("extra").append(delim)
                    .append("extra2").append(nl);

            for (QueueItem item : items) {
                sb.append(item.getId()).append(delim)
                        .append(item.getAction()).append(delim)
                        .append(item.getArticleId()).append(delim)
                        .append(item.getExtra()).append(delim)
                        .append(item.getExtra2()).append(nl);
            }

            return sb.toString();
        }

        private void removeFirstOfflineQueueItem() {
            Activity activity = getActivity();
            if (activity == null) return;

            QueueHelper queueHelper = new QueueHelper(DbConnection.getSession());
            List<QueueItem> items = queueHelper.getQueueItems();
            if (items.isEmpty()) {
                Toast.makeText(activity, R.string.misc_localQueue_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            queueHelper.dequeueItems(Collections.singletonList(items.get(0)));
            Toast.makeText(activity, R.string.misc_localQueue_removeFirstItem_done, Toast.LENGTH_SHORT).show();
        }

        private void showDisableTouchSetKeyCodeDialog() {
            Activity activity = getActivity();
            if(activity != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(R.string.d_disableTouch_changeKey_title);

                @SuppressLint("InflateParams")
                final View view = activity.getLayoutInflater().inflate(R.layout.dialog_set_key, null);
                final TextView keyCodeTextView = (TextView)view.findViewById(R.id.tv_keyCode);

                setIntToTextView(keyCodeTextView, settings.getDisableTouchKeyCode());

                builder.setView(view);

                DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

                        setIntToTextView(keyCodeTextView, keyCode);

                        return false;
                    }
                };

                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            settings.setDisableTouchKeyCode(Integer.parseInt(
                                    keyCodeTextView.getText().toString()));
                        } catch(NumberFormatException ignored) {}
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.setOnKeyListener(keyListener);

                builder.show();
            }
        }

        @SuppressLint("SetTextI18n")
        private void setIntToTextView(TextView textView, int value) {
            textView.setText(Integer.toString(value));
        }

        @Override
        public void onGetCredentialsResult(ClientCredentials clientCredentials) {
            setTextPreference(R.string.pref_key_connection_api_clientID,
                    clientCredentials.clientID);
            setTextPreference(R.string.pref_key_connection_api_clientSecret,
                    clientCredentials.clientSecret);
        }

        @Override
        public void onGetCredentialsFail() {}

        @Override
        public void onConfigurationTestSuccess(String url) {
            Log.d(TAG, String.format("onConfigurationTestSuccess(%s)", url));

            if(url != null) {
                setTextPreference(R.string.pref_key_connection_url, url);
            }

            settings.setConfigurationOk(true);
            settings.setConfigurationErrorShown(false);

            invalidateConfiguration = false;

            Toast.makeText(getActivity(), R.string.settings_parametersAutofilled,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionTestFail(WallabagWebService.ConnectionTestResult result,
                                         String details) {}

        @Override
        public void onApiAccessTestFail(TestApiAccessTask.Result result, String details) {}

        private void setOnClickListener(int keyResID) {
            Preference preference = findPreference(getString(keyResID));
            if(preference != null) {
                preference.setOnPreferenceClickListener(this);
            }
        }

        private void setTextPreference(int preferenceID, String value) {
            EditTextPreference preference = (EditTextPreference)
                    findPreference(getString(preferenceID));

            if(preference != null) {
                preference.setText(value);
            }
        }

        private void updateSummary(int keyResID) {
            String key = getString(keyResID);

            switch(keyResID) {
                case R.string.pref_key_connection_url:
                    EditTextPreference preference = (EditTextPreference)
                            findPreference(key);
                    if(preference != null) {
                        String value = preference.getText();
                        setSummary(key, (value == null || value.isEmpty())
                                ? getString(R.string.pref_desc_connection_url) : value);
                    }
                    break;

                case R.string.pref_key_connection_username:
                case R.string.pref_key_connection_api_clientID:
                case R.string.pref_key_connection_advanced_httpAuthUsername:
                case R.string.pref_key_ui_article_fontSize:
                case R.string.pref_key_ui_screenScrolling_percent:
                    setEditTextSummaryFromContent(key);
                    break;

                case R.string.pref_key_ui_theme:
                case R.string.pref_key_autoSync_interval:
                case R.string.pref_key_autoSync_type:
                    setListSummaryFromContent(key);
                    break;

                case R.string.pref_key_connection_password:
                case R.string.pref_key_connection_api_clientSecret:
                case R.string.pref_key_connection_advanced_httpAuthPassword:
                    setPasswordSummary(key);
                    break;

                case R.string.pref_key_storage_dbPath:
                    ListPreference dbPathListPreference = (ListPreference)findPreference(
                            getString(R.string.pref_key_storage_dbPath));
                    if(dbPathListPreference != null) {
                        CharSequence value = dbPathListPreference.getEntry();
                        if(TextUtils.isEmpty(value)) {
                            dbPathListPreference.setSummary(R.string.pref_name_storage_dbPath_internalStorage);
                        } else if(value.equals(StorageHelper.getExternalStoragePath())) {
                            dbPathListPreference.setSummary(R.string.pref_name_storage_dbPath_externalStorage);
                        } else {
                            dbPathListPreference.setSummary(value);
                        }
                    }
                    break;
            }
        }

        private void setSummary(String key, String text) {
            Preference preference = findPreference(key);
            if(preference != null) {
                preference.setSummary(text);
            }
        }

        private void setEditTextSummaryFromContent(String key) {
            EditTextPreference preference = (EditTextPreference)findPreference(key);
            if(preference != null) {
                preference.setSummary(preference.getText());
            }
        }

        private void setListSummaryFromContent(String key) {
            ListPreference preference = (ListPreference)findPreference(key);
            if(preference != null) {
                preference.setSummary(preference.getEntry());
            }
        }

        private void setPasswordSummary(String key) {
            EditTextPreference preference = (EditTextPreference)findPreference(key);
            if(preference != null) {
                String value = preference.getText();
                preference.setSummary(value == null || value.isEmpty() ? "" : "********");
            }
        }

    }

}
