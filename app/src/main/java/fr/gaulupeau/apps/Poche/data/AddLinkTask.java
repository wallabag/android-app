package fr.gaulupeau.apps.Poche.data;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.entity.OfflineURL;
import fr.gaulupeau.apps.Poche.entity.OfflineURLDao;

public class AddLinkTask extends AsyncTask<Void, Void, Boolean> {

    private final String url;
    private String errorMessage;
    private Activity activity;
    private ProgressBar progressBar;
    private ProgressDialog progressDialog;
    private boolean finishActivity;

    private boolean isOffline;
    private boolean savedOffline;

    public AddLinkTask(String url, Activity activity) {
        this(url, activity, null, null, false);
    }

    public AddLinkTask(String url, Activity activity, ProgressBar progressBar,
                       ProgressDialog progressDialog, boolean finishActivity) {
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
        boolean result = false;

        if(WallabagConnection.isNetworkOnline()) {
            Settings settings = App.getInstance().getSettings();
            WallabagService service = new WallabagService(
                    settings.getUrl(),
                    settings.getKey(Settings.USERNAME),
                    settings.getKey(Settings.PASSWORD));

            try {
                if(service.addLink(url)) {
                    result = true;
                } else if(activity != null) {
                    errorMessage = activity.getString(R.string.addLink_errorMessage);
                }
            } catch (IOException e) {
                errorMessage = e.getMessage();
                e.printStackTrace();
            }

            if(result) return true;
        } else {
            isOffline = true;
        }

        OfflineURLDao urlDao = DbConnection.getSession().getOfflineURLDao();
        OfflineURL offlineURL = new OfflineURL();
        offlineURL.setUrl(url);
        urlDao.insert(offlineURL);

        savedOffline = true;

        return false;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (success) {
            if(activity != null) {
                Toast.makeText(activity, R.string.addLink_success_text, Toast.LENGTH_SHORT).show();
            }
        } else {
            if(activity != null) {
                if(!isOffline) {
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.d_addLink_failedOnline_title)
                            .setMessage(errorMessage)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                } else if(!savedOffline) {
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.d_addLink_failed_title)
                            .setMessage(errorMessage)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                }

                if(savedOffline) {
                    Toast.makeText(activity, R.string.addLink_savedOffline, Toast.LENGTH_SHORT).show();
                }
            }
        }

        if(progressBar != null) progressBar.setVisibility(View.GONE);
        if(progressDialog != null) progressDialog.dismiss();
        if(activity != null && finishActivity) activity.finish();
    }

}
