package fr.gaulupeau.apps.Poche.ui;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.EditText;

import fr.gaulupeau.apps.InThePoche.R;

public class AddActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        EditText pageUrl = (EditText) findViewById(R.id.page_url);


    }
}
