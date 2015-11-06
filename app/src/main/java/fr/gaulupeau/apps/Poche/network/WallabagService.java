package fr.gaulupeau.apps.Poche.network;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class WallabagService {

    public static class FeedsCredentials {
        public String userID;
        public String token;
    }

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

    public String getPage(String fullUrl) throws IOException {
        return getPage(HttpUrl.parse(fullUrl));
    }

    public String getPage(HttpUrl url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        return executeRequestForResult(request);
    }

    public FeedsCredentials getCredentials() throws IOException {
        Request configRequest = getConfigRequest();

        String response = executeRequestForResult(configRequest);
        if(response == null) return null;

        Pattern pattern = Pattern.compile(
                "\"\\?feed&amp;type=home&amp;user_id=(\\d+)&amp;token=([a-zA-Z0-9]+)\"",
                Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(response);
        if(!matcher.find()) {
            Request generateTokenRequest = getGenerateTokenRequest();
            executeRequest(generateTokenRequest);

            response = executeRequestForResult(configRequest);
            if(response == null) return null;

            matcher = pattern.matcher(response);
            if(!matcher.find()) return null;
        }

        FeedsCredentials credentials = new FeedsCredentials();
        credentials.userID = matcher.group(1);
        credentials.token = matcher.group(2);

        return credentials;
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

    public boolean deleteArticle(int articleId) throws IOException {
        HttpUrl url = HttpUrl.parse(endpoint)
                .newBuilder()
                .setQueryParameter("action", "delete")
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

    private Request getConfigRequest() {
        HttpUrl url = HttpUrl.parse(endpoint)
                .newBuilder()
                .setQueryParameter("view", "config")
                .build();

        return new Request.Builder()
                .url(url)
                .build();
    }

    private Request getGenerateTokenRequest() {
        HttpUrl url = HttpUrl.parse(endpoint)
                .newBuilder()
                .setQueryParameter("feed", null)
                .setQueryParameter("action", "generate")
                .build();

        Log.d(TAG, "getGenerateTokenRequest() url: " + url.toString());

        return new Request.Builder()
                .url(url)
                .build();
    }

    private boolean executeRequest(Request request) throws IOException {
        return executeRequest(request, true, true);
    }

    private boolean executeRequest(Request request, boolean checkResponse, boolean autoRelogin) throws IOException {
        return executeRequestForResult(request, checkResponse, autoRelogin) != null;
    }

    private String executeRequestForResult(Request request) throws IOException {
        return executeRequestForResult(request, true, true);
    }

    private String executeRequestForResult(Request request, boolean checkResponse, boolean autoRelogin)
            throws IOException {
        Log.d(TAG, "executeRequest() start; autoRelogin: " + autoRelogin);

        Response response = exec(request);
        Log.d(TAG, "executeRequest() got response");

        if(checkResponse) checkResponse(response);
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
                throw new IOException(String.format(
                        App.getInstance().getString(R.string.unsuccessfulRequest_errorMessage),
                        response.code(), response.message()
                ));

            return false;
        }

        return true;
    }

    private boolean isLoginPage(String body) throws IOException {
        if(body == null || body.length() == 0) return false;

//        "<body class=\"login\">"
        return body.contains("<form method=\"post\" action=\"?login\" name=\"loginform\">"); // any way to improve?
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
