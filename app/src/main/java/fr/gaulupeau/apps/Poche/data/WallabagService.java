package fr.gaulupeau.apps.Poche.data;

import android.util.Log;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;

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

    public void addLink(String link) throws IOException {
        doLogin();

        HttpUrl url = HttpUrl.parse(endpoint)
                .newBuilder()
                .setQueryParameter("plainurl", link)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).execute();
    }

    private boolean doLogin() throws IOException {
        String url = endpoint+"/?login";

        RequestBody formBody = new FormEncodingBuilder()
                .add("login", username)
                .add("password", password)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        Response response = client.newCall(request).execute();

        return response.code() == 200; // TODO: detect unsuccessful login
    }

    public boolean toggleArchive(int articleId) throws IOException {
        doLogin();

        HttpUrl url = HttpUrl.parse(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_archive")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();

        Log.d(TAG, String.valueOf(response.code()));

        return response.code() == 200;
    }

    public boolean toggleFavorite(int articleId) throws IOException {
        doLogin();

        HttpUrl url = HttpUrl.parse(endpoint)
                .newBuilder()
                .setQueryParameter("action", "toggle_fav")
                .setQueryParameter("id", Integer.toString(articleId))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();

        Log.d(TAG, String.valueOf(response.code()));

        return response.code() == 200;
    }

}
