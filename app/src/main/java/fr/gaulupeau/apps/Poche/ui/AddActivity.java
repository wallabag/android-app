package fr.gaulupeau.apps.Poche.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.network.tasks.AddLinkTask;

public class AddActivity extends BaseActionBarActivity {

    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        final EditText pageUrl = (EditText) findViewById(R.id.page_url);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setIndeterminate(true);

        // TODO: lock button while operation is running
        // TODO: cancel operation if activity is hiding
        findViewById(R.id.add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AddLinkTask(pageUrl.getText().toString(), getApplicationContext(),
                        progressBar, null).execute();
            }
        });
    }

}
