package fr.gaulupeau.apps.Poche.service;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.UUID;

import fr.gaulupeau.apps.InThePoche.R;

public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_COPY_TO_CLIPBOARD = "copy_to_clipboard";

    public static final String EXTRA_COPY_TO_CLIPBOARD_LABEL = "copy_to_clipboard_label";
    public static final String EXTRA_COPY_TO_CLIPBOARD_TEXT = "copy_to_clipboard_text";

    public static PendingIntent getCopyToClipboardPendingIntent(
            Context context, String label, String text) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction(ACTION_COPY_TO_CLIPBOARD);
        intent.putExtra(EXTRA_COPY_TO_CLIPBOARD_LABEL, label);
        intent.putExtra(EXTRA_COPY_TO_CLIPBOARD_TEXT, text);
        // make the intent unique, so `PendingIntent` creates a **new** pending intent
        intent.setData(Uri.parse("uuid:" + UUID.randomUUID()));

        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(ACTION_COPY_TO_CLIPBOARD.equals(action)) {
            ClipboardManager clipboardManager = (ClipboardManager) context
                    .getSystemService(Context.CLIPBOARD_SERVICE);

            ClipData data = ClipData.newPlainText(
                    intent.getStringExtra(EXTRA_COPY_TO_CLIPBOARD_LABEL),
                    intent.getStringExtra(EXTRA_COPY_TO_CLIPBOARD_TEXT));

            clipboardManager.setPrimaryClip(data);

            Toast.makeText(context, context.getString(R.string.copiedToClipboard), Toast.LENGTH_SHORT).show();
        }
    }

}
