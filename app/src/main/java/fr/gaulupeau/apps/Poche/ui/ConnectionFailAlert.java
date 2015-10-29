package fr.gaulupeau.apps.Poche.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class ConnectionFailAlert {

    public static AlertDialog getDialog(Context context, String message) {
        return new AlertDialog.Builder(context)
                .setTitle("Connection Failure")
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // nop
                    }
                }).create();
    }
}
