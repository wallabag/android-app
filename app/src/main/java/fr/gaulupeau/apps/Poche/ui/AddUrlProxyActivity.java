package fr.gaulupeau.apps.Poche.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import java.util.regex.Matcher;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.service.OperationsHelper;

public class AddUrlProxyActivity extends AppCompatActivity {

    private static final String TAG = AddUrlProxyActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyProxyTheme(this);
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        final String extraText = extras.getString(Intent.EXTRA_TEXT);
        final String pageUrl;

        // Parsing string for urls.
        Matcher matcher;
        if(extraText != null && !extraText.isEmpty()
                && (matcher = Patterns.WEB_URL.matcher(extraText)).find()) {
            pageUrl = matcher.group();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.d_add_fail_title)
                    .setMessage(getString(R.string.d_add_fail_text) + extraText)
                    .setPositiveButton(R.string.ok, null)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            finish();
                        }
                    })
                    .show();
            return;
        }

        Settings.checkFirstRunInit(this);

        Log.d(TAG, "Bagging " + pageUrl);

        OperationsHelper.addArticle(this, pageUrl);

        Toast.makeText(getApplicationContext(),
                R.string.addLink_success_text, Toast.LENGTH_SHORT).show();

        finish();
    }

}
