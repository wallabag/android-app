package fr.gaulupeau.apps.Poche.ui.preferences;

import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.ui.BaseActionBarActivity;

public class SettingsActivity extends BaseActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        if(savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.content, new SettingsFragment())
                    .commit();
        }
    }

}
