package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.FeedsCredentials;

import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getHttpURL;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequest;

/**
 * Created by strubbl on 11.04.16.
 */
public abstract class WallabagServiceEndpoint {
    private static final String TAG = WallabagServiceEndpoint.class.getSimpleName();

    protected String endpoint;
    protected final String username;
    protected final String password;
    protected OkHttpClient client;

    public WallabagServiceEndpoint(String endpoint, String username, String password, OkHttpClient client) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.client = client;
    }

    public abstract int testConnection() throws IOException;

    public abstract FeedsCredentials getCredentials() throws IOException;

    protected FeedsCredentials getCredentials(String configPath, String credentialsPattern) throws IOException {
        Log.d(TAG, "getCredentials(): configPath=" + configPath + " credentialsPattern=" + credentialsPattern);
        Request configRequest = getConfigRequest(configPath);

        String response = executeRequestForResult(configRequest);
        if(response == null) {
            Log.d(TAG, "getCredentials(): configRequest response is null");
            return null;
        }

        Pattern pattern = Pattern.compile(
                credentialsPattern,
                Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(response);
        if(!matcher.find()) {
            Log.d(TAG, "getCredentials(): matcher did not find a result, therefore trying to generate new token");
            Request generateTokenRequest = getGenerateTokenRequest();
            executeRequest(generateTokenRequest);

            response = executeRequestForResult(configRequest);
            if(response == null) {
                Log.d(TAG, "getCredentials(): configRequest response is null after token generation");
                return null;
            }

            matcher = pattern.matcher(response);
            if(!matcher.find()) {
                Log.w(TAG, "getCredentials(): still did not find a match for the feed");
                return null;
            }
        }

        FeedsCredentials credentials = new FeedsCredentials();
        credentials.userID = matcher.group(1);
        credentials.token = matcher.group(2);
        Log.d(TAG, "credentials=" + credentials);

        return credentials;
    }

    public abstract boolean addLink(String link) throws IOException;

    public abstract boolean toggleArchive(int articleId) throws IOException;

    public abstract boolean toggleFavorite(int articleId) throws IOException;

    public abstract boolean deleteArticle(int articleId) throws IOException;

    public abstract String getExportUrl(long articleId, String exportType);

    protected Response exec(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    protected boolean checkResponse(Response response) throws IOException {
        return checkResponse(response, true);
    }

    protected boolean checkResponse(Response response, boolean throwException) throws IOException {
        if(!response.isSuccessful()) {
            Log.w(TAG, "checkResponse() response is not OK; response code: " + response.code()
                    + ", response message: " + response.message());
            if(throwException)
                throw new IOException(String.format(
                        App.getInstance().getString(R.string.unsuccessfulRequest_errorMessage),
                        response.code(), response.message()
                ));

            return false;
        }

        return true;
    }

    protected abstract boolean isLoginPage(String body) throws IOException;

    protected abstract boolean isRegularPage(String body) throws IOException;

    protected boolean containsMarker(String body, String marker) throws IOException {
        return !(body == null || body.isEmpty()) && body.contains(marker);
    }

    protected abstract Request getLoginRequest(String csrfToken) throws IOException;

    protected abstract String executeRequestForResult(Request request, boolean checkResponse, boolean autoRelogin) throws IOException;

    protected Request getConfigRequest(String path) throws IOException {
        HttpUrl url = getHttpURL(endpoint + path)
                .newBuilder()
                .build();
        Log.d(TAG, "getConfigRequest() url: " + url.toString());
        return getRequest(url);
    }

    protected abstract Request getGenerateTokenRequest() throws IOException;

    protected boolean executeRequest(Request request) throws IOException {
        return executeRequest(request, true, true);
    }

    private boolean executeRequest(Request request, boolean checkResponse, boolean autoRelogin) throws IOException {
        return executeRequestForResult(request, checkResponse, autoRelogin) != null;
    }

    private String executeRequestForResult(Request request) throws IOException {
        return executeRequestForResult(request, true, true);
    }
}
