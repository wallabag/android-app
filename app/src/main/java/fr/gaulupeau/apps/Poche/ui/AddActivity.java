package fr.gaulupeau.apps.Poche.ui;

import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.WallabagService;

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
                new AddTask(endpoint, username, password, pageUrl.getText().toString()).execute();

            }
        });


    }

    private class AddTask extends AsyncTask<Void, Void, Boolean> {

        private final String endpoint;
        private final String username;
        private final String password;
        private final String url;
        private String errorMessage;

        public AddTask(String endpoint, String username,String password, String url) {

            this.endpoint = endpoint;
            this.username = username;
            this.password = password;
            this.url = url;
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            WallabagService service = new WallabagService(endpoint, username, password);
            try {
                service.addLink(url);
                return true;
            } catch (IOException e) {
                errorMessage = e.getMessage();
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Toast.makeText(AddActivity.this, "Added", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(AddActivity.this)
                        .setTitle("Fail")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
            }

            progressBar.setVisibility(View.GONE);
            AddActivity.this.finish();
        }
    }
}
