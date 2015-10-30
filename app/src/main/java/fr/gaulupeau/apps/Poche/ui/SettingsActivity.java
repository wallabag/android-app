package fr.gaulupeau.apps.Poche.ui;

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
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.WallabagSettings;

public class SettingsActivity extends BaseActionBarActivity {
	Button btnDone;
	EditText editPocheUrl;
	EditText editAPIUsername;
	EditText editAPIToken;
	CheckBox allCerts;
	CheckBox highContrast;
	TextView textViewVersion;
	EditText username;
	EditText password;

    private Settings settings;
	private WallabagSettings wallabagSettings;

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

		editPocheUrl.setText(wallabagSettings.wallabagURL);
		editAPIUsername.setText(wallabagSettings.userID);
		editAPIToken.setText(wallabagSettings.userToken);
		allCerts.setChecked(settings.getBoolean(Settings.ALL_CERTS, false));
		highContrast.setChecked(settings.getBoolean(Settings.HIGH_CONTRAST,
				android.os.Build.MODEL.equals("NOOK")));

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

		btnDone = (Button) findViewById(R.id.btnDone);
		btnDone.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				wallabagSettings.wallabagURL = editPocheUrl.getText().toString();
				wallabagSettings.userID = editAPIUsername.getText().toString();
				wallabagSettings.userToken = editAPIToken.getText().toString();

                settings.setBoolean(Settings.ALL_CERTS, allCerts.isChecked());
                settings.setBoolean(Settings.HIGH_CONTRAST, highContrast.isChecked());

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

	private void updateButton() {
		wallabagSettings.wallabagURL = editPocheUrl.getText().toString();
		wallabagSettings.userID = editAPIUsername.getText().toString();
		wallabagSettings.userToken = editAPIToken.getText().toString();

		btnDone.setEnabled(wallabagSettings.isValid());
	}
}
