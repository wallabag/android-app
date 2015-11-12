package fr.gaulupeau.apps.Poche.ui;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import java.io.IOException;
import java.util.regex.Matcher;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.WallabagService;

public class BagItProxyActivity extends AppCompatActivity {

    private static final String TAG = BagItProxyActivity.class.getSimpleName();
    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        final String extraText = extras.getString("android.intent.extra.TEXT");
        final String pageUrl;

        // Parsing string for urls.
        Matcher matcher = Patterns.WEB_URL.matcher(extraText);
        if (matcher.find()) {
            pageUrl = matcher.group();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Fail")
                    .setMessage("Couldn't find a URL in share string:\n" + extraText)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // nop
                        }
                    }).create();
            return;
        }

        Settings settings = ((App) getApplication()).getSettings();

        Log.d(TAG, "Bagging " + pageUrl);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("Bagging page");
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.show();

        new AddTask(pageUrl, settings).execute();
    }

    private class AddTask extends AsyncTask<Void, Void, Boolean> {

        private final String url;
        private final String endpoint;
        private final String username;
        private final String password;
        private String errorMessage;

        public AddTask(String url, Settings settings) {

            this.url = url;
            endpoint = settings.getUrl();
            username = settings.getKey(Settings.USERNAME);
            password = settings.getKey(Settings.PASSWORD);
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
                Toast.makeText(BagItProxyActivity.this, "Added", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(BagItProxyActivity.this)
                        .setTitle("Fail")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
            }

            Log.d(TAG, "Bagging done");
            mProgressDialog.dismiss();
            BagItProxyActivity.this.finish();
        }
    }
}
