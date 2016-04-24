package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.FeedsCredentials;
import fr.gaulupeau.apps.Poche.data.Settings;

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
    private Settings settings;

    public WallabagService(String endpoint, String username, String password) {
        this(endpoint, username, password, WallabagConnection.getClient(), -1);
    }

    public WallabagService(String endpoint, String username, String password, OkHttpClient client, int wallabagMajorVersion) {
        this.endpoint = endpoint;
        this.client = client;
        this.wallabagMajorVersion = wallabagMajorVersion;
        this.settings = App.getInstance().getSettings();

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
     * and if valid version is found, save the wallabag version to settings
     *
     * @returns the version as integer, e.g. for wallabag v2 the number 2. if detection fails
     *          it returns -1
     */
    private int guessWallabagVersion() {
        int wallabagVersion = -1;
        if(isWallabagVersion2()) {
            wallabagVersion = 2;
        }
        else if(isWallabagVersion1()) {
            wallabagVersion = 1;
        }

        // save version as setting if valid version found
        if(wallabagVersion > 0) {
            Log.d(TAG, "guessWallabagVersion() saving Settings.WALLABAG_VERSION=" + wallabagVersion);
            settings.setInt(Settings.WALLABAG_VERSION, wallabagVersion);
        }
        else {
            Log.w(TAG,"guessWallabagVersion() couldn't guess Wallabag version");
        }

        return wallabagVersion;
    }

    private boolean isWallabagVersion2() {
        String body = getBodyFromHttpResponse(endpoint + "/api/version");
        if (body != null
                && !body.isEmpty()
                && body.startsWith("\"2")) {
            Log.d(TAG, "isWallabagVersion2() found Wallabag v2");
            return true;
        }
        Log.d(TAG, "isWallabagVersion2() did not find Wallabag v2");
        return false;
    }

    private boolean isWallabagVersion1() {
        String body = getBodyFromHttpResponse(endpoint + "/?view=about");
        if (body != null
                && !body.isEmpty()
                && (body.contains(Settings.WALLABAG_LOGOUT_LINK_V1) || body.contains(Settings.WALLABAG_LOGIN_FORM_V1))
                ) {
            Log.d(TAG, "isWallabagVersion1() found Wallabag v1");
            return true;
        }
        Log.d(TAG, "isWallabagVersion1() did not find Wallabag v1");
        return false;
    }

    private String getBodyFromHttpResponse(String url) {
        Log.d(TAG, "getBodyFromHttpResponse() url=" + url);
        HttpUrl httpUrl = HttpUrl.parse(url);
        if(httpUrl == null) {
            return "";
        }
        Request guessRequest = getRequest(httpUrl);
        Response response;
        try {
            response = client.newCall(guessRequest).execute();
        } catch (IOException e) {
            Log.d(TAG, "getBodyFromHttpResponse() IOException");
            e.printStackTrace();
            return "";
        }
        if(response.code() != 200) {
            Log.d(TAG, "getBodyFromHttpResponse() response.code not 200");
            return "";
        }
        String body = "";
        try {
            body = response.body().string();
        } catch (NullPointerException e) {
            Log.d(TAG, "getBodyFromHttpResponse() NullPointerException");
            e.printStackTrace();
        }
        catch (IOException e) {
            Log.d(TAG, "getBodyFromHttpResponse() IOException");
            e.printStackTrace();
        }
        return body;
    }
}
