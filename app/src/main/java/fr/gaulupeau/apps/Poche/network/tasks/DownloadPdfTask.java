package fr.gaulupeau.apps.Poche.network.tasks;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class DownloadPdfTask extends GenericArticleTask {

    private File exportDir;

    public DownloadPdfTask(Context context, int articleId, ArticleDao articleDao, Article article) {
        super(context, articleId, articleDao, article);
    }

    @Override
    protected Boolean doInBackgroundSimple(Void... params) throws IOException {
        if(isOffline || noCredentials || context == null) return false;

        exportDir = context.getApplicationContext().getExternalFilesDir(null);

        publishProgress(1); // report that we didn't stop because of isOffline or noCredentials

        int testConn = service.testConnection();
        Log.d(TAG, "doInBackgroundSimple() testConn=" + testConn);
        if (testConn != 0) {
            Log.w(TAG, "doInBackgroundSimple() testing connection failed with value " + testConn);
            // TODO: set errorMessage
            return false;
        }

        String articleTitle = article.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_");
        String exportType = "pdf";
        String exportUrl = service.getExportUrl(articleId, exportType);
        Log.d(TAG, "doInBackgroundSimple() exportUrl=" + exportUrl);
        final String exportFileName = articleTitle + "." + exportType;

        Request request = new Request.Builder()
                .url(exportUrl)
                .build();
        service.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.i(TAG, "callback.onFailure()", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "callback.onResponse()");

                if(!response.isSuccessful()) {
                    throw new IOException("Unexpected code: " + response);
                }

                if(Log.isLoggable(TAG, Log.DEBUG)) {
                    Headers responseHeaders = response.headers();
                    for (int i = 0, size = responseHeaders.size(); i < size; i++) {
                        Log.d(TAG, responseHeaders.name(i) + ": " + responseHeaders.value(i));
                    }
                }

                File file = new File(exportDir, exportFileName);
                Log.d(TAG, "getExportUrl() saving file " + file.getAbsolutePath());

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

                Log.d(TAG, "callback.onResponse() finished");
            }
        });

        return true; // can't really be sure
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        int current = progress[0];
        if(current >= 1) {
            String toastTextStart = String.format(
                    context.getString(R.string.downloadPdfPathStart), exportDir);
            Toast.makeText(context, toastTextStart, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        super.onPostExecute(success);

        if(success) return;

        if(isOffline && context != null) {
            Toast.makeText(context, R.string.downloadPdf_noInternetConnection, Toast.LENGTH_SHORT)
                    .show();
        }
    }

}
