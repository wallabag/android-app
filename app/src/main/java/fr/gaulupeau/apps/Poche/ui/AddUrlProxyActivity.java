package fr.gaulupeau.apps.Poche.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import java.util.regex.Matcher;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;

public class AddUrlProxyActivity extends AppCompatActivity {

    public static final String PARAM_ORIGIN_URL = "origin_url";

    private static final String TAG = AddUrlProxyActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        final String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);

        String foundUrl;

        // Parsing string for urls.
        Matcher matcher;
        if(extraText != null && !extraText.isEmpty()
                && (matcher = Patterns.WEB_URL.matcher(extraText)).find()) {
            foundUrl = matcher.group();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.d_add_fail_title)
                    .setMessage(getString(R.string.d_add_fail_text) + extraText)
                    .setPositiveButton(R.string.ok, null)
                    .setOnDismissListener(dialog -> finish())
                    .show();
            return;
        }

        boolean showDialog = !Settings.checkFirstRunInit(this);

        if (showDialog) {
            showDialog = App.getInstance().getSettings().isShowArticleAddedDialog();
        }

        String originUrl = intent.getStringExtra(PARAM_ORIGIN_URL);

        Log.d(TAG, "Bagging: " + foundUrl + ", origin: " + originUrl);

        OperationsHelper.addArticle(this, foundUrl, originUrl);

        if (showDialog) {
            Intent editActivityIntent = new Intent(this, EditAddedArticleActivity.class);
            editActivityIntent.putExtra(EditAddedArticleActivity.PARAM_ARTICLE_URL, foundUrl);

            startActivity(editActivityIntent);
        } else {
            Toast.makeText(getApplicationContext(),
                    R.string.addLink_success_text, Toast.LENGTH_SHORT).show();
        }

        finish();
    }

}
