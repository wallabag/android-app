package fr.gaulupeau.apps.Poche.data;

import android.util.Log;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class WallabagService {

    private static String TAG = WallabagService.class.getSimpleName();

    private String endpoint;
    private final String username;
    private final String password;
    private OkHttpClient client;

    public WallabagService(String endpoint, String username, String password) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        client = WallabagConnection.getClient();
    }

    public boolean addLink(String link) throws IOException {
        HttpUrl url = HttpUrl.parse(endpoint)
                .newBuilder()
                .setQueryParameter("plainurl", link)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        return executeRequest(request);
    }

    public boolean toggleArchive(int articleId) throws IOException {
        HttpUrl url = HttpUrl.parse(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_archive")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        return executeRequest(request);
    }

    public boolean toggleFavorite(int articleId) throws IOException {
        HttpUrl url = HttpUrl.parse(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_fav")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        return executeRequest(request);
    }

    private Request getLoginRequest() {
        String url = endpoint + "/?login";

        RequestBody formBody = new FormEncodingBuilder()
                .add("login", username)
                .add("password", password)
//                .add("longlastingsession", "on")
                .build();

        return new Request.Builder()
                .url(url)
                .post(formBody)
                .build();
    }

    private boolean executeRequest(Request request) throws IOException {
        return executeRequest(request, true);
    }

    private boolean executeRequest(Request request, boolean autoRelogin) throws IOException {
        Log.d(TAG, "executeRequest() start; autoRelogin: " + autoRelogin);

//        printLoginCookie();

        Response response = exec(request);
        Log.d(TAG, "executeRequest() got response");

        checkResponse(response);
        if(!isLoginPage(response)) return true;
        Log.d(TAG, "executeRequest() response is login page");
        if(!autoRelogin) return false;

        Log.d(TAG, "executeRequest() trying to re-login");
        Response loginResponse = exec(getLoginRequest());
        checkResponse(response);
        if(isLoginPage(loginResponse)) {
            throw new IOException("Couldn't login: probably wrong username or password");
        }

//        printLoginCookie();

        Log.d(TAG, "executeRequest() re-login response is OK; re-executing request");
        response = exec(request);

        checkResponse(response);
        return !isLoginPage(response);
    }

    private Response exec(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    private boolean checkResponse(Response response) throws IOException {
        return checkResponse(response, true);
    }

    private boolean checkResponse(Response response, boolean throwException) throws IOException {
        if(!response.isSuccessful()) {
            Log.w(TAG, "checkResponse() response is not OK; response code: " + response.code()
                    + ", response message: " + response.message());
            if(throwException)
                throw new IOException("Request unsuccessful; response code: " + response.code()
                        + ", response message: " + response.message());

            return false;
        }

        return true;
    }

    private boolean isLoginPage(Response response) throws IOException {
        if(response == null) return false;

        String body = response.body().string();
        return body.contains("name=\"loginform\""); // any way to improve?
    }

    // TODO: do not print actual value
    private void printLoginCookie() throws IOException {
        Map<String, List<String>> cookies = client.getCookieHandler()
                .get(URI.create(endpoint), new HashMap<String, List<String>>(0));
        List<String> l = cookies.get("Cookie");
        if(l != null && !l.isEmpty()) {
            String loginCookie = l.get(0);
            Log.d(TAG, "cookie: " + loginCookie);
        }
    }

}
