package fr.gaulupeau.apps.Poche.ui;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatSpinner;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.FeedsCredentials;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagServiceEndpoint;
import fr.gaulupeau.apps.Poche.network.tasks.GetCredentialsTask;
import fr.gaulupeau.apps.Poche.network.tasks.TestConnectionTask;
import fr.gaulupeau.apps.Poche.network.tasks.TestConnectionTask.TestResult;
import fr.gaulupeau.apps.Poche.service.AlarmHelper;

public class SettingsActivity extends BaseActionBarActivity
        implements TestConnectionTask.ResultHandler, GetCredentialsTask.ResultHandler {

    private Button btnTestConnection;
    private Button btnGetCredentials;
    private Button btnDone;
    private EditText editPocheUrl;
    private EditText editAPIUsername;
    private EditText editAPIToken;
    private AppCompatSpinner versionChooser;
    private CheckBox allCerts;
    private CheckBox customSSLSettings;
    private AppCompatSpinner themeChooser;
    private EditText fontSizeET;
    private CheckBox serifFont;
    private EditText listLimit;
    private CheckBox handleHttpScheme;
    private TextView textViewVersion;
    private EditText username;
    private EditText password;
    private EditText httpAuthUsername;
    private EditText httpAuthPassword;
    private CheckBox autosyncEnableCheckbox;
    private CheckBox autosyncQueueEnableCheckbox;
    private AppCompatSpinner autosyncIntervalChooser;
    private AppCompatSpinner autosyncTypeChooser;

    private ProgressDialog progressDialog;

    private Settings settings;

    private TestConnectionTask testConnectionTask;
    private GetCredentialsTask getCredentialsTask;

    private Boolean configurationIsOk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        settings = App.getInstance().getSettings();

        editPocheUrl = (EditText) findViewById(R.id.pocheUrl);
        editAPIUsername = (EditText) findViewById(R.id.APIUsername);
        editAPIToken = (EditText) findViewById(R.id.APIToken);
        versionChooser = (AppCompatSpinner) findViewById(R.id.versionChooser);
        allCerts = (CheckBox) findViewById(R.id.accept_all_certs_cb);
        customSSLSettings = (CheckBox) findViewById(R.id.custom_ssl_settings_cb);
        themeChooser = (AppCompatSpinner) findViewById(R.id.themeChooser);
        fontSizeET = (EditText) findViewById(R.id.fontSizeET);
        serifFont = (CheckBox) findViewById(R.id.ui_font_serif);
        listLimit = (EditText) findViewById(R.id.list_limit_number);
        handleHttpScheme = (CheckBox) findViewById(R.id.handle_http_scheme);
        autosyncEnableCheckbox = (CheckBox) findViewById(R.id.autosync_enable);
        autosyncQueueEnableCheckbox = (CheckBox) findViewById(R.id.autosync_queue_enable);
        autosyncIntervalChooser = (AppCompatSpinner) findViewById(R.id.autosync_interval_chooser);
        autosyncTypeChooser = (AppCompatSpinner) findViewById(R.id.autosync_type_chooser);

        editPocheUrl.setText(settings.getUrl());
        editPocheUrl.setSelection(editPocheUrl.getText().length());
        editAPIUsername.setText(settings.getFeedsUserID());
        editAPIToken.setText(settings.getFeedsToken());

        int wallabagVersion = settings.getWallabagServerVersion();
        versionChooser.setSelection((wallabagVersion == -1 ? 2 : wallabagVersion) - 1);

        allCerts.setChecked(settings.isAcceptAllCertificates());
        customSSLSettings.setChecked(settings.isCustomSSLSettings());

        Themes.Theme[] themes = Themes.Theme.values();
        String[] themeOptions = new String[themes.length];
        Themes.Theme currentThemeName = settings.getTheme();
        int currentThemeIndex = 0;
        for(int i = 0; i < themes.length; i++) {
            if(themes[i] == currentThemeName) currentThemeIndex = i;
            themeOptions[i] = getString(themes[i].getNameId());
        }
        themeChooser.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, themeOptions));
        themeChooser.setSelection(currentThemeIndex);

        fontSizeET.setText(String.valueOf(settings.getArticleFontSize()));

        serifFont.setChecked(settings.isArticleFontSerif());
        listLimit.setText(String.valueOf(settings.getArticlesListLimit()));

        handleHttpScheme.setChecked(settings.isHandlingHttpScheme());

        username = (EditText) findViewById(R.id.username);
        username.setText(settings.getUsername());
        password = (EditText) findViewById(R.id.password);
        password.setText(settings.getPassword());

        httpAuthUsername = (EditText) findViewById(R.id.http_auth_username);
        httpAuthUsername.setText(settings.getHttpAuthUsername());
        httpAuthPassword = (EditText) findViewById(R.id.http_auth_password);
        httpAuthPassword.setText(settings.getHttpAuthPassword());

        final boolean autosyncEnabled = settings.isAutoUpdateEnabled();
        final boolean autosyncQueueEnabled = settings.isAutoSyncQueueEnabled();
        final long autosyncInterval = settings.getAutoUpdateInterval();
        final int autosyncType = settings.getAutoUpdateType();
        autosyncEnableCheckbox.setChecked(autosyncEnabled);
        autosyncQueueEnableCheckbox.setChecked(autosyncQueueEnabled);
        // TODO: better ArrayAdapter creation
        autosyncIntervalChooser.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new String[] {
                getString(R.string.settings_autosync_interval_15m),
                getString(R.string.settings_autosync_interval_30m),
                getString(R.string.settings_autosync_interval_1h),
                getString(R.string.settings_autosync_interval_12h),
                getString(R.string.settings_autosync_interval_24h)}));
        autosyncIntervalChooser.setSelection(
                Settings.autoUpdateIntervalToOptionIndex(autosyncInterval));
        autosyncTypeChooser.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new String[] {
                getString(R.string.settings_autosync_type_fast),
                getString(R.string.settings_autosync_type_full)}));
        autosyncTypeChooser.setSelection(autosyncType);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCanceledOnTouchOutside(true);
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                cancelTasks();
            }
        });

        btnTestConnection = (Button) findViewById(R.id.btnTestConnection);
        btnTestConnection.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                cancelTask(testConnectionTask);

                progressDialog.setMessage(getString(R.string.settings_testingConnection));
                progressDialog.show();

                testConnectionTask = new TestConnectionTask(
                        editPocheUrl.getText().toString(),
                        username.getText().toString(), password.getText().toString(),
                        httpAuthUsername.getText().toString(), httpAuthPassword.getText().toString(),
                        customSSLSettings.isChecked(), allCerts.isChecked(), -1, false,
                        SettingsActivity.this);

                testConnectionTask.execute();
            }
        });

        btnGetCredentials = (Button) findViewById(R.id.btnGetFeedsCredentials);
        btnGetCredentials.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                cancelTask(getCredentialsTask);

                progressDialog.setMessage(getString(R.string.settings_gettingCredentials));
                progressDialog.show();

                getCredentialsTask = new GetCredentialsTask(
                        SettingsActivity.this, editPocheUrl.getText().toString(),
                        username.getText().toString(), password.getText().toString(),
                        httpAuthUsername.getText().toString(), httpAuthPassword.getText().toString());

                getCredentialsTask.execute();
            }
        });

        btnDone = (Button) findViewById(R.id.btnDone);
        btnDone.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                settings.setUrl(editPocheUrl.getText().toString());
                settings.setFeedsUserID(editAPIUsername.getText().toString());
                settings.setFeedsToken(editAPIToken.getText().toString());

                settings.setWallabagServerVersion(versionChooser.getSelectedItemPosition() + 1);

                settings.setAcceptAllCertificates(allCerts.isChecked());
                settings.setCustomSSLSettings(customSSLSettings.isChecked());
                Themes.Theme selectedTheme = Themes.Theme.values()[themeChooser.getSelectedItemPosition()];
                settings.setTheme(selectedTheme);
                try {
                    settings.setArticleFontSize(Integer.parseInt(fontSizeET.getText().toString()));
                } catch(NumberFormatException ignored) {}
                settings.setArticleFontSerif(serifFont.isChecked());
                try {
                    settings.setArticlesListLimit(Integer.parseInt(listLimit.getText().toString()));
                } catch(NumberFormatException ignored) {}

                settings.setHandleHttpScheme(handleHttpScheme.isChecked());

                settings.setUsername(username.getText().toString());
                settings.setPassword(password.getText().toString());

                settings.setHttpAuthUsername(httpAuthUsername.getText().toString());
                settings.setHttpAuthPassword(httpAuthPassword.getText().toString());

                boolean autosyncEnabledNew = autosyncEnableCheckbox.isChecked();
                boolean autosyncQueueEnabledNew = autosyncQueueEnableCheckbox.isChecked();
                long autosyncIntervalNew = Settings.autoUpdateOptionIndexToInterval(
                        autosyncIntervalChooser.getSelectedItemPosition());
                int autosyncTypeNew = autosyncTypeChooser.getSelectedItemPosition();

                settings.setAutoUpdateEnabled(autosyncEnabledNew);
                settings.setAutoSyncQueueEnabled(autosyncQueueEnabledNew);
                settings.setAutoUpdateInterval(autosyncIntervalNew);
                settings.setAutoUpdateType(autosyncTypeNew);

                if(autosyncEnabledNew != autosyncEnabled) {
                    if(autosyncEnabledNew) {
                        AlarmHelper.setAlarm(SettingsActivity.this, autosyncIntervalNew, true);
                    } else {
                        AlarmHelper.unsetAlarm(SettingsActivity.this, true);
                    }
                } else if(autosyncEnabledNew) {
                    if(autosyncInterval != autosyncIntervalNew) {
                        AlarmHelper.updateAlarmInterval(SettingsActivity.this, autosyncIntervalNew);
                    }
                }

                if(autosyncQueueEnabledNew != autosyncQueueEnabled) {
                    if(autosyncQueueEnabledNew) {
                        if(settings.isOfflineQueuePending()) {
                            Settings.enableConnectivityChangeReceiver(SettingsActivity.this, true);
                        }
                    } else {
                        Settings.enableConnectivityChangeReceiver(SettingsActivity.this, false);
                    }
                }

                if(configurationIsOk != null) {
                    settings.setConfigurationOk(configurationIsOk);
                    settings.setConfigurationErrorShown(false);
                }

                finish();

                if(selectedTheme != Themes.getCurrentTheme()) {
                    Themes.init();

                    Intent intent = new Intent(SettingsActivity.this, ArticlesListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }
            }
        });

        textViewVersion = (TextView) findViewById(R.id.version);
        textViewVersion.setText(BuildConfig.VERSION_NAME);

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    @Override
    protected void onStop() {
        super.onStop();

        cancelTasks();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuWipeDb: {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle(R.string.wipe_db_dialog_title);
                b.setMessage(R.string.wipe_db_dialog_message);
                b.setPositiveButton(R.string.positive_answer, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DbConnection.getSession().getArticleDao().deleteAll();
                    }
                });
                b.setNegativeButton(R.string.negative_answer, null);
                b.create().show();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTestConnectionResult(List<TestResult> results) {
        if(progressDialog != null) progressDialog.dismiss();

        TestResult testResult = results.get(0);
        WallabagServiceEndpoint.ConnectionTestResult result = testResult.result;
        String errorMessage = testResult.errorMessage;

        boolean success = testResult.result == WallabagServiceEndpoint.ConnectionTestResult.OK;
        if(success) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.d_testConnection_success_title)
                    .setMessage(R.string.d_connectionTest_success_text)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else {
            if(result != null) {
                switch(result) {
                    case WallabagNotFound:
                        errorMessage = getString(R.string.testConnection_errorMessage1, 1);
                        break;

                    case IncorrectCredentials:
                        errorMessage = getString(R.string.testConnection_errorMessage2, 2);
                        break;

                    case AuthProblem:
                        errorMessage = getString(R.string.testConnection_errorMessage3, 3);
                        break;

                    case HTTPAuth:
                        errorMessage = getString(R.string.testConnection_errorMessage5, 5);
                        break;

                    case IncorrectURL:
                        errorMessage = getString(R.string.testConnection_errorMessage6, 6);
                        break;

                    default:
                        errorMessage = getString(R.string.testConnection_errorMessage_unknown)
                                + result;
                }
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.d_testConnection_fail_title)
                    .setMessage(errorMessage)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }

        configurationIsOk = success;
    }

    @Override
    public void handleGetCredentialsResult(Boolean success, FeedsCredentials credentials,
                                           int wallabagVersion) {
        if(progressDialog != null) progressDialog.dismiss();

        if(success) {
            editAPIUsername.setText(credentials.userID);
            editAPIToken.setText(credentials.token);

            if(wallabagVersion == -1) wallabagVersion = 2;
            versionChooser.setSelection(wallabagVersion - 1);

            Toast.makeText(this, R.string.getCredentials_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.getCredentials_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelTasks() {
        cancelTask(testConnectionTask);
        cancelTask(getCredentialsTask);
    }

    private void cancelTask(AsyncTask task) {
        if(task != null) {
            task.cancel(true);
        }
    }

}
