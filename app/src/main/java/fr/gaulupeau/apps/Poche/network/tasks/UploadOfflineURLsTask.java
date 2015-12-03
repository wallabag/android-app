package fr.gaulupeau.apps.Poche.network.tasks;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.entity.OfflineURL;
import fr.gaulupeau.apps.Poche.entity.OfflineURLDao;
import fr.gaulupeau.apps.Poche.ui.DialogHelperActivity;

public class UploadOfflineURLsTask extends AsyncTask<Void, Integer, Boolean> {

    private String errorMessage;
    private Context context;
    private ProgressDialog progressDialog;

    private int totalUploaded, totalCount;

    public UploadOfflineURLsTask(Context context, ProgressDialog progressDialog) {
        this.context = context;
        this.progressDialog = progressDialog;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Settings settings = App.getInstance().getSettings();
        WallabagService service = new WallabagService(
                settings.getUrl(),
                settings.getKey(Settings.USERNAME),
                settings.getKey(Settings.PASSWORD));

        OfflineURLDao offlineURLDao = DbConnection.getSession().getOfflineURLDao();
        List<OfflineURL> urls = offlineURLDao.queryBuilder()
                .orderAsc(OfflineURLDao.Properties.Id).build().list();

        if(urls.isEmpty()) {
            return true;
        }

        List<OfflineURL> uploaded = new ArrayList<>(urls.size());

        int counter = 0;
        int size = urls.size();

        publishProgress(counter, size);

        boolean result = false;
        try {
            // add multithreading?

            for(OfflineURL url: urls) {
                if(isCancelled()) break;
                if(!service.addLink(url.getUrl())) {
                    if(context != null) {
                        errorMessage = context.getString(R.string.couldntUploadURL_errorMessage);
                    }
                    break;
                }

                uploaded.add(url);

                publishProgress(++counter, size);
            }

            result = true;
        } catch (IOException e) {
            errorMessage = e.getMessage();
            e.printStackTrace();
        }

        if(!uploaded.isEmpty()) {
            for(OfflineURL url: uploaded) {
                offlineURLDao.delete(url);
            }
        }

        totalUploaded = counter;
        totalCount = size;

        return result;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        if(progressDialog != null) {
            int current = progress[0];
            if(current == 0) {
                int max = progress[1];
                if(progressDialog.getMax() != max) progressDialog.setMax(max);
            }

            progressDialog.setProgress(current);
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (success) {
            if(context != null) {
                if(totalCount == 0) {
                    Toast.makeText(context, R.string.uploadURLs_nothingToUpload, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, R.string.uploadURLs_finished, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            if(context != null) {
                DialogHelperActivity.showAlertDialog(context,
                        context.getString(R.string.d_uploadURLs_title), errorMessage,
                        context.getString(R.string.ok));

                Toast.makeText(context, String.format(
                                context.getString(R.string.uploadURLs_result_text),
                                totalUploaded, totalCount),
                        Toast.LENGTH_SHORT).show();
            }
        }

        if(progressDialog != null) progressDialog.dismiss();
    }

}
