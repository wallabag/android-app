package fr.gaulupeau.apps.Poche.ui;

import android.app.AlarmManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.service.AlarmHelper;

public class SettingsActivityNew extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener {

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

        public SettingsFragment() {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);

            settings = new Settings(App.getInstance());

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

                themeListPreference.setOnPreferenceChangeListener(this);
            }

            Preference autoUpdateEnabledPreference = findPreference(
                    getString(R.string.pref_key_autoUpdate_enabled));
            if(autoUpdateEnabledPreference != null) {
                autoUpdateEnabledPreference.setOnPreferenceChangeListener(this);
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

                autoUpdateIntervalListPreference.setOnPreferenceChangeListener(this);
            }

            Preference autoSyncQueueEnabledPreference = findPreference(
                    getString(R.string.pref_key_autoSyncQueue_enabled));
            if(autoSyncQueueEnabledPreference != null) {
                autoSyncQueueEnabledPreference.setOnPreferenceChangeListener(this);
            }

            Preference handleHttpSchemePreference = findPreference(
                    getString(R.string.pref_key_misc_handleHttpScheme));
            if(handleHttpSchemePreference != null) {
                handleHttpSchemePreference.setDefaultValue(settings.isHandlingHttpScheme());
                handleHttpSchemePreference.setOnPreferenceChangeListener(this);
            }
        }

        @Override
        public void onStart() {
            super.onStart();

            newTheme = false;

            autoUpdateChanged = false;
            initialAutoUpdateEnabled = settings.isAutoUpdateEnabled();
            initialAutoUpdateInterval = settings.getAutoUpdateInterval();

            autoSyncChanged = false;
            initialAutoSyncEnabled = settings.isAutoSyncQueueEnabled();
        }

        @Override
        public void onStop() {
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

            super.onStop();
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String key = preference.getKey();

            if(key == null || key.isEmpty()) return true;

            if(key.equals(getString(R.string.pref_key_ui_theme))) {
                newTheme = true;
            } else if(key.equals(getString(R.string.pref_key_autoUpdate_enabled))) {
                autoUpdateChanged = true;
                newAutoUpdateEnabled = (Boolean)newValue;
            } else if(key.equals(getString(R.string.pref_key_autoUpdate_interval))) {
                autoUpdateChanged = true;
                newAutoUpdateInterval = (Long)newValue;
            } else if(key.equals(getString(R.string.pref_key_autoSyncQueue_enabled))) {
                autoSyncChanged = true;
                newAutoSyncEnabled = (Boolean)newValue;
            } else if(key.equals(getString(R.string.pref_key_misc_handleHttpScheme))) {
                settings.setHandleHttpScheme((Boolean)newValue);
            }

            return true;
        }

    }

}
