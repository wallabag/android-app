package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.FeedsCredentials;

import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getHttpURL;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequest;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequestBuilder;

/**
 * Created by strubbl on 11.04.16.
 */
public class WallabagServiceEndpointV2 extends WallabagServiceEndpoint {

    public static final String WALLABAG_LOGIN_FORM_V2 = "/login_check\" method=\"post\" name=\"loginform\">";
    public static final String WALLABAG_LOGOUT_LINK_V2 = "/logout\">";
    public static final String WALLABAG_LOGO_V2 = "alt=\"wallabag logo\" />";

    private static final String CREDENTIALS_PATTERN = "\"/(\\S+)/([a-zA-Z0-9]+)/unread.xml\"";

    private static final String TAG = WallabagServiceEndpointV2.class.getSimpleName();

    public int testConnection() throws IOException {
        // TODO: detect redirects
        // TODO: check response codes prior to getting body

        HttpUrl httpUrl = HttpUrl.parse(endpoint + "/");
        if(httpUrl == null) {
            return 6;
        }
        Request testRequest = getRequest(httpUrl);

        Response response = exec(testRequest);
        if(response.code() == 401) {
            return 5; // fail because of HTTP Auth
        }

        String body = response.body().string();
        if(isRegularPage(body)) {
            return 0; // if HTTP-auth-only access control used, we should be already logged in
        }

        if(!isLoginPage(body)) {
            return 1; // it's not even wallabag login page: probably something wrong with the URL
        }

        String csrfToken = getCsrfToken(body);
        if(csrfToken == null){
            return 7; // cannot find csrf string in the login page
        }

        Request loginRequest = getLoginRequest(csrfToken);

        response = exec(loginRequest);
        body = response.body().string();

        if(isLoginPage(body)) {
//            if(body.contains("div class='messages error'"))
            return 2; // still login page: probably wrong username or password
        }

        response = exec(testRequest);
        body = response.body().string();

        if(isLoginPage(body)) {
            return 3; // login page AGAIN: weird, probably authorization problems (maybe cookies expire)
        }

        if(!isRegularPage(body)) {
            return 4; // unexpected content: expected to find "log out" button
        }

        return 0;
    }

    public FeedsCredentials getCredentials() throws IOException {
        FeedsCredentials fc = getCredentials("/config", CREDENTIALS_PATTERN);
        // overwrite userID with username because first matcher group of previous regex, which
        // should return the user name, might include the subdirectory in which wallabag is installed
        fc.userID = username;
        return fc;
    }

    public WallabagServiceEndpointV2(String endpoint, String username, String password, OkHttpClient client) {
        super(endpoint, username, password, client);
    }

    protected boolean isLoginPage(String body) throws IOException {
        return containsMarker(body, WALLABAG_LOGIN_FORM_V2) && containsMarker(body, WALLABAG_LOGO_V2);
    }

    protected boolean isRegularPage(String body) throws IOException {
        return containsMarker(body, WALLABAG_LOGOUT_LINK_V2) && containsMarker(body, WALLABAG_LOGO_V2);
    }

    protected Request getLoginRequest(String csrfToken) throws IOException {
        HttpUrl url = getHttpURL(endpoint + "/login_check");

        // TODO: maybe move null checks somewhere else
        RequestBody formBody = new FormBody.Builder()
                .add("_username", username != null ? username : "")
                .add("_password", password != null ? password : "")
                .add("_csrf_token", csrfToken != null ? csrfToken : "")
//                .add("_remember_me", "on")
                .build();

        return getRequestBuilder()
                .url(url)
                .post(formBody)
                .build();
    }

    protected String executeRequestForResult(Request request, boolean checkResponse, boolean autoRelogin)
            throws IOException {
        Log.d(TAG, "executeRequestForResult() start: url: " + request.url() + " checkResponse: " + checkResponse + " autoRelogin: " + autoRelogin);

        Response response = exec(request);
        Log.d(TAG, "executeRequestForResult() got response");

        if(checkResponse) super.checkResponse(response);
        String body = response.body().string();
        if(!isLoginPage(body)) {
            Log.d(TAG, "executeRequestForResult() already logged in, returning this response body");
            return body;
        }
        Log.d(TAG, "executeRequestForResult() response is login page");
        if(!autoRelogin) {
            Log.d(TAG, "executeRequestForResult() autoRelogin is not true, returning");
            return null;
        }

        Log.d(TAG, "executeRequestForResult() trying to re-login");
        // loading a fresh new clean login page, because otherwise we get an implicit redirect to a
        // page we want in our variable "request" directly after login. This is not what we want.
        // We want to explicitly call our request right after we are logged in.
        // TODO: check: can we use this implicit redirect to reduce number of requests?
        HttpUrl url = getHttpURL(endpoint + "/")
                .newBuilder()
                .build();
        Response loginRequest = exec(getRequest(url));
        body = loginRequest.body().string();
        if (!isLoginPage(body)) {
            Log.e(TAG, "executeRequestForResult() got no login page after requesting endpoint");
            return null;
        }
        String csrfToken = getCsrfToken(body);
        if(csrfToken == null) {
            Log.d(TAG, "executeRequestForResult() found no csrfToken in login page's body");
            return null;
        }
        Log.d(TAG, "executeRequestForResult() csrfToken=" + csrfToken);

        Response loginResponse = exec(getLoginRequest(csrfToken));
        if(checkResponse) checkResponse(loginResponse);
        if(isLoginPage(loginResponse.body().string())) {
            Log.w(TAG, "executeRequestForResult() still on login page, wrong credentials");
            throw new IOException(App.getInstance()
                    .getString(R.string.wrongUsernameOrPassword_errorMessage));
        }

        Log.d(TAG, "executeRequestForResult() re-login response is OK; re-executing request");
        response = exec(request);

        if(checkResponse) checkResponse(response);
        body = response.body().string();
        return !isLoginPage(body) ? body : null;
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

    public boolean addLink(String link) throws IOException {
        Log.d(TAG, "addLink() link=" + link);
        HttpUrl url = getHttpURL(endpoint + "/bookmarklet")
                .newBuilder()
                .setQueryParameter("url", link)
                .build();
        return executeRequest(getRequest(url));
    }

    public boolean toggleArchive(int articleId) throws IOException {
        Log.d(TAG, "toggleArchive() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint + "/archive/" + articleId)
                .newBuilder()
                .build();
        return executeRequest(getRequest(url));
    }

    public boolean toggleFavorite(int articleId) throws IOException {
        Log.d(TAG, "toggleFavorite() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint + "/star/" + articleId)
                .newBuilder()
                .build();
        return executeRequest(getRequest(url));
    }

    public boolean deleteArticle(int articleId) throws IOException {
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

    protected Request getGenerateTokenRequest() throws IOException {
        HttpUrl url = getHttpURL(endpoint + "/generate-token")
                .newBuilder()
                .build();
        Log.d(TAG, "getGenerateTokenRequest() url: " + url.toString());
        return getRequest(url);
    }
}
