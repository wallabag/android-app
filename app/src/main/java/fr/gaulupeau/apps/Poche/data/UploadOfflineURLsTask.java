package fr.gaulupeau.apps.Poche.data;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.entity.OfflineURL;
import fr.gaulupeau.apps.Poche.entity.OfflineURLDao;

public class UploadOfflineURLsTask extends AsyncTask<Void, Integer, Boolean> {

    private String errorMessage;
    private Activity activity;
    private ProgressDialog progressDialog;

    private int totalUploaded, totalCount;

    public UploadOfflineURLsTask(Activity activity) {
        this(activity, null);
    }

    public UploadOfflineURLsTask(Activity activity, ProgressDialog progressDialog) {
        this.activity = activity;
        this.progressDialog = progressDialog;
    }

    @Override
    protected Boolean doInBackground(Void... params) { // TODO: report progress
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
                    errorMessage = "Couldn't upload URL";
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
            if(activity != null) {
                if(totalCount == 0) {
                    Toast.makeText(activity, "Nothing to upload", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Uploading finished", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            if(activity != null) {
                new AlertDialog.Builder(activity)
                        .setTitle("Fail")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show();

                Toast.makeText(activity,
                        "Uploaded " + totalUploaded + " out of " + totalCount + " URLs",
                        Toast.LENGTH_SHORT).show();
            }
        }

        if(progressDialog != null) progressDialog.dismiss();
    }

}
