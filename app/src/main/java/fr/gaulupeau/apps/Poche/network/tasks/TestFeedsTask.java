package fr.gaulupeau.apps.Poche.network.tasks;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class TestFeedsTask extends AsyncTask<Void, Void, Void> {

    public enum Result {
        OK, NotFound, NoAccess, UnknownError
    }

    public interface ResultHandler {
        void testFeedsTaskOnResult(Result result, String details);
    }

    private static final String TAG = TestFeedsTask.class.getSimpleName();

    private final String endpointUrl;
    private final String feedsUserID;
    private final String feedsToken;
    private String httpAuthUsername;
    private String httpAuthPassword;
    private boolean customSSLSettings;
    private boolean acceptAllCertificates;
    private int wallabagServerVersion = -1;
    private ResultHandler resultHandler;

    private Result result;
    private String details;

    public TestFeedsTask(String endpointUrl, String feedsUserID, String feedsToken,
                         String httpAuthUsername, String httpAuthPassword,
                         boolean customSSLSettings, boolean acceptAllCertificates,
                         int wallabagServerVersion, ResultHandler resultHandler) {
        this.endpointUrl = endpointUrl;
        this.feedsUserID = feedsUserID;
        this.feedsToken = feedsToken;
        this.httpAuthUsername = httpAuthUsername;
        this.httpAuthPassword = httpAuthPassword;
        this.customSSLSettings = customSSLSettings;
        this.acceptAllCertificates = acceptAllCertificates;
        this.wallabagServerVersion = wallabagServerVersion;
        this.resultHandler = resultHandler;
    }

    @Override
    protected Void doInBackground(Void... params) {
        OkHttpClient client = WallabagConnection.createClient(
                customSSLSettings, acceptAllCertificates);
        FeedUpdater feedUpdater = new FeedUpdater(
                endpointUrl, feedsUserID, feedsToken,
                httpAuthUsername, httpAuthPassword,
                wallabagServerVersion, client);

        // TODO: more logging

        InputStream is = null;
        try {
            Response response = feedUpdater.getResponse(
                    feedUpdater.getFeedUrl(FeedUpdater.FeedType.Favorite));

            if(response.isSuccessful()) {
                is = response.body().byteStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String s = br.readLine();
                if(!"<?xml version=\"1.0\" encoding=\"utf-8\"?>".equals(s)) {
                    if("Uh, there is a problem while generating feed. Wrong token used?".equals(s)) {
                        result = Result.NoAccess;
                    } else {
                        result = Result.UnknownError;
                    }
                } else if((s = br.readLine()) == null || !s.startsWith("<rss version=\"2.0\"")) {
                    result = Result.UnknownError;
                } else {
                    result = Result.OK;
                }
            } else {
                if(response.code() == 404) {
                    result = Result.NotFound;
                } else {
                    result = Result.UnknownError;
                }
            }
        } catch(IncorrectConfigurationException | IOException e) {
            Log.d(TAG, "Exception", e);

            result = Result.UnknownError;
            details = e.getLocalizedMessage();
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch(IOException ignored) {}
            }
        }

        Log.i(TAG, "Result: " + result + (details != null ? "; details: " + details : ""));

        return null;
    }

    @Override
    protected void onPostExecute(Void ignored) {
        if(resultHandler != null) resultHandler.testFeedsTaskOnResult(result, details);
    }

}
