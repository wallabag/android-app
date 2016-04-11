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
import fr.gaulupeau.apps.Poche.data.Settings;

import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getHttpURL;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequest;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequestBuilder;

/**
 * Created by strubbl on 11.04.16.
 */
public class WallabagServiceEndpointV1 extends WallabagServiceEndpoint {
    private static final String TAG = WallabagServiceEndpointV1.class.getSimpleName();

    public WallabagServiceEndpointV1(String endpoint, String username, String password, OkHttpClient client) {
        super(endpoint, username, password, client);
    }


    public int testConnection() throws IOException {
        // TODO: detect redirects
        // TODO: check response codes prior to getting body

        HttpUrl httpUrl = HttpUrl.parse(endpoint + "/?view=about");
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

        Request loginRequest = getLoginRequest();

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

    protected boolean isLoginPage(String body) throws IOException {
        if(body == null || body.length() == 0) return false;

        return body.contains(Settings.WALLABAG_LOGIN_FORM_V1);
    }

    protected boolean isRegularPage(String body) throws IOException {
        if(body == null || body.length() == 0) return false;

        return body.contains("href=\"./?logout\"");
    }

    private Request getLoginRequest() throws IOException {
        return getLoginRequest("");
    }

    protected Request getLoginRequest(String csrfToken) throws IOException {
        HttpUrl url = getHttpURL(endpoint + "/?login");

        // TODO: maybe move null checks somewhere else
        RequestBody formBody = new FormEncodingBuilder()
                .add("login", username != null ? username : "")
                .add("password", password != null ? password : "")
//                .add("longlastingsession", "on")
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

        Log.d(TAG, "executeRequest() trying to re-login");
        Response loginResponse = exec(getLoginRequest());
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
}
