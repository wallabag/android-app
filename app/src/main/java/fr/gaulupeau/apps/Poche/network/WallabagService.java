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
import fr.gaulupeau.apps.Poche.data.Settings;

import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getHttpURL;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequestBuilder;
import static fr.gaulupeau.apps.Poche.network.WallabagConnection.getRequest;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class WallabagService {

    private static final String TAG = WallabagService.class.getSimpleName();

    private String endpoint;
    private OkHttpClient client;
    private int wallabagMajorVersion;
    private WallabagServiceEndpoint serviceEndpoint;

    public WallabagService(String endpoint, String username, String password) {
        this(endpoint, username, password, WallabagConnection.getClient(), -1);
    }

    public WallabagService(String endpoint, String username, String password, OkHttpClient client, int wallabagMajorVersion) {
        this.endpoint = endpoint;
        this.client = client;
        this.wallabagMajorVersion = wallabagMajorVersion;

        if(this.wallabagMajorVersion == -1) {
            this.wallabagMajorVersion = guessWallabagVersion();
        }

        switch (this.wallabagMajorVersion) {
            case 1:
                serviceEndpoint = new WallabagServiceEndpointV1(endpoint, username, password, client);
                break;
            case 2:
                serviceEndpoint = new WallabagServiceEndpointV2(endpoint, username, password, client);
                break;
            default:
                Log.w(TAG, "switch default case for wallabagMajorVersion reached. this.wallabagMajorVersion=" + this.wallabagMajorVersion);
                serviceEndpoint = new WallabagServiceEndpointV1(endpoint, username, password, client);
        }
    }

    public FeedsCredentials getCredentials() throws IOException {
        return serviceEndpoint.getCredentials();
    }

    public boolean addLink(String link) throws IOException {
        return serviceEndpoint.addLink(link);
    }

    public boolean toggleArchive(int articleId) throws IOException {
        return serviceEndpoint.toggleArchive(articleId);
    }

    public boolean toggleFavorite(int articleId) throws IOException {
        return serviceEndpoint.toggleFavorite(articleId);
    }

    public boolean deleteArticle(int articleId) throws IOException {
        return serviceEndpoint.deleteArticle(articleId);
    }

    public int testConnection() throws IOException {
        return serviceEndpoint.testConnection();
    }

    /**
     * try to guess the version of the wallabag instance
     *
     * @returns the version as integer, e.g. for wallabag v2 the number 2. if detection fails
     *          it returns -1
     */
    private int guessWallabagVersion() {
        int wallabagVersion = -1;
        HttpUrl httpUrl = HttpUrl.parse(endpoint + "/?view=about");
        if(httpUrl == null) {
            return -1;
        }
        Request guessRequest = getRequest(httpUrl);
        Response response;
        try {
            response = client.newCall(guessRequest).execute();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        if(response.code() != 200) {
            return -1;
        }

        String body;
        try {
            body = response.body().string();
        } catch (NullPointerException e) {
            e.printStackTrace();
            return -1;
        }
        catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        if (body.contains(Settings.WALLABAG_LOGIN_FORM_V2)) {
            wallabagVersion = 2;
        }
        else if(body.contains(Settings.WALLABAG_LOGIN_FORM_V1)) {
            wallabagVersion = 1;
        }
        return wallabagVersion;
    }
}
