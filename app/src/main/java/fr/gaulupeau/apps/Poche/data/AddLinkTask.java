package fr.gaulupeau.apps.Poche.data;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.IOException;

public class AddLinkTask extends AsyncTask<Void, Void, Boolean> {

    private final String endpoint;
    private final String username;
    private final String password;
    private final String url;
    private String errorMessage;
    private Activity activity;
    private ProgressBar progressBar;
    private ProgressDialog progressDialog;
    private boolean finishActivity;

    public AddLinkTask(String endpoint, String username, String password, String url,
                       Activity activity, ProgressBar progressBar, ProgressDialog progressDialog,
                       boolean finishActivity) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.url = url;
        this.activity = activity;
        this.progressBar = progressBar;
        this.progressDialog = progressDialog;
        this.finishActivity = finishActivity;
    }

    @Override
    protected void onPreExecute() {
        if(progressBar != null) progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        WallabagService service = new WallabagService(endpoint, username, password);
        try {
            if(service.addLink(url)) return true;

            errorMessage = "Couldn't add link";
            return true;
        } catch (IOException e) {
            errorMessage = e.getMessage();
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (success) {
            if(activity != null) Toast.makeText(activity, "Added", Toast.LENGTH_SHORT).show();
        } else {
            if(activity != null)
                new AlertDialog.Builder(activity)
                        .setTitle("Fail")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show();
        }

        if(progressBar != null) progressBar.setVisibility(View.GONE);
        if(progressDialog != null) progressDialog.dismiss();
        if(activity != null && finishActivity) activity.finish();
    }

}
