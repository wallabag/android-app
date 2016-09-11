package fr.gaulupeau.apps.Poche.ui.preferences;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagServiceEndpoint;
import fr.gaulupeau.apps.Poche.network.tasks.TestFeedsTask;
import fr.gaulupeau.apps.Poche.service.AlarmHelper;
import fr.gaulupeau.apps.Poche.ui.Themes;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
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
                R.string.pref_key_connection_username,
                R.string.pref_key_connection_serverVersion,
                R.string.pref_key_connection_feedsUserID,
                R.string.pref_key_connection_advanced_httpAuthUsername,
                R.string.pref_key_ui_theme,
                R.string.pref_key_ui_article_fontSize,
                R.string.pref_key_ui_lists_limit,
                R.string.pref_key_autoUpdate_interval,
                R.string.pref_key_autoUpdate_type
        };

        private Settings settings;

        private boolean newTheme;

        private boolean autoUpdateChanged;
        private boolean initialAutoUpdateEnabled;
        private long initialAutoUpdateInterval;
        private boolean newAutoUpdateEnabled;
        private long newAutoUpdateInterval;

        private boolean autoSyncChanged;
        private boolean initialAutoSyncEnabled;
        private boolean newAutoSyncEnabled;

        private boolean connectionParametersChanged;

        private ConfigurationTestHelper configurationTestHelper;

        public SettingsFragment() {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);

            settings = new Settings(App.getInstance());

            Preference connectionWizardPreference = findPreference(
                    getString(R.string.pref_key_connection_wizard));
            if(connectionWizardPreference != null) {
                connectionWizardPreference.setOnPreferenceClickListener(this);
            }

            Preference autofillPreference = findPreference(
                    getString(R.string.pref_key_connection_autofill));
            if(autofillPreference != null) {
                autofillPreference.setOnPreferenceClickListener(this);
            }

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

            ListPreference autoUpdateIntervalListPreference = (ListPreference)findPreference(
                    getString(R.string.pref_key_autoUpdate_interval));
            if(autoUpdateIntervalListPreference != null) {
                // may set arbitrary values on Android API 19+
                autoUpdateIntervalListPreference.setEntries(new String[] {
                        getString(R.string.settings_autosync_interval_15m),
                        getString(R.string.settings_autosync_interval_30m),
                        getString(R.string.settings_autosync_interval_1h),
                        getString(R.string.settings_autosync_interval_12h),
                        getString(R.string.settings_autosync_interval_24h)
                });
                autoUpdateIntervalListPreference.setEntryValues(new String[] {
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
        public void onResume() {
            super.onResume();

            newTheme = false;

            autoUpdateChanged = false;
            initialAutoUpdateEnabled = settings.isAutoUpdateEnabled();
            initialAutoUpdateInterval = settings.getAutoUpdateInterval();

            autoSyncChanged = false;
            initialAutoSyncEnabled = settings.isAutoSyncQueueEnabled();

            settings.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            settings.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

            if(newTheme) {
                Themes.init();
            }

            if(autoUpdateChanged) {
                autoUpdateChanged = false;

                if(newAutoUpdateEnabled != initialAutoUpdateEnabled) {
                    if(newAutoUpdateEnabled) {
                        AlarmHelper.setAlarm(getActivity(), newAutoUpdateInterval, true);
                    } else {
                        AlarmHelper.unsetAlarm(getActivity(), true);
                    }
                } else if(newAutoUpdateEnabled) {
                    if(newAutoUpdateInterval != initialAutoUpdateInterval) {
                        AlarmHelper.updateAlarmInterval(getActivity(), newAutoUpdateInterval);
                    }
                }
            }

            if(autoSyncChanged) {
                autoSyncChanged = false;

                if(newAutoSyncEnabled != initialAutoSyncEnabled) {
                    if(newAutoSyncEnabled) {
                        if(settings.isOfflineQueuePending()) {
                            Settings.enableConnectivityChangeReceiver(getActivity(), true);
                        }
                    } else {
                        Settings.enableConnectivityChangeReceiver(getActivity(), false);
                    }
                }
            }

            if(connectionParametersChanged) {
                connectionParametersChanged = false;

                Log.i(TAG, "onStop() setting isConfigurationOk(false)");
                settings.setConfigurationOk(false);
            }

            super.onPause();
        }

        @Override
        public void onStop() {
            if(configurationTestHelper != null) {
                configurationTestHelper.cancel();
                configurationTestHelper = null;
            }

            super.onStop();
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

            int keyResID = Settings.getPrefKeyIDByValue(key);
            switch(keyResID) {
                case R.string.pref_key_ui_theme:
                    newTheme = true;
                    break;

                case R.string.pref_key_autoUpdate_enabled:
                    autoUpdateChanged = true;
                    newAutoUpdateEnabled = settings.isAutoUpdateEnabled();
                    break;

                case R.string.pref_key_autoUpdate_interval:
                    autoUpdateChanged = true;
                    newAutoUpdateInterval = settings.getAutoUpdateInterval();
                    break;

                case R.string.pref_key_autoSyncQueue_enabled:
                    autoSyncChanged = true;
                    newAutoSyncEnabled = settings.isAutoSyncQueueEnabled();
                    break;

                case R.string.pref_key_connection_url:
                case R.string.pref_key_connection_username:
                case R.string.pref_key_connection_password:
                case R.string.pref_key_connection_serverVersion:
                case R.string.pref_key_connection_advanced_acceptAllCertificates:
                case R.string.pref_key_connection_advanced_customSSLSettings:
                case R.string.pref_key_connection_advanced_httpAuthUsername:
                case R.string.pref_key_connection_advanced_httpAuthPassword:
                case R.string.pref_key_connection_feedsUserID:
                case R.string.pref_key_connection_feedsToken:
                    Log.i(TAG, "onSharedPreferenceChanged() connectionParametersChanged");
                    connectionParametersChanged = true;
                    break;
            }

            // not optimal :/
            updateSummary(keyResID);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch(Settings.getPrefKeyIDByValue(preference.getKey())) {
                case R.string.pref_key_connection_wizard:
                    Activity activity = getActivity();
                    if(activity != null) {
                        startActivity(new Intent(activity, ConnectionWizardActivity.class));

                        activity.finish();
                    }

                    return true;

                case R.string.pref_key_connection_autofill:
                    configurationTestHelper = new ConfigurationTestHelper(
                            getActivity(), this, this, settings, true);
                    configurationTestHelper.test();

                    return true;
            }

            return false;
        }

        @Override
        public void onGetCredentialsResult(String feedsUserID, String feedsToken) {
            setTextPreference(R.string.pref_key_connection_feedsUserID, feedsUserID);
            setTextPreference(R.string.pref_key_connection_feedsToken, feedsToken);
        }

        @Override
        public void onGetCredentialsFail() {}

        @Override
        public void onConfigurationTestSuccess(String url, Integer serverVersion) {
            Log.d(TAG, String.format("onConfigurationTestSuccess(%s, %s)", url, serverVersion));

            if(url != null) {
                setTextPreference(R.string.pref_key_connection_url, url);
            }
            if(serverVersion != null) {
                ListPreference serverVersionPreference = (ListPreference)findPreference(
                        getString(R.string.pref_key_connection_serverVersion));

                if(serverVersionPreference != null) {
                    serverVersionPreference.setValue(String.valueOf(serverVersion));
                }
            }

            settings.setConfigurationOk(true);

            connectionParametersChanged = false;

            Toast.makeText(getActivity(), R.string.settings_parametersAutofilled,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionTestFail(WallabagServiceEndpoint.ConnectionTestResult result,
                                         String details) {}

        @Override
        public void onFeedsTestFail(TestFeedsTask.Result result, String details) {}

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
                case R.string.pref_key_connection_feedsUserID:
                case R.string.pref_key_connection_advanced_httpAuthUsername:
                case R.string.pref_key_ui_article_fontSize:
                case R.string.pref_key_ui_lists_limit:
                    setEditTextSummaryFromContent(key);
                    break;

                case R.string.pref_key_connection_serverVersion:
                case R.string.pref_key_ui_theme:
                case R.string.pref_key_autoUpdate_interval:
                case R.string.pref_key_autoUpdate_type:
                    setListSummaryFromContent(key);
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

    }

}
