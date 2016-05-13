package fr.gaulupeau.apps.Poche.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class HttpSchemeHandlerActivity extends AppCompatActivity {

    private static final String TAG = HttpSchemeHandlerActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri data = getIntent().getData();
        if(data != null) {
            String url = data.toString();

            Intent addActivityIntent = new Intent(this, BagItProxyActivity.class);
            addActivityIntent.setAction(Intent.ACTION_SEND);
            addActivityIntent.putExtra(Intent.EXTRA_TEXT, url);

            startActivity(addActivityIntent);
        } else {
            Log.w(TAG, "Data is null");
        }

        finish();
    }

}
