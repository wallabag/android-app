package fr.gaulupeau.apps.Poche.data;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;

import java.io.IOException;

public class TestConnectionTask extends AsyncTask<Void, Void, Boolean> {

    private Context context;
    private final String endpoint;
    private final String username;
    private final String password;
    private ProgressDialog progressDialog;
    private String errorMessage;

    public TestConnectionTask(Context context, String endpoint, String username, String password,
                              ProgressDialog progressDialog) {
        this.context = context;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.progressDialog = progressDialog;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        WallabagService service = new WallabagService(endpoint, username, password);
        try {
            String page = service.getPage(endpoint + "/?view=about");
            if(page != null) return true;

            errorMessage = "Couldn't access wallabag";
            return true;
        } catch (IOException e) {
            errorMessage = e.getMessage();
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if(progressDialog != null) progressDialog.dismiss();

        if (success) {
            if(context != null)
                new AlertDialog.Builder(context)
                        .setTitle("Success")
                        .setMessage("Connection test was successful")
                        .setPositiveButton("OK", null)
                        .show();
        } else {
            if(context != null)
                new AlertDialog.Builder(context)
                        .setTitle("Fail")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show();
        }
    }

}
