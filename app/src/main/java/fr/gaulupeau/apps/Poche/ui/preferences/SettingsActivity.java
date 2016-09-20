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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagServiceEndpoint;
import fr.gaulupeau.apps.Poche.network.tasks.TestFeedsTask;
import fr.gaulupeau.apps.Poche.service.AlarmHelper;
import fr.gaulupeau.apps.Poche.ui.Themes;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
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
                R.string.pref_key_connection_password,
                R.string.pref_key_connection_serverVersion,
                R.string.pref_key_connection_feedsUserID,
                R.string.pref_key_connection_feedsToken,
                R.string.pref_key_connection_advanced_httpAuthUsername,
                R.string.pref_key_connection_advanced_httpAuthPassword,
                R.string.pref_key_ui_theme,
                R.string.pref_key_ui_article_fontSize,
                R.string.pref_key_ui_lists_limit,
                R.string.pref_key_ui_tapToScroll_percent,
                R.string.pref_key_autoSync_interval,
                R.string.pref_key_autoSync_type
        };

        private Settings settings;

        private boolean newTheme;

        private boolean autoSyncChanged;
        private boolean initialAutoSyncEnabled;
        private long initialAutoSyncInterval;
        private boolean newAutoSyncEnabled;
        private long newAutoSyncInterval;

        private boolean autoSyncQueueChanged;
        private boolean initialAutoSyncQueueEnabled;
        private boolean newAutoSyncQueueEnabled;

        private boolean connectionParametersChanged;
        private boolean httpClientReinitializationNeeded;

        private ConfigurationTestHelper configurationTestHelper;

        public SettingsFragment() {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);

            settings = new Settings(App.getInstance());

            setOnClickListener(R.string.pref_key_connection_wizard);
            setOnClickListener(R.string.pref_key_connection_autofill);
            setOnClickListener(R.string.pref_key_connection_advanced_clearCookies);
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
        public void onResume() {
            super.onResume();

            newTheme = false;

            autoSyncChanged = false;
            initialAutoSyncEnabled = settings.isAutoSyncEnabled();
            initialAutoSyncInterval = settings.getAutoSyncInterval();

            autoSyncQueueChanged = false;
            initialAutoSyncQueueEnabled = settings.isAutoSyncQueueEnabled();

            settings.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            settings.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

            if(newTheme) {
                Themes.init();
            }

            if(autoSyncChanged) {
                autoSyncChanged = false;

                if(newAutoSyncEnabled != initialAutoSyncEnabled) {
                    if(newAutoSyncEnabled) {
                        AlarmHelper.setAlarm(getActivity(), newAutoSyncInterval, true);
                    } else {
                        AlarmHelper.unsetAlarm(getActivity(), true);
                    }
                } else if(newAutoSyncEnabled) {
                    if(newAutoSyncInterval != initialAutoSyncInterval) {
                        AlarmHelper.updateAlarmInterval(getActivity(), newAutoSyncInterval);
                    }
                }
            }

            if(autoSyncQueueChanged) {
                autoSyncQueueChanged = false;

                if(newAutoSyncQueueEnabled != initialAutoSyncQueueEnabled) {
                    if(newAutoSyncQueueEnabled) {
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

                Log.i(TAG, "onPause() setting isConfigurationOk(false)");
                settings.setConfigurationOk(false);
            }

            if(httpClientReinitializationNeeded) {
                httpClientReinitializationNeeded = false;

                Log.i(TAG, "onPause() calling WallabagConnection.replaceClient()");
                WallabagConnection.replaceClient();
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

                case R.string.pref_key_autoSync_enabled:
                    autoSyncChanged = true;
                    newAutoSyncEnabled = settings.isAutoSyncEnabled();
                    break;

                case R.string.pref_key_autoSync_interval:
                    autoSyncChanged = true;
                    newAutoSyncInterval = settings.getAutoSyncInterval();
                    break;

                case R.string.pref_key_autoSyncQueue_enabled:
                    autoSyncQueueChanged = true;
                    newAutoSyncQueueEnabled = settings.isAutoSyncQueueEnabled();
                    break;

                case R.string.pref_key_connection_advanced_acceptAllCertificates:
                case R.string.pref_key_connection_advanced_customSSLSettings:
                    Log.d(TAG, "onSharedPreferenceChanged() httpClientReinitializationNeeded");
                    httpClientReinitializationNeeded = true;
                case R.string.pref_key_connection_url:
                case R.string.pref_key_connection_username:
                case R.string.pref_key_connection_password:
                case R.string.pref_key_connection_serverVersion:
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
                            getActivity(), this, this, settings, true, false);
                    configurationTestHelper.test();

                    return true;
                }
                case R.string.pref_key_connection_advanced_clearCookies: {
                    Activity activity = getActivity();
                    if(activity != null) {
                        WallabagConnection.clearCookies(getActivity());
                        WallabagConnection.replaceClient();

                        Toast.makeText(activity,
                                R.string.pref_toast_connection_advanced_clearCookies,
                                Toast.LENGTH_SHORT).show();
                    }

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
                                        DbConnection.getSession().getArticleDao().deleteAll();
                                        DbConnection.getSession().getQueueItemDao().deleteAll();

                                        App.getInstance().getSettings().setFirstSyncDone(false);
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
            settings.setConfigurationErrorShown(false);

            connectionParametersChanged = false;

            Toast.makeText(getActivity(), R.string.settings_parametersAutofilled,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionTestFail(WallabagServiceEndpoint.ConnectionTestResult result,
                                         String details) {}

        @Override
        public void onFeedsTestFail(TestFeedsTask.Result result, String details) {}

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
                case R.string.pref_key_connection_feedsUserID:
                case R.string.pref_key_connection_advanced_httpAuthUsername:
                case R.string.pref_key_ui_article_fontSize:
                case R.string.pref_key_ui_lists_limit:
                case R.string.pref_key_ui_tapToScroll_percent:
                    setEditTextSummaryFromContent(key);
                    break;

                case R.string.pref_key_connection_serverVersion:
                case R.string.pref_key_ui_theme:
                case R.string.pref_key_autoSync_interval:
                case R.string.pref_key_autoSync_type:
                    setListSummaryFromContent(key);
                    break;

                case R.string.pref_key_connection_password:
                case R.string.pref_key_connection_feedsToken:
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
