package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.FeedsCredentials;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectCredentialsException;
import fr.gaulupeau.apps.Poche.network.exceptions.NotAuthorizedException;
import fr.gaulupeau.apps.Poche.network.exceptions.RequestException;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getHttpURL;
import static fr.gaulupeau.apps.Poche.network.WallabagServiceEndpointV1.WALLABAG_LOGIN_FORM_V1;

public class WallabagServiceEndpointV2 extends WallabagServiceEndpoint {

    public static final String WALLABAG_LOGIN_FORM_V2 = "/login_check\" method=\"post\" name=\"loginform\">";
    public static final String WALLABAG_LOGOUT_LINK_V2 = "/logout\">";
    public static final String WALLABAG_LOGO_V2 = "alt=\"wallabag logo\" />";

    private static final String CREDENTIALS_PATTERN = "\"/(\\S+)/([a-zA-Z0-9]+)/unread.xml\"";

    private static final String TAG = WallabagServiceEndpointV2.class.getSimpleName();

    public ConnectionTestResult testConnection()
            throws IncorrectConfigurationException, IOException {
        // TODO: check response codes prior to getting body

        HttpUrl httpUrl = HttpUrl.parse(endpoint + "/");
        if(httpUrl == null) {
            Log.i(TAG, "testConnection() incorrect URL: \"" + endpoint + "/\"");
            return ConnectionTestResult.IncorrectURL;
        }
        Request testRequest = getRequest(httpUrl);

        Response response = exec(testRequest);
        if(response.code() == 401) {
            // fail because of HTTP Auth
            Log.i(TAG, "testConnection() response code: 401");
            return ConnectionTestResult.HTTPAuth;
        }

        String body = response.body().string();
        if(isRegularPage(body)) {
            // if HTTP-auth-only access control used, we should be already logged in
            Log.i(TAG, "testConnection() got regular page");
            return ConnectionTestResult.OK;
        }

        if(!isLoginPage(body)) {
            if(isLoginPageOfDifferentVersion(body)) {
                Log.i(TAG, "testConnection() found a login page of different server version");
                return ConnectionTestResult.IncorrectServerVersion;
            } else {
                // it's not even wallabag login page: probably something wrong with the URL
                Log.i(TAG, "testConnection() expected login page, got unknown response");
                Log.i(TAG, "testConnection() response code: " + response.code());
                Log.i(TAG, "testConnection() response body: " + body);
                return ConnectionTestResult.WallabagNotFound;
            }
        }

        String csrfToken = getCsrfToken(body);
        if(csrfToken == null){
            // cannot find csrf string in the login page
            Log.i(TAG, "testConnection() couldn't find CSRF token");
            Log.i(TAG, "testConnection() response body: " + body);
            return ConnectionTestResult.NoCSRF;
        }

        Request loginRequest = getLoginRequest(csrfToken);

        response = exec(loginRequest);
        body = response.body().string();

        if(isLoginPage(body)) {
//            if(body.contains("div class='messages error'"))
            // still login page: probably wrong username or password
            Log.i(TAG, "testConnection() got login page as a result for a login attempt");
            return ConnectionTestResult.IncorrectCredentials;
        }

        response = exec(testRequest);
        body = response.body().string();

        if(isLoginPage(body)) {
            // login page AGAIN: weird, probably authorization problems (maybe cookies expire)
            // usually caused by redirects:
            // HTTP -> HTTPS -- guaranteed
            // other (hostname -> www.hostname) -- maybe
            Log.i(TAG, "testConnection() got login page again - after a successful login");
            return ConnectionTestResult.AuthProblem;
        }

        if(!isRegularPage(body)) {
            // unexpected content: expected to find "log out" button
            Log.i(TAG, "testConnection() expected regular page, got unknown response");
            Log.i(TAG, "testConnection() response code: " + response.code());
            Log.i(TAG, "testConnection() response body: " + body);
            return ConnectionTestResult.UnknownPageAfterLogin;
        }

        Log.i(TAG, "testConnection() test finished successfully");
        return ConnectionTestResult.OK;
    }

