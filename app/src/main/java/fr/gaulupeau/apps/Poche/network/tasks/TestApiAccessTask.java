package fr.gaulupeau.apps.Poche.network.tasks;

import android.os.AsyncTask;
import android.util.Log;

import com.di72nn.stuff.wallabag.apiwrapper.ParameterHandler;
import com.di72nn.stuff.wallabag.apiwrapper.WallabagService;
import com.di72nn.stuff.wallabag.apiwrapper.exceptions.AuthorizationException;
import com.di72nn.stuff.wallabag.apiwrapper.exceptions.NotFoundException;
import com.di72nn.stuff.wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;
import com.di72nn.stuff.wallabag.apiwrapper.models.TokenResponse;

import fr.gaulupeau.apps.Poche.network.WallabagConnection;

public class TestApiAccessTask extends AsyncTask<Void, Void, Void> {

    public enum Result {
        OK, NO_ACCESS, NOT_FOUND, UNKNOWN_ERROR
    }

    public interface ResultHandler {
        void onTestApiAccessTaskResult(Result result, String details);
    }

    private static final String TAG = TestApiAccessTask.class.getSimpleName();

    private final String url;
    private final String username;
    private final String password;
    private final String clientID;
    private final String clientSecret;
    private final String refreshToken;
    private final String accessToken;
    private final boolean customSSLSettings;

    private final ResultHandler resultHandler;

    private Result result;
    private String details;

    public TestApiAccessTask(String url, String username, String password,
                             String clientID, String clientSecret,
                             String refreshToken, String accessToken,
                             boolean customSSLSettings, ResultHandler resultHandler) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.clientID = clientID;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.customSSLSettings = customSSLSettings;
        this.resultHandler = resultHandler;
    }

    @Override
    protected Void doInBackground(Void... params) {
        WallabagService wallabagService = new WallabagService(url, new ParameterHandler() {
            String localRefreshToken;
            String localAccessToken;

            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public String getPassword() {
                return password;
            }

            @Override
            public String getClientID() {
                return clientID;
            }

            @Override
            public String getClientSecret() {
                return clientSecret;
            }

            @Override
            public String getRefreshToken() {
                return localRefreshToken != null ? localRefreshToken : refreshToken;
            }

            @Override
            public String getAccessToken() {
                return localAccessToken != null ? localAccessToken : accessToken;
            }

            @Override
            public void tokensUpdated(TokenResponse token) {
                localRefreshToken = token.refreshToken;
                localAccessToken = token.accessToken;
            }
        }, WallabagConnection.createClient(false, customSSLSettings));

        try {
            Log.d(TAG, "doInBackground() API version: " + wallabagService.getVersion());
        } catch(NotFoundException e) {
            Log.w(TAG, "doInBackground() NotFoundException", e);
            result = Result.NOT_FOUND;
        } catch(UnsuccessfulResponseException e) {
            Log.w(TAG, "doInBackground() UnsuccessfulResponseException with body: "
                    + e.getResponseBody(), e);
            result = Result.UNKNOWN_ERROR;
            details = e.toString();
        } catch(Exception e) {
            Log.w(TAG, "doInBackground() Exception", e);
            result = Result.UNKNOWN_ERROR;
            details = e.toString();
        }

        if(result != null) return null;

        try {
            wallabagService.getArticlesBuilder().perPage(1).execute();
            result = Result.OK;
        } catch(NotFoundException e) { // no articles, but we have access
            Log.i(TAG, "doInBackground() NotFoundException (that means we have access)", e);
            result = Result.OK;
        } catch(AuthorizationException e) {
            Log.w(TAG, "doInBackground() AuthorizationException", e);
            result = Result.NO_ACCESS;
            details = e.getResponseBody();
        } catch(UnsuccessfulResponseException e) {
            Log.w(TAG, "doInBackground() UnsuccessfulResponseException with body: "
                    + e.getResponseBody(), e);
            result = Result.UNKNOWN_ERROR;
            details = e.toString();
        } catch(Exception e) {
            Log.w(TAG, "doInBackground() Exception", e);
            result = Result.UNKNOWN_ERROR;
            details = e.toString();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void ignored) {
        if(resultHandler != null) resultHandler.onTestApiAccessTaskResult(result, details);
    }

}
