package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.FeedsCredentials;

import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getHttpURL;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequest;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequestBuilder;

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
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("plainurl", link)
                .build();

        return executeRequest(getRequest(url));
    }

    public boolean toggleArchive(int articleId) throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_archive")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        return executeRequest(getRequest(url));
    }

    public boolean toggleFavorite(int articleId) throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_fav")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        return executeRequest(getRequest(url));
    }

    public boolean deleteArticle(int articleId) throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("action", "delete")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        return executeRequest(getRequest(url));
    }

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

    protected abstract Request getLoginRequest(String csrfToken) throws IOException;

    protected abstract String executeRequestForResult(Request request, boolean checkResponse, boolean autoRelogin) throws IOException;

    private Request getConfigRequest() throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("view", "config")
                .build();

        return getRequest(url);
    }

    private Request getGenerateTokenRequest() throws IOException {
        HttpUrl url = getHttpURL(endpoint)
                .newBuilder()
                .setQueryParameter("feed", null)
                .setQueryParameter("action", "generate")
                .build();

        Log.d(TAG, "getGenerateTokenRequest() url: " + url.toString());

        return getRequest(url);
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
}
