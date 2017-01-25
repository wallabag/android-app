package fr.gaulupeau.apps.Poche.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

public abstract class BaseActionBarActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        addBackButtonToActionBar();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Themes.checkTheme(this);
    }

    protected void addBackButtonToActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);
    }

    protected void hideBackButtonFromActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) actionBar.setDisplayHomeAsUpEnabled(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
