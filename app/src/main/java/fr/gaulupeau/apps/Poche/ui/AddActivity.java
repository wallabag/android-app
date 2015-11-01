package fr.gaulupeau.apps.Poche.ui;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.AddLinkTask;
import fr.gaulupeau.apps.Poche.data.Settings;

public class AddActivity extends AppCompatActivity {

    ProgressBar progressBar;
    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        final EditText pageUrl = (EditText) findViewById(R.id.page_url);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setIndeterminate(true);

        settings = ((App) getApplication()).getSettings();

        findViewById(R.id.add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String endpoint = settings.getUrl();
                String username = settings.getKey(Settings.USERNAME);
                String password = settings.getKey(Settings.PASSWORD);
                new AddLinkTask(endpoint, username, password, pageUrl.getText().toString(),
                        AddActivity.this, progressBar, null, true).execute();

            }
        });


    }

}
