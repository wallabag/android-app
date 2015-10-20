package fr.gaulupeau.apps.Poche.ui;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;

public class SettingsActivity extends BaseActionBarActivity {
	Button btnDone;
	EditText editPocheUrl;
	EditText editAPIUsername;
	EditText editAPIToken;
	TextView textViewVersion;
	EditText username;
	EditText password;

    private Settings settings;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);

        settings = ((App) getApplication()).getSettings();

		String pocheUrl = settings.getKey(Settings.URL);
		String apiUsername = settings.getKey(Settings.USER_ID);
		String apiToken = settings.getKey(Settings.TOKEN);

		editPocheUrl = (EditText) findViewById(R.id.pocheUrl);
		editPocheUrl.setText(pocheUrl == null ? "http://" : pocheUrl);
		editAPIUsername = (EditText) findViewById(R.id.APIUsername);
		editAPIUsername.setText(apiUsername);
		editAPIToken = (EditText) findViewById(R.id.APIToken);
		editAPIToken.setText(apiToken);

		username = (EditText) findViewById(R.id.username);
		username.setText(settings.getKey(Settings.USERNAME));
		password = (EditText) findViewById(R.id.password);
		password.setText(settings.getKey(Settings.PASSWORD));

		btnDone = (Button) findViewById(R.id.btnDone);
		btnDone.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
                settings.setString(Settings.URL, editPocheUrl.getText().toString());
                settings.setString(Settings.USER_ID, editAPIUsername.getText().toString());
                settings.setString(Settings.TOKEN, editAPIToken.getText().toString());
                settings.setString(Settings.USERNAME, username.getText().toString());
                settings.setString(Settings.PASSWORD, password.getText().toString());
				finish();
			}
		});

		textViewVersion = (TextView) findViewById(R.id.version);
		textViewVersion.setText(BuildConfig.VERSION_NAME);

	}
}
