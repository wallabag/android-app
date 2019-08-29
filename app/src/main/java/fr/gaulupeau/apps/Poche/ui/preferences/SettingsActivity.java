package fr.gaulupeau.apps.Poche.ui.preferences;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.OperationsHelper;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.StorageHelper;
import fr.gaulupeau.apps.Poche.network.ClientCredentials;
import fr.gaulupeau.apps.Poche.network.WallabagServiceWrapper;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.tasks.TestApiAccessTask;
import fr.gaulupeau.apps.Poche.ui.Themes;

public class SettingsActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final String TITLE_TAG = fr.gaulupeau.apps.Poche.ui.preferences.SettingsActivity.HeaderFragment.class.getSimpleName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new HeaderFragment())
                    .commit();
        } else {
            setTitle(savedInstanceState.getCharSequence(TITLE_TAG));
        }
        getSupportFragmentManager().addOnBackStackChangedListener(
                new FragmentManager.OnBackStackChangedListener() {
                    @Override
                    public void onBackStackChanged() {
                        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                            setTitle(R.string.title_activity_settings);
                        }
                    }
                });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence(TITLE_TAG, getTitle());
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().popBackStackImmediate()) {
            return true;
        }
        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings, fragment)
                .addToBackStack(null)
                .commit();
        setTitle(pref.getTitle());
        return true;
    }

    public static class HeaderFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // Connection wizard preference appears in the root screen instead of the Connection
            // screen
            Preference connectionWizardPreference = findPreference(getString(R.string.pref_key_connection_wizard));
            if (connectionWizardPreference != null) {
                connectionWizardPreference.setOnPreferenceClickListener(preference -> {
                    Activity activity = getActivity();
                    if (activity != null) {
                        ConnectionWizardActivity.runWizard(activity, true);

                        activity.finish();
                    }
                    return true;
                });
            }
        }
    }

    public static class ConnectionFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceClickListener,
            Preference.OnPreferenceChangeListener,
            SharedPreferences.OnSharedPreferenceChangeListener,
            ConfigurationTestHelper.ResultHandler,
            ConfigurationTestHelper.GetCredentialsHandler {
        private Settings settings;

        private String oldUrl;
        private String oldHttpAuthUsername;
        private String oldUsername;
        private String oldApiClientID;

        @Override
        public void onGetCredentialsResult(ClientCredentials clientCredentials) {
            setTextPreference(R.string.pref_key_connection_api_clientID,
                    clientCredentials.clientID);
            setTextPreference(R.string.pref_key_connection_api_clientSecret,
                    clientCredentials.clientSecret);
        }

        @Override
        public void onGetCredentialsFail() {
        }

        @Override
        public void onConfigurationTestSuccess(String url) {
            Log.d(TITLE_TAG, String.format("onConfigurationTestSuccess(%s)", url));

            if (url != null) {
                setTextPreference(R.string.pref_key_connection_url, url);
            }

            settings.setConfigurationOk(true);
            settings.setConfigurationErrorShown(false);

            Toast.makeText(getActivity(), R.string.settings_parametersAutofilled,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionTestFail(WallabagWebService.ConnectionTestResult result,
                                         String details) {
        }

        @Override
        public void onApiAccessTestFail(TestApiAccessTask.Result result, String details) {
        }


        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.connection_preferences, rootKey);

            settings = new Settings(App.getInstance());

            oldUrl = settings.getUrl();
            oldHttpAuthUsername = settings.getHttpAuthUsername();
            oldUsername = settings.getUsername();
            oldApiClientID = settings.getApiClientID();


            Preference connectionAutofillPreference = findPreference(getString(R.string.pref_key_connection_autofill));
            if (connectionAutofillPreference != null) {
                connectionAutofillPreference.setOnPreferenceClickListener(this);
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            Log.d(TITLE_TAG, String.format("onPreferenceChange(key: %s, newValue: %s)",
                    preference.getKey(), newValue));
            return true;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.d(TITLE_TAG, "onSharedPreferenceChanged(" + key + ")");

            boolean themeChanged = false;

            int keyResID = Settings.getPrefKeyIDByValue(key);
            switch (keyResID) {

                case R.string.pref_key_connection_advanced_customSSLSettings:
                case R.string.pref_key_connection_url:
                    Log.d(TITLE_TAG, "onSharedPreferenceChanged() serviceWrapperReinitializationNeeded");
                    Log.i(TITLE_TAG, "applyChanges() calling WallabagServiceWrapper.resetInstance()");
                    WallabagServiceWrapper.resetInstance();
                case R.string.pref_key_connection_advanced_httpAuthUsername:
                case R.string.pref_key_connection_advanced_httpAuthPassword:
                case R.string.pref_key_connection_username:
                case R.string.pref_key_connection_password:
                case R.string.pref_key_connection_api_clientID:
                case R.string.pref_key_connection_api_clientSecret:
                    Log.i(TITLE_TAG, "onSharedPreferenceChanged() invalidateConfiguration");
                    break;
            }

            switch (keyResID) {
                case R.string.pref_key_connection_url:
                case R.string.pref_key_connection_advanced_httpAuthUsername:
                case R.string.pref_key_connection_username:
                case R.string.pref_key_connection_api_clientID:
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
                    break;
            }

            // not optimal :/
            updateSummary(keyResID);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (Settings.getPrefKeyIDByValue(preference.getKey())) {
                case R.string.pref_key_connection_autofill: {
                    ConfigurationTestHelper configurationTestHelper = new ConfigurationTestHelper(
                            getActivity(), this, this, settings, false);
                    configurationTestHelper.test();

                    return true;
                }
            }

            return false;
        }

        private void setTextPreference(int preferenceID, String value) {
            EditTextPreference preference = (EditTextPreference)
                    findPreference(getString(preferenceID));

            if (preference != null) {
                preference.setText(value);
            }
        }

        private void updateSummary(int keyResID) {
//            String key = getString(keyResID);
//
//            switch (keyResID) {
//                case R.string.pref_key_connection_url:
//                    EditTextPreference preference = (EditTextPreference)
//                            findPreference(key);
//                    if (preference != null) {
//                        String value = preference.getText();
//                        setSummary(key, (value == null || value.isEmpty())
//                                ? getString(R.string.pref_desc_connection_url) : value);
//                    }
//                    break;
//
//                case R.string.pref_key_connection_username:
//                case R.string.pref_key_connection_api_clientID:
//                case R.string.pref_key_connection_advanced_httpAuthUsername:
//                    setEditTextSummaryFromContent(key);
//                    break;
//                case R.string.pref_key_connection_password:
//                case R.string.pref_key_connection_api_clientSecret:
//                case R.string.pref_key_connection_advanced_httpAuthPassword:
//                    setPasswordSummary(key);
//                    break;
//
//            }
        }
    }

    public static class UIFragment extends PreferenceFragmentCompat {
        private static final int[] SUMMARIES_TO_INITIATE = {
                R.string.pref_key_ui_theme,
                R.string.pref_key_ui_article_fontSize,
                R.string.pref_key_ui_screenScrolling_percent
        };

        private Settings settings;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.ui_preferences, rootKey);

            settings = new Settings(App.getInstance());

            Preference uiDisableTouchPreference = findPreference(getString(R.string.pref_key_ui_disableTouch_keyCode));
            if (uiDisableTouchPreference != null) {
                uiDisableTouchPreference.setOnPreferenceClickListener(preference -> {
                    showDisableTouchSetKeyCodeDialog();
                    return true;
                });
            }

            Preference uiThemePreference = findPreference(getString(R.string.pref_key_ui_theme));
            if (uiThemePreference != null) {
                uiThemePreference.setOnPreferenceChangeListener((preference, result) -> {
                    Log.d(TITLE_TAG, "onSharedPreferenceChanged() theme changed");

                    Themes.init();

                    Activity activity = getActivity();
                    if (activity != null) Themes.checkTheme(activity);
                    return true;
                });
            }

            ListPreference themeListPreference = (ListPreference) findPreference(
                    getString(R.string.pref_key_ui_theme));
            if (themeListPreference != null) {
                Themes.Theme[] themes = Themes.Theme.values();
                String[] themeEntries = new String[themes.length];
                String[] themeEntryValues = new String[themes.length];
                for (int i = 0; i < themes.length; i++) {
                    themeEntries[i] = getString(themes[i].getNameId());
                    themeEntryValues[i] = themes[i].toString();
                }

                themeListPreference.setEntries(themeEntries);
                themeListPreference.setEntryValues(themeEntryValues);
            }
        }


        private void showDisableTouchSetKeyCodeDialog() {
            Activity activity = getActivity();
            if (activity != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(R.string.d_disableTouch_changeKey_title);

                @SuppressLint("InflateParams") final View view = activity.getLayoutInflater().inflate(R.layout.dialog_set_key, null);
                final TextView keyCodeTextView = view.findViewById(R.id.tv_keyCode);

                // Just a Int so US locale works
                keyCodeTextView.setText(String.format(Locale.US, "%d", settings.getDisableTouchKeyCode()));

                builder.setView(view);

                DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

                        keyCodeTextView.setText(String.format(Locale.US, "%d", keyCode));

                        return false;
                    }
                };

                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            settings.setDisableTouchKeyCode(Integer.parseInt(
                                    keyCodeTextView.getText().toString()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.setOnKeyListener(keyListener);

                builder.show();
            }
        }
    }

    public static class SyncFragment extends PreferenceFragmentCompat {
        private static final int[] SUMMARIES_TO_INITIATE = {
                R.string.pref_key_autoSync_interval,
                R.string.pref_key_autoSync_type
        };

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.sync_preferences, rootKey);

            // Show sync types description dialog
            Preference sync_type_preference = findPreference(getString(R.string.pref_key_sync_syncTypes_description));
            if (sync_type_preference != null) {
                sync_type_preference.setOnPreferenceClickListener(preference -> {
                    Activity activity = getActivity();
                    if (activity != null) {
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.pref_name_sync_syncTypes)
                                .setMessage(R.string.pref_desc_sync_syncTypes_text)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }
                    return true;
                });
            }

            // Set sync interval
            ListPreference autoSyncIntervalListPreference = (ListPreference) findPreference(
                    getString(R.string.pref_key_autoSync_interval));
            if (autoSyncIntervalListPreference != null) {
                // may set arbitrary values on Android API 19+
                autoSyncIntervalListPreference.setEntries(new String[]{
                        getString(R.string.pref_option_autoSync_interval_15m),
                        getString(R.string.pref_option_autoSync_interval_30m),
                        getString(R.string.pref_option_autoSync_interval_1h),
                        getString(R.string.pref_option_autoSync_interval_12h),
                        getString(R.string.pref_option_autoSync_interval_24h)
                });
                autoSyncIntervalListPreference.setEntryValues(new String[]{
                        String.valueOf(AlarmManager.INTERVAL_FIFTEEN_MINUTES),
                        String.valueOf(AlarmManager.INTERVAL_HALF_HOUR),
                        String.valueOf(AlarmManager.INTERVAL_HOUR),
                        String.valueOf(AlarmManager.INTERVAL_HALF_DAY),
                        String.valueOf(AlarmManager.INTERVAL_DAY)
                });
            }
        }


    }

    public static class MiscellaneousFragment extends PreferenceFragmentCompat {
        private static final int[]    SUMMARIES_TO_INITIATE = {
                R.string.pref_key_storage_dbPath
        };
        private              Settings settings;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.miscellaneous_preferences, rootKey);

            settings = new Settings(App.getInstance());

            Preference wipe_db_preference = findPreference(getString(R.string.pref_key_misc_wipeDB));
            if (wipe_db_preference != null) {
                wipe_db_preference.setOnPreferenceClickListener(preference -> {
                    Activity activity = getActivity();
                    if (activity != null) {
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
                });
            }


            Preference handleHttpSchemePreference = findPreference(
                    getString(R.string.pref_key_misc_handleHttpScheme));
            if (handleHttpSchemePreference != null) {
                handleHttpSchemePreference.setDefaultValue(settings.isHandlingHttpScheme());
                handleHttpSchemePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    settings.setHandleHttpScheme((Boolean) newValue);
                    return true;
                });
            }

            ListPreference dbPathListPreference = (ListPreference) findPreference(
                    getString(R.string.pref_key_storage_dbPath));
            if (dbPathListPreference != null) {
                List<String> entriesList = new ArrayList<>(2);
                List<String> entryValuesList = new ArrayList<>(2);

                entriesList.add(getString(R.string.pref_name_storage_dbPath_internalStorage));
                entryValuesList.add("");

                if (StorageHelper.isExternalStorageWritable()) {
                    entriesList.add(getString(R.string.pref_name_storage_dbPath_externalStorage));
                    entryValuesList.add(StorageHelper.getExternalStoragePath());
                }

                dbPathListPreference.setEntries(entriesList.toArray(new String[0]));
                dbPathListPreference.setEntryValues(entryValuesList.toArray(new String[0]));

                dbPathListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (TextUtils.equals(settings.getDbPath(), (String) newValue)) {
                        Log.d(TITLE_TAG, "onPreferenceChange() new DbPath is the same");
                    } else if (settings.moveDb((String) newValue)) { // TODO: do in a background thread
                        DbConnection.resetSession();

                        Toast.makeText(getActivity(), R.string.pref_name_storage_dbPath_dbMoved,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TITLE_TAG, "onPreferenceChange() couldn't move DB; ignoring preference change");
                        return false;
                    }
                    return true;
                });
            }
        }


    }

}
