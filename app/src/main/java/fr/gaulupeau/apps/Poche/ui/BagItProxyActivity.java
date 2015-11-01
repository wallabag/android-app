package fr.gaulupeau.apps.Poche.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Patterns;

import java.util.regex.Matcher;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.AddLinkTask;
import fr.gaulupeau.apps.Poche.data.Settings;

public class BagItProxyActivity extends AppCompatActivity {

    private static final String TAG = BagItProxyActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        final String extraText = extras.getString(Intent.EXTRA_TEXT);
        final String pageUrl;

        // Parsing string for urls.
        Matcher matcher;
        if (extraText != null && extraText.length() > 0
                && (matcher = Patterns.WEB_URL.matcher(extraText)).find()) {
            pageUrl = matcher.group();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Fail")
                    .setMessage("Couldn't find a URL in share string:\n" + extraText)
                    .setPositiveButton("OK", null)
                    .create();
            return;
        }

        Settings settings = ((App) getApplication()).getSettings();

        Log.d(TAG, "Baging " + pageUrl);

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Baging page");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        new AddLinkTask(settings.getUrl(), settings.getKey(Settings.USERNAME),
                settings.getKey(Settings.PASSWORD), pageUrl, this, null, progressDialog, true)
                .execute();
    }

}
