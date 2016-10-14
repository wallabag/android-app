package fr.gaulupeau.apps.Poche.network.tasks;

import android.os.AsyncTask;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.data.FeedsCredentials;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;

public class GetCredentialsTask extends AsyncTask<Void, Void, Boolean> {

    private ResultHandler handler;
    private final String endpoint;
    private final String username;
    private final String password;
    private String httpAuthUsername;
    private String httpAuthPassword;
    private boolean customSSLSettings;
    private boolean acceptAllCertificates;

    private FeedsCredentials credentials;
    private int wallabagVersion = -1;

    public GetCredentialsTask(ResultHandler handler, String endpoint,
                              String username, String password,
                              String httpAuthUsername, String httpAuthPassword,
                              boolean customSSLSettings, boolean acceptAllCertificates,
                              int wallabagVersion) {
        this.handler = handler;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.httpAuthUsername = httpAuthUsername;
        this.httpAuthPassword = httpAuthPassword;
        this.customSSLSettings = customSSLSettings;
        this.acceptAllCertificates = acceptAllCertificates;
        this.wallabagVersion = wallabagVersion;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        WallabagService service = new WallabagService(endpoint, username, password,
                httpAuthUsername, httpAuthPassword, wallabagVersion,
                WallabagConnection.createClient(false, customSSLSettings, acceptAllCertificates));
        try {
            credentials = service.getCredentials();
            wallabagVersion = service.getWallabagVersion();

            return credentials != null;
        } catch (RequestException | IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if(handler != null) {
            handler.handleGetCredentialsResult(
                    success != null && success, credentials, wallabagVersion
            );
        }
    }

    public interface ResultHandler {
        void handleGetCredentialsResult(
                boolean success, FeedsCredentials credentials, int wallabagVersion
        );
    }

}
