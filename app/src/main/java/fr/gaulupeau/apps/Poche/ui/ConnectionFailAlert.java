package fr.gaulupeau.apps.Poche.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

import fr.gaulupeau.apps.InThePoche.R;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class ConnectionFailAlert {

    public static AlertDialog getDialog(Context context, String message) {
        return new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.d_connectionFail_title))
                .setMessage(message)
                .setPositiveButton(context.getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // nop
                    }
                }).create();
    }
}
