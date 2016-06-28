package fr.gaulupeau.apps.Poche.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.service.ServiceHelper;

public class AddActivity extends BaseActionBarActivity {

    private ProgressBar progressBar;
    private EditText pageUrlEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        pageUrlEditText = (EditText) findViewById(R.id.page_url);

        // TODO: get rid of the progress bar
//        progressBar = (ProgressBar) findViewById(R.id.progressBar);
//        progressBar.setIndeterminate(true);
    }

    public void addButtonClicked(View view) {
        // TODO: check url

        ServiceHelper.addLink(this, pageUrlEditText.getText().toString());

        // TODO: add feedback?
    }

}
