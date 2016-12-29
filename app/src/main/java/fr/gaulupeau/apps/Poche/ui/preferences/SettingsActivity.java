package fr.gaulupeau.apps.Poche.ui.preferences;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.OperationsHelper;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.ClientCredentials;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.WallabagServiceWrapper;
import fr.gaulupeau.apps.Poche.network.tasks.TestApiAccessTask;
import fr.gaulupeau.apps.Poche.service.AlarmHelper;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;
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
                R.string.pref_key_connection_api_refreshToken, // TODO: remove: debug
                R.string.pref_key_connection_api_accessToken, // TODO: remove: debug
                R.string.pref_key_ui_theme,
                R.string.pref_key_ui_article_fontSize,
                R.string.pref_key_ui_screenScrolling_percent,
                R.string.pref_key_autoSync_interval,
                R.string.pref_key_autoSync_type
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

        private ConfigurationTestHelper configurationTestHelper;

        public SettingsFragment() {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);

            settings = new Settings(App.getInstance());

            setOnClickListener(R.string.pref_key_connection_wizard);
            setOnClickListener(R.string.pref_key_connection_autofill);
            setOnClickListener(R.string.pref_key_misc_wipeDB);

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

                Log.i(TAG, "applyChanges() calling WallabagServiceWrapper.resetInstance()");
                WallabagServiceWrapper.resetInstance();
            }

            if(imageCachingChanged) {
                imageCachingChanged = false;

                if(!oldImageCacheEnabled && settings.isImageCacheEnabled()
                        && settings.isFirstSyncDone()) {
                    Log.i(TAG, "applyChanges() image caching changed, starting image fetching");
                    ServiceHelper.fetchImages(App.getInstance());
                }
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            int keyID = Settings.getPrefKeyIDByValue(preference.getKey());
            if(keyID == R.string.pref_key_misc_handleHttpScheme) {
                settings.setHandleHttpScheme((Boolean)newValue);
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

                case R.string.pref_key_connection_advanced_customSSLSettings:
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
            }

            return false;
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
                case R.string.pref_key_connection_api_refreshToken: // TODO: remove: debug
                case R.string.pref_key_connection_api_accessToken: // TODO: remove: debug
                case R.string.pref_key_connection_advanced_httpAuthPassword:
                    setPasswordSummary(key);
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
