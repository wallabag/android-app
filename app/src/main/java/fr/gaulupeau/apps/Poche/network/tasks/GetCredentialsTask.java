package fr.gaulupeau.apps.Poche.network.tasks;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.network.ClientCredentials;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;

public class GetCredentialsTask extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = GetCredentialsTask.class.getSimpleName();

    private ResultHandler handler;
    private final String url;
    private final String username;
    private final String password;
    private final String httpAuthUsername;
    private final String httpAuthPassword;
    private final boolean customSSLSettings;

    private ClientCredentials credentials;

    public GetCredentialsTask(ResultHandler handler, String url,
                              String username, String password,
                              String httpAuthUsername, String httpAuthPassword,
                              boolean customSSLSettings) {
        this.handler = handler;
        this.url = url;
        this.username = username;
        this.password = password;
        this.httpAuthUsername = httpAuthUsername;
        this.httpAuthPassword = httpAuthPassword;
        this.customSSLSettings = customSSLSettings;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        WallabagWebService service = new WallabagWebService(url, username, password,
                httpAuthUsername, httpAuthPassword,
                WallabagConnection.createClient(true, customSSLSettings));
        try {
            credentials = service.getApiClientCredentials();

            return credentials != null;
        } catch(RequestException | IOException e) {
            Log.e(TAG, "doInBackground() exception", e);
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if(handler != null) {
            handler.handleGetCredentialsResult(success != null && success, credentials);
        }
    }

    public interface ResultHandler {
        void handleGetCredentialsResult(boolean success, ClientCredentials credentials);
    }

}
