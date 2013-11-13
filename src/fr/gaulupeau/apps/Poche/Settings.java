package fr.gaulupeau.apps.Poche;

import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;
import fr.gaulupeau.apps.InThePoche.R;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class Settings extends Activity {
	Button btnDone;
	EditText editPocheUrl;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String pocheUrl = settings.getString("pocheUrl", "http://");
    	editPocheUrl = (EditText)findViewById(R.id.pocheUrl);
    	editPocheUrl.setText(pocheUrl);
    	
        btnDone = (Button)findViewById(R.id.btnDone);
        btnDone.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// close the app
				SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
	        	SharedPreferences.Editor editor = settings.edit();
	        	editor.putString("pocheUrl", editPocheUrl.getText().toString());
				editor.commit();
				finish();
			}
        });
	}
}
