package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.FeedsCredentials;
import fr.gaulupeau.apps.Poche.data.Settings;

import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getHttpURL;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequest;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequestBuilder;

/**
 * Created by strubbl on 11.04.16.
 */
public class WallabagServiceEndpointV2 extends WallabagServiceEndpoint {
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
        return getCredentials("/config", "\"/(\\S+)/([a-zA-Z0-9]+)/unread.xml\"");
    }


    public WallabagServiceEndpointV2(String endpoint, String username, String password, OkHttpClient client) {
        super(endpoint, username, password, client);
    }

    protected boolean isLoginPage(String body) throws IOException {
        if(body == null || body.length() == 0) return false;

//        "<body class=\"login\">"
        return body.contains(Settings.WALLABAG_LOGIN_FORM_V2); // any way to improve?
    }

    protected boolean isRegularPage(String body) throws IOException {
        return isRegularPage(body, Settings.WALLABAG_LOGOUT_LINK_V2);
    }

    protected Request getLoginRequest(String csrfToken) throws IOException {
        HttpUrl url = getHttpURL(endpoint + "/login_check");

        // TODO: maybe move null checks somewhere else
        RequestBody formBody = new FormEncodingBuilder()
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
        Log.d(TAG, "executeRequest() start; autoRelogin: " + autoRelogin);

        Response response = exec(request);
        Log.d(TAG, "executeRequest() got response");

        if(checkResponse) super.checkResponse(response);
        String body = response.body().string();
        if(!isLoginPage(body)) return body;
        Log.d(TAG, "executeRequest() response is login page");
        if(!autoRelogin) return null;

        String csrfToken = getCsrfToken(body);
        if(csrfToken == null) return null;
        Log.d(TAG, "executeRequest() csrfToken=" + csrfToken);

        Log.d(TAG, "executeRequest() trying to re-login");
        Response loginResponse = exec(getLoginRequest(csrfToken));
        if(checkResponse) checkResponse(response);
        if(isLoginPage(loginResponse.body().string())) {
            throw new IOException(App.getInstance()
                    .getString(R.string.wrongUsernameOrPassword_errorMessage));
        }

        Log.d(TAG, "executeRequest() re-login response is OK; re-executing request");
        response = exec(request);

        if(checkResponse) checkResponse(response);
        body = response.body().string();
        return !isLoginPage(body) ? body : null;
    }

    private String getCsrfToken(String body) {
        String startCsrfTokenString = "<input type=\"hidden\" name=\"_csrf_token\" value=\"";
        int csrfTokenStartIndex = body.indexOf(startCsrfTokenString) + startCsrfTokenString.length();
        int csrfTokenEndIndex = body.indexOf("\" />", csrfTokenStartIndex);
        Log.d(TAG, "csrfTokenStartIndex=" + csrfTokenStartIndex + " and csrfTokenEndIndex=" + csrfTokenEndIndex + ", so csrfTokenLength=" + (csrfTokenEndIndex-csrfTokenStartIndex));
        if(csrfTokenStartIndex==-1 || csrfTokenEndIndex==-1){
            return null; // cannot find csrf string in the login page
        }
        String csrfToken = body.substring(csrfTokenStartIndex, csrfTokenEndIndex);
        Log.d(TAG, "csrfToken=" + csrfToken);
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
        HttpUrl url = getHttpURL(endpoint + "/archive/" + Integer.toString(articleId))
                .newBuilder()
                .build();
        return executeRequest(getRequest(url));
    }

    public boolean toggleFavorite(int articleId) throws IOException {
        Log.d(TAG, "toggleFavorite() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint + "/star/" + Integer.toString(articleId))
                .newBuilder()
                .build();
        return executeRequest(getRequest(url));
    }

    public boolean deleteArticle(int articleId) throws IOException {
        Log.d(TAG, "deleteArticle() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint + "/delete/" + Integer.toString(articleId))
                .newBuilder()
                .build();
        return executeRequest(getRequest(url));
    }
}
