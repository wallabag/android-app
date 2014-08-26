package fr.gaulupeau.apps.Poche;

import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;
import fr.gaulupeau.apps.InThePoche.R;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class Settings extends Activity {
	Button btnDone;
	EditText editPocheUrl;
	EditText editAPIUsername;
	EditText editAPIToken;
	EditText editGlobalToken;
	TextView textViewVersion;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			try {
				getActionBar().setDisplayHomeAsUpEnabled(true);
			} catch (Exception e) {
				//
			}
		}

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String pocheUrl = settings.getString("pocheUrl", "http://");
        String apiUsername = settings.getString("APIUsername", "");
        String apiToken = settings.getString("APIToken", "");
    	editPocheUrl = (EditText)findViewById(R.id.pocheUrl);
    	editPocheUrl.setText(pocheUrl);
    	editAPIUsername = (EditText)findViewById(R.id.APIUsername);
    	editAPIUsername.setText(apiUsername);
    	editAPIToken = (EditText)findViewById(R.id.APIToken);
    	editAPIToken.setText(apiToken);
        btnDone = (Button)findViewById(R.id.btnDone);
        btnDone.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
	        	SharedPreferences.Editor editor = settings.edit();
	        	editor.putString("pocheUrl", editPocheUrl.getText().toString());
	        	editor.putString("APIUsername", editAPIUsername.getText().toString());
	        	editor.putString("APIToken", editAPIToken.getText().toString());
				editor.commit();
				finish();
			}
        });
		try {
			textViewVersion = (TextView) findViewById(R.id.version);
			textViewVersion.setText(getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName);
		} catch (Exception e) {
			//
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				this.finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
}
