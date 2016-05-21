package fr.gaulupeau.apps.Poche.network.tasks;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class DownloadPdfTask extends GenericArticleTask {

    protected static String TAG = DownloadPdfTask.class.getSimpleName();

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private int notificationID = 1337; // TODO: fix?

    private String exportDir;

    private File resultFile;

    public DownloadPdfTask(Context context, int articleId, ArticleDao articleDao, Article article,
                           String exportDir) {
        super(context, articleId, articleDao, article);
        this.exportDir = exportDir;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        if(context == null) return;

        notificationManager = (NotificationManager)context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        notificationBuilder = new NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.downloadPdfPathStart)).setContentText(context.getString(R.string.downloadPdfProgress))
                .setSmallIcon(R.drawable.ic_action_refresh);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(String.format(context.getString(R.string.downloadPdfProgressDetail),article.getTitle().replaceAll("[^a-zA-Z0-9.-]", " ")));
        notificationBuilder.setStyle(inboxStyle);

    }

    @Override
    protected Boolean doInBackgroundSimple(Void... params) throws IOException {
        if(isOffline || noCredentials) return false;

        publishProgress(1); // report that we didn't stop because of isOffline or noCredentials

        int testConn = service.testConnection();
        Log.d(TAG, "doInBackgroundSimple() testConn=" + testConn);
        if (testConn != 0) {
            Log.w(TAG, "doInBackgroundSimple() testing connection failed with value " + testConn);
            if(context != null) {
                errorMessage = context.getString(R.string.d_connectionFail_title);
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
            }
            return false;
        }

        String articleTitle = article.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_");
        String exportType = "pdf";
        String exportUrl = service.getExportUrl(articleId, exportType);
        Log.d(TAG, "doInBackgroundSimple() exportUrl=" + exportUrl);
        String exportFileName = articleTitle + "." + exportType;

        Request request = new Request.Builder()
                .url(exportUrl)
                .build();

        Response response = service.getClient().newCall(request).execute();

        if(!response.isSuccessful()) {
            throw new IOException("Unexpected code: " + response);
        }

        // do we need it?
        if(Log.isLoggable(TAG, Log.DEBUG)) {
            Headers responseHeaders = response.headers();
            for (int i = 0, size = responseHeaders.size(); i < size; i++) {
                Log.d(TAG, responseHeaders.name(i) + ": " + responseHeaders.value(i));
            }
        }

        File file = new File(exportDir, exportFileName);
        Log.d(TAG, "Saving file " + file.getAbsolutePath());

        BufferedSink sink = null;
        try {
            sink = Okio.buffer(Okio.sink(file));
            sink.writeAll(response.body().source());
        } catch(Exception e) {
            Log.w(TAG, e);
        } finally {
            if(sink != null) {
                try {
                    sink.close();
                } catch(IOException ignored) {}
            }
            response.body().close();
        }

        resultFile = file;

        Log.d(TAG, "doInBackgroundSimple finished");

        return true;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate();

        if(context == null) return;

        int current = progress[0];
        if(current == 1) {
            notificationManager.notify(notificationID,
                    notificationBuilder.setProgress(1, 0, true).build());
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        super.onPostExecute(success);

        if(context == null) return;

        if(success) {
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(resultFile), "application/pdf");

            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, 0);
            notificationBuilder.setContentTitle(context.getString(R.string.downloadPdfArticleDownloaded)).setContentText(context.getString(R.string.downloadPdfTouchToOpen))
                    .setContentIntent(contentIntent).setProgress(0, 0, false);

            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(String.format(context.getString(R.string.downloadPdfArticleDownloadedDetail),article.getTitle().replaceAll("[^a-zA-Z0-9.-]", " ")));
            notificationBuilder.setStyle(inboxStyle);

            notificationManager.notify(notificationID, notificationBuilder.build());
        } else {
            notificationManager.cancel(notificationID);

            if(isOffline) {
                Toast.makeText(
                        context, R.string.downloadPdf_noInternetConnection, Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

}
