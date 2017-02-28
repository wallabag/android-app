package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectCredentialsException;
import fr.gaulupeau.apps.Poche.network.exceptions.NotAuthorizedException;
import fr.gaulupeau.apps.Poche.network.exceptions.UnsuccessfulResponseException;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WallabagWebService {

    private static final String TAG = WallabagWebService.class.getSimpleName();

    public static final String WALLABAG_LOGIN_FORM_V2 = "/login_check\" method=\"post\" name=\"loginform\">";
    private static final String WALLABAG_LOGOUT_LINK_V2 = "/logout\">";
    private static final String WALLABAG_LOGO_V2 = "alt=\"wallabag logo\" />";
    private static final String WALLABAG_LOGIN_FORM_V1 = "<form method=\"post\" action=\"?login\" name=\"loginform\">";

    private static final String CLIENT_NAME = "Android app";

    private static final String CLIENT_PATTERN =
            "<div class=\"collapsible-header\">([^<]+?)</div>" +
                    ".*?<td><strong><code>([^<]+?)</code></strong></td>" +
                    ".*?<td><strong><code>([^<]+?)</code></strong></td>" +
                    ".*?<td><strong><code>([^<]+?)</code></strong></td>" +
                    ".*?<td><strong><code>([^<]+?)</code></strong></td>" +
                    ".*?/developer/client/delete/";

    public enum ConnectionTestResult {
        OK, INCORRECT_URL, UNSUPPORTED_SERVER_VERSION, WALLABAG_NOT_FOUND,
        HTTP_AUTH, NO_CSRF, INCORRECT_CREDENTIALS,
        AUTH_PROBLEM, UNKNOWN_PAGE_AFTER_LOGIN
    }

    private final String endpoint;
    private final OkHttpClient client;
    private final String username, password;
    private final RequestCreator requestCreator;

    public static WallabagWebService fromSettings(Settings settings) {
        return new WallabagWebService(settings.getUrl(),
                settings.getUsername(), settings.getPassword(),
                settings.getHttpAuthUsername(), settings.getHttpAuthPassword(),
                WallabagConnection.createClient());
    }

    public WallabagWebService(String endpoint, String username, String password,
                              String httpAuthUsername, String httpAuthPassword,
                              OkHttpClient client) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.client = client;
        this.requestCreator = new RequestCreator(httpAuthUsername, httpAuthPassword);
    }

    public OkHttpClient getClient() {
        return client;
    }

    public ConnectionTestResult testConnection()
            throws IncorrectConfigurationException, IOException {
        // TODO: check response codes prior to getting body

        HttpUrl httpUrl = HttpUrl.parse(endpoint + "/");
        if(httpUrl == null) {
            return ConnectionTestResult.INCORRECT_URL;
        }
        Request testRequest = getRequest(httpUrl);

        Response response = exec(testRequest);
        if(response.code() == 401) {
            response.close();
            // fail because of HTTP Auth
            return ConnectionTestResult.HTTP_AUTH;
        }

        String body = response.body().string();
        if(isRegularPage(body)) {
            // if HTTP-auth-only access control used, we should be already logged in
            return ConnectionTestResult.OK;
        }

        if(!isLoginPage(body)) {
            if(isLoginPageOfDifferentVersion(body)) {
                return ConnectionTestResult.UNSUPPORTED_SERVER_VERSION;
            } else {
                // it's not even wallabag login page: probably something wrong with the URL
                return ConnectionTestResult.WALLABAG_NOT_FOUND;
            }
        }

        String csrfToken = getCsrfToken(body);
        if(csrfToken == null){
            // cannot find csrf string in the login page
            return ConnectionTestResult.NO_CSRF;
        }

        response = exec(getLoginRequest(csrfToken));
        body = response.body().string();

        if(isLoginPage(body)) {
//            if(body.contains("div class='messages error'"))
            // still login page: probably wrong username or password
            return ConnectionTestResult.INCORRECT_CREDENTIALS;
        }

        response = exec(testRequest);
        body = response.body().string();

        if(isLoginPage(body)) {
            // login page AGAIN: weird, probably authorization problems (maybe cookies expire)
            // usually caused by redirects:
            // HTTP -> HTTPS -- guaranteed
            // other (hostname -> www.hostname) -- maybe
            return ConnectionTestResult.AUTH_PROBLEM;
        }

        if(!isRegularPage(body)) {
            // unexpected content: expected to find "log out" button
            return ConnectionTestResult.UNKNOWN_PAGE_AFTER_LOGIN;
        }

        return ConnectionTestResult.OK;
    }

    public ClientCredentials getApiClientCredentials() throws RequestException, IOException {
        ClientCredentials clientCredentials = findApiClientCredentials(false);

        if(clientCredentials == null) {
            Log.i(TAG, "getApiClientCredentials() no acceptable API client found, trying to create a new one");
            createApiClient(); // TODO: optimize: this method implicitly gets credentials right after creation

            clientCredentials = findApiClientCredentials(true);
        }

        return clientCredentials;
    }

    private ClientCredentials findApiClientCredentials(boolean desperate)
            throws RequestException, IOException {
        String configPath = "/config";
        Log.d(TAG, "getApiClientCredentials() configPath: " + configPath);

        String response = executeRequestForResult(getDeveloperPageRequest());
        if(response == null) {
            Log.d(TAG, "getApiClientCredentials() configRequest response is null");
            return null;
        }

        Pattern pattern = Pattern.compile(
                CLIENT_PATTERN,
                Pattern.DOTALL
        );

        String lastClientID = null;
        String lastClientSecret = null;
        boolean found = desperate;

        Matcher matcher = pattern.matcher(response);
        while(matcher.find()) {
            String clientName = matcher.group(1);
            lastClientID = matcher.group(2);
            lastClientSecret = matcher.group(3);
//            String redirectURIs = matcher.group(4);
//            String grantTypesAllowed = matcher.group(5);

            Log.d(TAG, "getApiClientCredentials() found client: " + clientName);

            if(clientName != null && clientName.startsWith(CLIENT_NAME + " - #")) {
                found = true;
                break;
            }
        }

        if(found && lastClientID != null) {
            ClientCredentials credentials = new ClientCredentials();
            credentials.clientID = lastClientID;
            credentials.clientSecret = lastClientSecret;

            Log.d(TAG, "getApiClientCredentials() client ID: " + credentials.clientID);

            return credentials;
        }

        return null;
    }

    private boolean createApiClient() throws RequestException, IOException {
        Request createClientPageRequest = getCreateClientPageRequest();
        String createClientPage = executeRequestForResult(createClientPageRequest);

        String token = findSubstring(
                "<input type=\"hidden\" id=\"client__token\" name=\"client[_token]\" value=\"", "\" />",
                createClientPage);

        return token != null && executeRequest(getCreateClientRequest(CLIENT_NAME, token));
    }

    private String findSubstring(String startMarker, String endMarker, String s) {
        int startIndex = s.indexOf(startMarker);
        if(startIndex < 0) return null;
        startIndex += startMarker.length();

        int endIndex = s.indexOf(endMarker, startIndex);
        if(endIndex < 0) return null;

        return s.substring(startIndex, endIndex);
    }

    private Response exec(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    private boolean checkResponse(Response response) throws RequestException {
        return checkResponse(response, true);
    }

    private boolean checkResponse(Response response, boolean throwException)
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

    private boolean isLoginPage(String body) {
        return containsMarker(body, WALLABAG_LOGIN_FORM_V2) && containsMarker(body, WALLABAG_LOGO_V2);
    }

    private boolean isRegularPage(String body) throws IOException {
        return containsMarker(body, WALLABAG_LOGOUT_LINK_V2) && containsMarker(body, WALLABAG_LOGO_V2);
    }

    private boolean isLoginPageOfDifferentVersion(String body) {
        return containsMarker(body, WALLABAG_LOGIN_FORM_V1);
    }

    private boolean containsMarker(String body, String marker) {
        return !(body == null || body.isEmpty()) && body.contains(marker);
    }

    private Request.Builder getRequestBuilder() {
        return requestCreator.getRequestBuilder();
    }

    private Request getRequest(HttpUrl url) {
        return requestCreator.getRequest(url);
    }

    private Request getLoginRequest(String csrfToken) throws IncorrectConfigurationException {
        HttpUrl url = getHttpURL(endpoint + "/login_check");

        RequestBody formBody = new FormBody.Builder()
                .add("_username", username != null ? username : "")
                .add("_password", password != null ? password : "")
                .add("_csrf_token", csrfToken != null ? csrfToken : "")
                .build();

        return getRequestBuilder()
                .url(url)
                .post(formBody)
                .build();
    }

    private Request getCleanLoginPageRequest() throws IncorrectConfigurationException {
        HttpUrl url = getHttpURL(endpoint + "/")
                .newBuilder()
                .build();

        return getRequest(url);
    }

    private String executeRequestForResult(
            Request request, boolean checkResponse, boolean autoRelogin)
            throws RequestException, IOException {
        Log.d(TAG, String.format(
                "executeRequestForResult(url: %s, checkResponse: %s, autoRelogin: %s) started",
                request.url(), checkResponse, autoRelogin));

        Response response = exec(request);
        Log.d(TAG, "executeRequestForResult() got response");

        if(checkResponse) checkResponse(response);
        String body = response.body().string();
        if(!isLoginPage(body)) {
            Log.d(TAG, "executeRequestForResult() already logged in, returning this response body");
            return body;
        }
        Log.d(TAG, "executeRequestForResult() response is login page");
        if(!autoRelogin) {
            Log.d(TAG, "executeRequestForResult() autoRelogin is not set, throwing exception");
            throw new NotAuthorizedException("Not authorized");
        }

        Log.d(TAG, "executeRequestForResult() trying to re-login");
        // loading a fresh new clean login page, because otherwise we get an implicit redirect to a
        // page we want in our variable "request" directly after login. This is not what we want.
        // We want to explicitly call our request right after we are logged in.
        // TODO: check: can we use this implicit redirect to reduce number of requests?
        Response loginPageResponse = exec(getCleanLoginPageRequest());
        body = loginPageResponse.body().string();
        if (!isLoginPage(body)) {
            Log.w(TAG, "executeRequestForResult() got no login page after requesting endpoint");
            throw new RequestException("Got no login page when expected it");
        }
        String csrfToken = getCsrfToken(body);
        if(csrfToken == null) {
            Log.w(TAG, "executeRequestForResult() found no csrfToken in login page's body");
            throw new RequestException("CSRF token was not found on login page");
        }
        Log.d(TAG, "executeRequestForResult() csrfToken=" + csrfToken);

        Response loginResponse = exec(getLoginRequest(csrfToken));
        if(checkResponse) checkResponse(loginResponse);
        if(isLoginPage(loginResponse.body().string())) {
            Log.w(TAG, "executeRequestForResult() still on login page -- incorrect credentials");
            throw new IncorrectCredentialsException(App.getInstance()
                    .getString(R.string.wrongUsernameOrPassword_errorMessage));
        }

        Log.d(TAG, "executeRequestForResult() re-login response is OK; re-executing request");
        response = exec(request);

        if(checkResponse) checkResponse(response);
        body = response.body().string();
        if(isLoginPage(body)) {
            Log.w(TAG, "executeRequestForResult() login page AGAIN; throwing exception");
            throw new RequestException("Unstable login session");
        }

        Log.d(TAG, "executeRequestForResult() finished; returning page body");
        return body;
    }

    private String getCsrfToken(String body) {
        return findSubstring("<input type=\"hidden\" name=\"_csrf_token\" value=\"", "\" />", body);
    }

    private Request getPathRequest(String path) throws IncorrectConfigurationException {
        return getRequest(getHttpURL(endpoint + path).newBuilder().build());
    }

    private Request getDeveloperPageRequest() throws IncorrectConfigurationException {
        return getPathRequest("/developer");
    }

    private Request getCreateClientPageRequest() throws IncorrectConfigurationException {
        return getPathRequest("/developer/client/create");
    }

    private Request getCreateClientRequest(String clientName, String token)
            throws IncorrectConfigurationException {
        HttpUrl url = getHttpURL(endpoint + "/developer/client/create")
                .newBuilder()
                .build();

        RequestBody formBody = new FormBody.Builder()
                .add("client[name]", clientName)
                .add("client[redirect_uris]", "")
                .add("client[save]", "")
                .add("client[_token]", token)
                .build();

        return getRequestBuilder()
                .url(url)
                .post(formBody)
                .build();
    }

    private boolean executeRequest(Request request) throws RequestException, IOException {
        return executeRequest(request, true, true);
    }

    private boolean executeRequest(Request request, boolean checkResponse, boolean autoRelogin)
            throws RequestException, IOException {
        // TODO: fix: this method does not return null anymore
        return executeRequestForResult(request, checkResponse, autoRelogin) != null;
    }

    private String executeRequestForResult(Request request) throws RequestException, IOException {
        return executeRequestForResult(request, true, true);
    }

    private static HttpUrl getHttpURL(String url) throws IncorrectConfigurationException {
        HttpUrl httpUrl = HttpUrl.parse(url);

        if(httpUrl == null) throw new IncorrectConfigurationException("Incorrect URL");

        return httpUrl;
    }

}
