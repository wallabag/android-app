package fr.gaulupeau.apps.Poche.ui;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.GetCredentialsTask;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.TestConnectionTask;
import fr.gaulupeau.apps.Poche.data.WallabagSettings;

public class SettingsActivity extends BaseActionBarActivity {
	Button btnTestConnection;
	Button btnGetCredentials;
	Button btnDone;
	EditText editPocheUrl;
	EditText editAPIUsername;
	EditText editAPIToken;
	CheckBox allCerts;
	CheckBox highContrast;
	EditText listLimit;
	TextView textViewVersion;
	EditText username;
	EditText password;
	ProgressDialog progressDialog;

    private Settings settings;
	private WallabagSettings wallabagSettings;

	private TestConnectionTask testConnectionTask;
	private GetCredentialsTask getCredentialsTask;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);

        settings = ((App) getApplication()).getSettings();
		wallabagSettings = WallabagSettings.settingsFromDisk(settings);

		if (!wallabagSettings.isValid()) {
			hideBackButtonToActionBar();
		}

		editPocheUrl = (EditText) findViewById(R.id.pocheUrl);
		editAPIUsername = (EditText) findViewById(R.id.APIUsername);
		editAPIToken = (EditText) findViewById(R.id.APIToken);
		allCerts = (CheckBox) findViewById(R.id.accept_all_certs_cb);
		highContrast = (CheckBox) findViewById(R.id.high_contrast_cb);
		listLimit = (EditText) findViewById(R.id.list_limit_number);

		editPocheUrl.setText(wallabagSettings.wallabagURL);
		editAPIUsername.setText(wallabagSettings.userID);
		editAPIToken.setText(wallabagSettings.userToken);
		allCerts.setChecked(settings.getBoolean(Settings.ALL_CERTS, false));
		highContrast.setChecked(settings.getBoolean(Settings.HIGH_CONTRAST,
				android.os.Build.MODEL.equals("NOOK")));
		listLimit.setText(String.valueOf(settings.getInt(Settings.LIST_LIMIT, 50)));

		username = (EditText) findViewById(R.id.username);
		username.setText(settings.getKey(Settings.USERNAME));
		password = (EditText) findViewById(R.id.password);
		password.setText(settings.getKey(Settings.PASSWORD));

		TextWatcher textWatcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) { }

			@Override
			public void afterTextChanged(Editable s) {
				updateButton();
			}
		};

		editPocheUrl.addTextChangedListener(textWatcher);
		editAPIUsername.addTextChangedListener(textWatcher);
		editAPIToken.addTextChangedListener(textWatcher);

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

				progressDialog.setMessage("Testing connection");
				progressDialog.show();

				testConnectionTask = new TestConnectionTask(
						SettingsActivity.this, editPocheUrl.getText().toString(),
						username.getText().toString(), password.getText().toString(),
						progressDialog);

				testConnectionTask.execute();
			}
		});

		btnGetCredentials = (Button) findViewById(R.id.btnGetFeedsCredentials);
		btnGetCredentials.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				cancelTask(getCredentialsTask);

				progressDialog.setMessage("Getting credentials");
				progressDialog.show();

				getCredentialsTask = new GetCredentialsTask(
						SettingsActivity.this, editPocheUrl.getText().toString(),
						username.getText().toString(), password.getText().toString(),
						editAPIUsername, editAPIToken, progressDialog);

				getCredentialsTask.execute();
			}
		});

		btnDone = (Button) findViewById(R.id.btnDone);
		btnDone.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				wallabagSettings.wallabagURL = editPocheUrl.getText().toString();
				wallabagSettings.userID = editAPIUsername.getText().toString();
				wallabagSettings.userToken = editAPIToken.getText().toString();

                settings.setBoolean(Settings.ALL_CERTS, allCerts.isChecked());
                settings.setBoolean(Settings.HIGH_CONTRAST, highContrast.isChecked());
                try {
                    settings.setInt(Settings.LIST_LIMIT, Integer.parseInt(listLimit.getText().toString()));
                } catch (NumberFormatException ignored) {}

                settings.setString(Settings.USERNAME, username.getText().toString());
                settings.setString(Settings.PASSWORD, password.getText().toString());

				if (wallabagSettings.isValid()) {
					wallabagSettings.save();
					finish();
				}
			}
		});

		textViewVersion = (TextView) findViewById(R.id.version);
		textViewVersion.setText(BuildConfig.VERSION_NAME);

		updateButton();
	}

	@Override
	protected void onStop() {
		super.onStop();

		cancelTasks();
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

	private void updateButton() {
		wallabagSettings.wallabagURL = editPocheUrl.getText().toString();
		wallabagSettings.userID = editAPIUsername.getText().toString();
		wallabagSettings.userToken = editAPIToken.getText().toString();

		btnDone.setEnabled(wallabagSettings.isValid());
	}
}
