package fr.gaulupeau.apps.Poche.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Patterns;

import java.util.regex.Matcher;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.AddLinkTask;

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
                    .setTitle(R.string.d_bag_fail_title)
                    .setMessage(getString(R.string.d_bag_fail_text) + extraText)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            return;
        }

        Log.d(TAG, "Baging " + pageUrl);

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.d_addingToWallabag_text));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        new AddLinkTask(pageUrl, this, null, progressDialog, true).execute();
    }

}
