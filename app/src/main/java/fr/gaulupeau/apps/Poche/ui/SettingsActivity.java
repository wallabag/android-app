package fr.gaulupeau.apps.Poche.ui;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatSpinner;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.WallabagSettings;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.tasks.GetCredentialsTask;
import fr.gaulupeau.apps.Poche.network.tasks.TestConnectionTask;

public class SettingsActivity extends BaseActionBarActivity {
	Button btnTestConnection;
	Button btnGetCredentials;
	Button btnDone;
	EditText editPocheUrl;
	EditText editAPIUsername;
	EditText editAPIToken;
	CheckBox allCerts;
	AppCompatSpinner themeChooser;
	AppCompatSpinner fontSizeChooser;
	CheckBox serifFont;
	EditText listLimit;
	TextView textViewVersion;
	EditText username;
	EditText password;
	EditText httpAuthUsername;
	EditText httpAuthPassword;
	ProgressDialog progressDialog;

    private Settings settings;
	private WallabagSettings wallabagSettings;

	private TestConnectionTask testConnectionTask;
	private GetCredentialsTask getCredentialsTask;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		Themes.applyTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);

		settings = App.getInstance().getSettings();
		wallabagSettings = WallabagSettings.settingsFromDisk(settings);

		if (!wallabagSettings.isValid()) {
			hideBackButtonToActionBar();
		}

		editPocheUrl = (EditText) findViewById(R.id.pocheUrl);
		editAPIUsername = (EditText) findViewById(R.id.APIUsername);
		editAPIToken = (EditText) findViewById(R.id.APIToken);
		allCerts = (CheckBox) findViewById(R.id.accept_all_certs_cb);
		themeChooser = (AppCompatSpinner) findViewById(R.id.themeChooser);
		fontSizeChooser = (AppCompatSpinner) findViewById(R.id.fontSizeChooser);
		serifFont = (CheckBox) findViewById(R.id.ui_font_serif);
		listLimit = (EditText) findViewById(R.id.list_limit_number);

		editPocheUrl.setText(wallabagSettings.wallabagURL);
		editPocheUrl.setSelection(editPocheUrl.getText().length());
		editAPIUsername.setText(wallabagSettings.userID);
		editAPIToken.setText(wallabagSettings.userToken);
		allCerts.setChecked(settings.getBoolean(Settings.ALL_CERTS, false));

		Themes.Theme[] themes = Themes.Theme.values();
		String[] themeOptions = new String[themes.length];
		Themes.Theme currentThemeName = Themes.getCurrentTheme();
		int currentThemeIndex = 0;
		for(int i = 0; i < themes.length; i++) {
			if(themes[i] == currentThemeName) currentThemeIndex = i;
			themeOptions[i] = getString(themes[i].getNameId());
		}
		themeChooser.setAdapter(new ArrayAdapter<>(
				this, android.R.layout.simple_spinner_item, themeOptions));
		themeChooser.setSelection(currentThemeIndex);

		fontSizeChooser.setAdapter(ArrayAdapter.createFromResource(this,
				R.array.settings_ui_fontSizeOptions, android.R.layout.simple_spinner_item));
		fontSizeChooser.setSelection(settings.getInt(Settings.FONT_SIZE, 1)); // TODO: fix default value

		serifFont.setChecked(settings.getBoolean(Settings.SERIF_FONT, false));
		listLimit.setText(String.valueOf(settings.getInt(Settings.LIST_LIMIT, 50)));

		username = (EditText) findViewById(R.id.username);
		username.setText(settings.getKey(Settings.USERNAME));
		password = (EditText) findViewById(R.id.password);
		password.setText(settings.getKey(Settings.PASSWORD));

		httpAuthUsername = (EditText) findViewById(R.id.http_auth_username);
		httpAuthUsername.setText(settings.getKey(Settings.HTTP_AUTH_USERNAME));
		httpAuthPassword = (EditText) findViewById(R.id.http_auth_password);
		httpAuthPassword.setText(settings.getKey(Settings.HTTP_AUTH_PASSWORD));

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

				progressDialog.setMessage(getString(R.string.settings_testingConnection));
				progressDialog.show();

				applyHttpAuth();

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

				progressDialog.setMessage(getString(R.string.settings_gettingCredentials));
				progressDialog.show();

				applyHttpAuth();

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
				Themes.Theme selectedTheme = Themes.Theme.values()[themeChooser.getSelectedItemPosition()];
				settings.setString(Settings.THEME, selectedTheme.toString());
				settings.setInt(Settings.FONT_SIZE, fontSizeChooser.getSelectedItemPosition());
				settings.setBoolean(Settings.SERIF_FONT, serifFont.isChecked());
                try {
                    settings.setInt(Settings.LIST_LIMIT, Integer.parseInt(listLimit.getText().toString()));
                } catch (NumberFormatException ignored) {}

                settings.setString(Settings.USERNAME, username.getText().toString());
                settings.setString(Settings.PASSWORD, password.getText().toString());

				applyHttpAuth();

				settings.setString(Settings.HTTP_AUTH_USERNAME, httpAuthUsername.getText().toString());
				settings.setString(Settings.HTTP_AUTH_PASSWORD, httpAuthPassword.getText().toString());

				if (wallabagSettings.isValid()) {
					wallabagSettings.save();
					finish();

					if(selectedTheme != Themes.getCurrentTheme()) {
						Themes.init();

						Intent intent = new Intent(SettingsActivity.this, ArticlesListActivity.class);
						intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						startActivity(intent);
					}
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

	private void cancelTasks() {
		cancelTask(testConnectionTask);
		cancelTask(getCredentialsTask);
	}

	private void cancelTask(AsyncTask task) {
		if(task != null) {
			task.cancel(true);
		}
	}

	private void applyHttpAuth() {
		String username = httpAuthUsername.getText().toString();
		String password = httpAuthPassword.getText().toString();

		WallabagConnection.setBasicAuthCredentials(username, password);
	}

	// TODO: remove?
	private void updateButton() {
		wallabagSettings.wallabagURL = editPocheUrl.getText().toString();
		wallabagSettings.userID = editAPIUsername.getText().toString();
		wallabagSettings.userToken = editAPIToken.getText().toString();

		btnDone.setEnabled(wallabagSettings.isValid());
	}
}
