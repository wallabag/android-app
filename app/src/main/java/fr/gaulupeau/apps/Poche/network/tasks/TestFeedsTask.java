package fr.gaulupeau.apps.Poche.network.tasks;

import android.os.AsyncTask;

public class TestFeedsTask extends AsyncTask<Void, Void, Void> {

    public enum Result {
        OK, NOT_FOUND, NO_ACCESS, UNKNOWN_ERROR
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
        return null;
    }

    @Override
    protected void onPostExecute(Void ignored) {
        if(resultHandler != null) resultHandler.testFeedsTaskOnResult(Result.OK, null);
    }

}
