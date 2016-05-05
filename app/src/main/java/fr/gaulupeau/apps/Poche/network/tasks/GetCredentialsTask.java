package fr.gaulupeau.apps.Poche.network.tasks;

import android.os.AsyncTask;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.data.FeedsCredentials;
import fr.gaulupeau.apps.Poche.network.WallabagService;

public class GetCredentialsTask extends AsyncTask<Void, Void, Boolean> {

    private ResultHandler handler;
    private final String endpoint;
    private final String username;
    private final String password;

    private FeedsCredentials credentials;
    private int wallabagVersion = -1;

    public GetCredentialsTask(ResultHandler handler, String endpoint,
                              String username, String password) {
        this.handler = handler;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        WallabagService service = new WallabagService(endpoint, username, password, -1);
        try {
            credentials = service.getCredentials();
            wallabagVersion = service.getWallabagVersion();

            return credentials != null;
        } catch (IOException e) {
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
                Boolean success, FeedsCredentials credentials, int wallabagVersion
        );
    }

}
