package fr.gaulupeau.apps.Poche;

import fr.gaulupeau.apps.InThePoche.R;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class Settings extends Activity {
	Button btnDone;
	EditText editPocheUrl;
	EditText editAPIUsername;
	EditText editAPIToken;
	Preference preference;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);

		preference = new Preference(this);
		editPocheUrl = (EditText)findViewById(R.id.pocheUrl);
		editPocheUrl.setText(preference.getPocheUrl());
		editAPIUsername = (EditText)findViewById(R.id.APIUsername);
		editAPIUsername.setText(preference.getApiUsername());
		editAPIToken = (EditText)findViewById(R.id.APIToken);
		editAPIToken.setText(preference.getApiToken());
		btnDone = (Button)findViewById(R.id.btnDone);
		btnDone.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				preference.setPocheUrl(editPocheUrl.getText().toString());
				preference.setApiUsername(editAPIUsername.getText().toString());
				preference.setApiToken(editAPIToken.getText().toString());
				preference.save();
				finish();
			}
        });
	}
}
