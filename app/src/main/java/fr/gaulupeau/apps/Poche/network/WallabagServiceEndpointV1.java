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
import static fr.gaulupeau.apps.Poche.network.WallabagServiceEndpointV2.WALLABAG_LOGIN_FORM_V2;
import static fr.gaulupeau.apps.Poche.network.WallabagServiceEndpointV2.WALLABAG_LOGO_V2;

public class WallabagServiceEndpointV1 extends WallabagServiceEndpoint {

    public static final String WALLABAG_LOGIN_FORM_V1 = "<form method=\"post\" action=\"?login\" name=\"loginform\">";
    public static final String WALLABAG_LOGOUT_LINK_V1 = "href=\"./?logout\"";

    private static final String CREDENTIALS_PATTERN = "\"\\?feed&amp;type=home&amp;user_id=(\\d+)&amp;token=([a-zA-Z0-9]+)\"";

    private static final String TAG = WallabagServiceEndpointV1.class.getSimpleName();

    public WallabagServiceEndpointV1(String endpoint, String username, String password,
                                     RequestCreator requestCreator,
                                     OkHttpClient client) {
        super(endpoint, username, password, requestCreator, client);
    }

    public ConnectionTestResult testConnection()
            throws IncorrectConfigurationException, IOException {
        // TODO: check response codes prior to getting body

        HttpUrl httpUrl = HttpUrl.parse(endpoint + "/?view=about");
        if(httpUrl == null) {
            return ConnectionTestResult.IncorrectURL;
        }
        Request testRequest = getRequest(httpUrl);

        Response response = exec(testRequest);
        if(response.code() == 401) {
            // fail because of HTTP Auth
            return ConnectionTestResult.HTTPAuth;
        }

        String body = response.body().string();
        if(isRegularPage(body)) {
            // if HTTP-auth-only access control used, we should be already logged in
            return ConnectionTestResult.OK;
        }

        if(!isLoginPage(body)) {
            if(isLoginPageOfDifferentVersion(body)) {
                return ConnectionTestResult.IncorrectServerVersion;
            } else {
                // it's not even wallabag login page: probably something wrong with the URL
                return ConnectionTestResult.WallabagNotFound;
            }
        }

        Request loginRequest = getLoginRequest();

        response = exec(loginRequest);
        body = response.body().string();

        if(isLoginPage(body)) {
//            if(body.contains("div class='messages error'"))
            // still login page: probably wrong username or password
            return ConnectionTestResult.IncorrectCredentials;
        }

        response = exec(testRequest);
        body = response.body().string();

        if(isLoginPage(body)) {
            // login page AGAIN: weird, probably authorization problems (maybe cookies expire)
            return ConnectionTestResult.AuthProblem;
        }

        if(!isRegularPage(body)) {
            // unexpected content: expected to find "log out" button
            return ConnectionTestResult.UnknownPageAfterLogin;
        }

        return ConnectionTestResult.OK;
    }

    public FeedsCredentials getCredentials() throws RequestException, IOException {
        return getCredentials("/?view=config", CREDENTIALS_PATTERN);
    }

    protected boolean isLoginPage(String body) {
        return !(body == null || body.isEmpty()) && body.contains(WALLABAG_LOGIN_FORM_V1);
    }

    protected boolean isRegularPage(String body) throws IOException {
        return containsMarker(body, WALLABAG_LOGOUT_LINK_V1);
    }

    private boolean isLoginPageOfDifferentVersion(String body) {
        return containsMarker(body, WALLABAG_LOGIN_FORM_V2) && containsMarker(body, WALLABAG_LOGO_V2);
    }

    private Request getLoginRequest() throws IncorrectConfigurationException {
        return getLoginRequest(null);
    }

    protected Request getLoginRequest(String unused) throws IncorrectConfigurationException {
        HttpUrl url = getHttpURL(endpoint + "/?login");

        RequestBody formBody = new FormBody.Builder()
                .add("login", username != null ? username : "")
                .add("password", password != null ? password : "")
                .add("longlastingsession", "on")
                .build();

        return getRequestBuilder()
                .url(url)
                .post(formBody)
                .build();
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
        Response loginResponse = exec(getLoginRequest());
        if(checkResponse) checkResponse(response);
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

    public boolean addLink(String link) throws RequestException, IOException {
        Log.d(TAG, "addLink() link=" + link);
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("plainurl", link)
                .build();

        return executeRequest(getRequest(url));
    }

    public boolean toggleArchive(int articleId) throws RequestException, IOException {
        Log.d(TAG, "toggleArchive() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_archive")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        return executeRequest(getRequest(url));
    }

    public boolean toggleFavorite(int articleId) throws RequestException, IOException {
        Log.d(TAG, "toggleFavorite() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_fav")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        return executeRequest(getRequest(url));
    }

    public boolean deleteArticle(int articleId) throws RequestException, IOException {
        Log.d(TAG, "deleteArticle() articleId=" + articleId);
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("action", "delete")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        return executeRequest(getRequest(url));
    }

    public String getExportUrl(long articleId, String exportType) {
        Log.d(TAG, "getExportUrl() articleId=" + articleId + " exportType=" + exportType);
        String exportUrl = endpoint + "/?" + exportType + "&method=id&value=" + articleId;
        Log.d(TAG, "getExportUrl() exportUrl=" + exportUrl);
        return exportUrl;
    }

    protected Request getGenerateTokenRequest() throws IncorrectConfigurationException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("feed", null)
                .setQueryParameter("action", "generate")
                .build();

        Log.d(TAG, "getGenerateTokenRequest() url: " + url.toString());

        return getRequest(url);
    }

}
