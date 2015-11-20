package fr.gaulupeau.apps.Poche.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import fr.gaulupeau.apps.InThePoche.R;

public class DialogHelperActivity extends AppCompatActivity {

    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_BUTTON_POSITIVE = "button_positive";

    public static void showConnectionFailureDialog(Context context, String message) {
        showAlertDialog(context, context.getString(R.string.d_connectionFail_title),
                message, context.getString(R.string.ok));
    }

    public static void showAlertDialog(Context context, String title, String message,
                                       String positiveButtonText) {
        Intent intent = new Intent(context, DialogHelperActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if(title != null) intent.putExtra(EXTRA_TITLE, title);
        if(message != null) intent.putExtra(EXTRA_MESSAGE, message);
        if(positiveButtonText != null) intent.putExtra(EXTRA_BUTTON_POSITIVE, positiveButtonText);

        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyProxyTheme(this);
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        String title = extras.getString(EXTRA_TITLE);
        String message = extras.getString(EXTRA_MESSAGE);
        String positiveButton = extras.getString(EXTRA_BUTTON_POSITIVE);

        AlertDialog.Builder b = new AlertDialog.Builder(this);

        if(title != null) b.setTitle(title);
        if(message != null) b.setMessage(message);
        if(positiveButton != null) b.setPositiveButton(positiveButton, null);

        b.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });

        b.show();
    }

}
