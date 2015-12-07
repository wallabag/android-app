package fr.gaulupeau.apps.Poche.network.tasks;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.entity.OfflineURL;
import fr.gaulupeau.apps.Poche.entity.OfflineURLDao;
import fr.gaulupeau.apps.Poche.ui.DialogHelperActivity;

public class AddLinkTask extends AsyncTask<Void, Void, Boolean> {

    private final String url;
    private String errorMessage;
    private Context context;
    private ProgressBar progressBar;
    private ProgressDialog progressDialog; // TODO: remove

    private boolean isOffline;
    private boolean savedOffline;

    public AddLinkTask(String url, Context context) {
        this(url, context, null, null);
    }

    public AddLinkTask(String url, Context context, ProgressBar progressBar,
                       ProgressDialog progressDialog) {
        this.url = url;
        this.context = context;
        this.progressBar = progressBar;
        this.progressDialog = progressDialog;
    }

    @Override
    protected void onPreExecute() {
        if(progressBar != null) progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        boolean result = false;

        isOffline = true;

        if(WallabagConnection.isNetworkOnline()) {
            Settings settings = App.getInstance().getSettings();

            String username = settings.getKey(Settings.USERNAME);
            if(username != null && username.length() > 0) {
                isOffline = false;

                WallabagService service = new WallabagService(
                        settings.getUrl(),
                        username,
                        settings.getKey(Settings.PASSWORD));

                try {
                    if(service.addLink(url)) {
                        result = true;
                    } else if(context != null) {
                        errorMessage = context.getString(R.string.addLink_errorMessage);
                    }
                } catch (IOException e) {
                    errorMessage = e.getMessage();
                    e.printStackTrace();
                }

                if(result) return true;
            }
        }

        OfflineURLDao urlDao = DbConnection.getSession().getOfflineURLDao();
        OfflineURL offlineURL = new OfflineURL();
        offlineURL.setUrl(url);
        urlDao.insertOrReplace(offlineURL);

        savedOffline = true;

        return false;
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (success) {
            if(context != null) {
                Toast.makeText(context, R.string.addLink_success_text, Toast.LENGTH_SHORT).show();
            }
        } else {
            if(context != null) {
                if(!isOffline) {
                    showDialog(context, R.string.d_addLink_failedOnline_title, errorMessage, R.string.ok);
                } else if(!savedOffline) {
                    showDialog(context, R.string.d_addLink_failed_title, errorMessage, R.string.ok);
                }

                if(savedOffline) {
                    Toast.makeText(context, R.string.addLink_savedOffline, Toast.LENGTH_SHORT).show();
                }
            }
        }

        if(progressBar != null) progressBar.setVisibility(View.GONE);
        if(progressDialog != null) progressDialog.dismiss();
    }

    private void showDialog(Context c, int titleId, String message, int buttonId) {
        DialogHelperActivity.showAlertDialog(c, c.getString(titleId), message, c.getString(buttonId));
    }

}