    public FeedsCredentials getCredentials() throws RequestException, IOException {
        FeedsCredentials fc = getCredentials("/config", CREDENTIALS_PATTERN);
        // overwrite userID with username because first matcher group of previous regex, which
        // should return the user name, might include the subdirectory in which wallabag is installed
        if(fc != null) fc.userID = username;
        return fc;
    }

    public WallabagServiceEndpointV2(String endpoint, String username, String password,
                                     RequestCreator requestCreator, OkHttpClient client) {
        super(endpoint, username, password, requestCreator, client);
    }

    protected boolean isLoginPage(String body) {
        return containsMarker(body, WALLABAG_LOGIN_FORM_V2) && containsMarker(body, WALLABAG_LOGO_V2);
    }

    protected boolean isRegularPage(String body) throws IOException {
        return containsMarker(body, WALLABAG_LOGOUT_LINK_V2) && containsMarker(body, WALLABAG_LOGO_V2);
    }

    private boolean isLoginPageOfDifferentVersion(String body) {
        return containsMarker(body, WALLABAG_LOGIN_FORM_V1);
    }

    protected Request getLoginRequest(String csrfToken) throws IncorrectConfigurationException {
        HttpUrl url = getHttpURL(endpoint + "/login_check");

        RequestBody formBody = new FormBody.Builder()
                .add("_username", username != null ? username : "")
                .add("_password", password != null ? password : "")
                .add("_csrf_token", csrfToken != null ? csrfToken : "")
                .add("_remember_me", "on")
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

    protected String executeRequestForResult(
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
        String startCsrfTokenString = "<input type=\"hidden\" name=\"_csrf_token\" value=\"";

        int csrfTokenStartIndex = body.indexOf(startCsrfTokenString);
        if(csrfTokenStartIndex == -1) {
            Log.d(TAG, "getCsrfToken() can't find start");
            return null;
        }
        csrfTokenStartIndex += startCsrfTokenString.length();

        int csrfTokenEndIndex = body.indexOf("\" />", csrfTokenStartIndex);
        if(csrfTokenEndIndex == -1) {
            Log.d(TAG, "getCsrfToken() can't find end");
            return null;
        }

        Log.d(TAG, "getCsrfToken() csrfTokenStartIndex=" + csrfTokenStartIndex
                + " and csrfTokenEndIndex=" + csrfTokenEndIndex
                + ", so csrfTokenLength=" + (csrfTokenEndIndex - csrfTokenStartIndex));

        String csrfToken = body.substring(csrfTokenStartIndex, csrfTokenEndIndex);
        Log.d(TAG, "getCsrfToken() csrfToken=" + csrfToken);

        return csrfToken;
    }

    public boolean addLink(String link) throws RequestException, IOException {
        Log.d(TAG, "addLink() link=" + link);
        HttpUrl url = getHttpURL(endpoint + "/bookmarklet")
                .newBuilder()
                .setQueryParameter("url", link)
                .build();
        return executeRequest(getRequest(url));
    }

    public boolean toggleArchive(int articleId) throws RequestException, IOException {
        Log.d(TAG, "toggleArchive() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint + "/archive/" + articleId)
                .newBuilder()
                .build();
        return executeRequest(getRequest(url));
    }

    public boolean toggleFavorite(int articleId) throws RequestException, IOException {
        Log.d(TAG, "toggleFavorite() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint + "/star/" + articleId)
                .newBuilder()
                .build();
        return executeRequest(getRequest(url));
    }

    public boolean deleteArticle(int articleId) throws RequestException, IOException {
        Log.d(TAG, "deleteArticle() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint + "/delete/" + articleId)
                .newBuilder()
                .build();
        return executeRequest(getRequest(url));
    }

    public String getExportUrl(long articleId, String exportType) {
        Log.d(TAG, "getExportUrl() articleId=" + articleId + " exportType=" + exportType);
        String exportUrl = endpoint + "/export/" + articleId + "." + exportType;
        Log.d(TAG, "getExportUrl() exportUrl=" + exportUrl);
        return exportUrl;
    }

    protected Request getGenerateTokenRequest() throws IncorrectConfigurationException {
        HttpUrl url = getHttpURL(endpoint + "/generate-token")
                .newBuilder()
                .build();
        Log.d(TAG, "getGenerateTokenRequest() url: " + url.toString());
        return getRequest(url);
    }

}
