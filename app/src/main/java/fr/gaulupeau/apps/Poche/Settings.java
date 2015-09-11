package fr.gaulupeau.apps.Poche;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;

public class Settings extends BaseActionBarActivity {
	Button btnDone;
	EditText editPocheUrl;
	EditText editAPIUsername;
	EditText editAPIToken;
	TextView textViewVersion;
    boolean nightmode;
    private CheckBox nightmode_cb;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        //11.09.2015 Nightmode
        nightmode = getIntent().getBooleanExtra("NIGHTMODE", false);
        setNightViewTheme();
        setContentView(R.layout.settings);
        //need to set the background to black separatley from setting the dark theme - why?
        if (nightmode) {
            LinearLayout ll = (LinearLayout) findViewById(R.id.settingsLinearLayout);
            ll.setBackgroundColor(Color.BLACK);
        }
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String pocheUrl = settings.getString("pocheUrl", "http://");
        String apiUsername = settings.getString("APIUsername", "");
        String apiToken = settings.getString("APIToken", "");
        boolean nightmodeOn = settings.getBoolean("Nightmode", false);
        editPocheUrl = (EditText) findViewById(R.id.pocheUrl);
        editPocheUrl.setText(pocheUrl);
        editAPIUsername = (EditText) findViewById(R.id.APIUsername);
        editAPIUsername.setText(apiUsername);
		editAPIToken = (EditText) findViewById(R.id.APIToken);
		editAPIToken.setText(apiToken);
        nightmode_cb = (CheckBox) findViewById(R.id.nightmode_cb);
        nightmode_cb.setChecked(nightmodeOn);
        btnDone = (Button) findViewById(R.id.btnDone);
        btnDone.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.putString("pocheUrl", editPocheUrl.getText().toString());
				editor.putString("APIUsername", editAPIUsername.getText().toString());
				editor.putString("APIToken", editAPIToken.getText().toString());
                editor.putBoolean("Nightmode", nightmode_cb.isChecked());
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

    private void setNightViewTheme() {
        if (nightmode) {
            this.setTheme(R.style.app_theme_dark);
        }
    }
}
