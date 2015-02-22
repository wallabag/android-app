package fr.gaulupeau.apps.Poche;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;

public class Settings extends BaseActionBarActivity {
	Button btnDone;
	EditText editPocheUrl;
	EditText editAPIUsername;
	EditText editAPIToken;
	TextView textViewVersion;

    WallabagSettings settings;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);

        settings = WallabagSettings.settingsFromDisk(this);
        if (!settings.isValid()) {
            hideBackButtonToActionBar();
        }

		editPocheUrl = (EditText) findViewById(R.id.pocheUrl);
		editAPIUsername = (EditText) findViewById(R.id.APIUsername);
		editAPIToken = (EditText) findViewById(R.id.APIToken);
		btnDone = (Button) findViewById(R.id.btnDone);

		editPocheUrl.setText(settings.wallabagURL);
		editAPIUsername.setText(settings.userID);
		editAPIToken.setText(settings.userToken);

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
        updateButton();

		btnDone.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
                settings.wallabagURL = editPocheUrl.getText().toString();
                settings.userID = editAPIUsername.getText().toString();
                settings.userToken = editAPIToken.getText().toString();

                if (settings.isValid()) {
                    settings.save();
                    finish();
                }
			}
		});
		try {
			textViewVersion = (TextView) findViewById(R.id.version);
			textViewVersion.setText(getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName);
		} catch (Exception e) {
			//
		}
	}

    private void updateButton() {
        settings.wallabagURL = editPocheUrl.getText().toString();
        settings.userID = editAPIUsername.getText().toString();
        settings.userToken = editAPIToken.getText().toString();

        btnDone.setEnabled(settings.isValid());
    }
}
