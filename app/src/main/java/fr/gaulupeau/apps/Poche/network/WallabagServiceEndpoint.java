package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import fr.gaulupeau.apps.Poche.data.FeedsCredentials;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;
import fr.gaulupeau.apps.Poche.network.exceptions.UnsuccessfulResponseException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getHttpURL;

public abstract class WallabagServiceEndpoint {

    private static final String TAG = WallabagServiceEndpoint.class.getSimpleName();

    public enum ConnectionTestResult {
        OK, INCORRECT_URL, INCORRECT_SERVER_VERSION, WALLABAG_NOT_FOUND,
        HTTP_AUTH, NO_CSRF, INCORRECT_CREDENTIALS,
        AUTH_PROBLEM, UNKNOWN_PAGE_AFTER_LOGIN
    }

    protected String endpoint;
    protected final String username;
    protected final String password;
    protected final String httpAuthUsername;
    protected final String httpAuthPassword;
    protected RequestCreator requestCreator;

    protected OkHttpClient client;

    public WallabagServiceEndpoint(String endpoint, String username, String password,
                                   String httpAuthUsername, String httpAuthPassword,
                                   RequestCreator requestCreator, OkHttpClient client) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.httpAuthUsername = httpAuthUsername;
        this.httpAuthPassword = httpAuthPassword;
        this.requestCreator = requestCreator;
        this.client = client;
    }

    public abstract ConnectionTestResult testConnection()
            throws IncorrectConfigurationException, IOException;

    public abstract FeedsCredentials getCredentials() throws RequestException, IOException;

    protected FeedsCredentials getCredentials(String configPath, String credentialsPattern)
            throws RequestException, IOException {
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

    public abstract boolean addLink(String link) throws RequestException, IOException;

    public abstract boolean toggleArchive(int articleId) throws RequestException, IOException;

    public abstract boolean toggleFavorite(int articleId) throws RequestException, IOException;

    public abstract boolean deleteArticle(int articleId) throws RequestException, IOException;

    public abstract String getExportUrl(long articleId, String exportType);

    protected Response exec(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    protected boolean checkResponse(Response response) throws RequestException {
        return checkResponse(response, true);
    }

    protected boolean checkResponse(Response response, boolean throwException)
            throws RequestException {
        if(!response.isSuccessful()) {
            Log.w(TAG, "checkResponse() response is not OK; response code: " + response.code()
                    + ", response message: " + response.message());
            if(throwException) { // TODO: throw multiple more specific exceptions?
                String responseBody = null;
                try {
                    responseBody = response.body().string();
                    Log.w(TAG, "checkResponse() response body: " + responseBody);
                } catch(IOException e) {
                    Log.d(TAG, "checkResponse() failed to get response body", e);
                }

                throw new UnsuccessfulResponseException(
                        response.code(), response.message(),
                        responseBody, response.request().url().toString());
            }

            return false;
        }

        return true;
    }

    protected abstract boolean isLoginPage(String body) throws IOException;

    protected abstract boolean isRegularPage(String body) throws IOException;

    protected boolean containsMarker(String body, String marker) {
        return !(body == null || body.isEmpty()) && body.contains(marker);
    }

    protected Request.Builder getRequestBuilder() {
        return requestCreator.getRequestBuilder();
    }

    protected Request getRequest(HttpUrl url) {
        return requestCreator.getRequest(url);
    }

    protected abstract Request getLoginRequest(String csrfToken)
            throws IncorrectConfigurationException;

    protected abstract String executeRequestForResult(
            Request request, boolean checkResponse, boolean autoRelogin)
            throws RequestException, IOException;

    protected Request getConfigRequest(String path) throws IncorrectConfigurationException {
        HttpUrl url = getHttpURL(endpoint + path)
                .newBuilder()
                .build();
        Log.d(TAG, "getConfigRequest() url: " + url.toString());
        return requestCreator.getRequest(url);
    }

    protected abstract Request getGenerateTokenRequest() throws IncorrectConfigurationException;

    protected boolean executeRequest(Request request) throws RequestException, IOException {
        return executeRequest(request, true, true);
    }

    private boolean executeRequest(Request request, boolean checkResponse, boolean autoRelogin)
            throws RequestException, IOException {
        return executeRequestForResult(request, checkResponse, autoRelogin) != null; // TODO: fix: this method does not return null anymore
    }

    private String executeRequestForResult(Request request) throws RequestException, IOException {
        return executeRequestForResult(request, true, true);
    }

}
