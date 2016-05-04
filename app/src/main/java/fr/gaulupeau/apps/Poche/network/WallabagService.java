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
import static fr.gaulupeau.apps.Poche.network.WallabagServiceEndpointV1.WALLABAG_LOGIN_FORM_V1;
import static fr.gaulupeau.apps.Poche.network.WallabagServiceEndpointV1.WALLABAG_LOGOUT_LINK_V1;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class WallabagService {

    private static final String TAG = WallabagService.class.getSimpleName();

    private String endpoint;
    private OkHttpClient client;
    private String username, password;
    private int wallabagMajorVersion;
    private WallabagServiceEndpoint serviceEndpoint;
    private Settings settings;

    public WallabagService(String endpoint, String username, String password) {
        this(endpoint, username, password, WallabagConnection.getClient(), -1);
    }

    public WallabagService(String endpoint, String username, String password, OkHttpClient client,
                           int wallabagMajorVersion) {
        this.endpoint = endpoint;
        this.client = client;
        this.username = username;
        this.password = password;
        this.wallabagMajorVersion = wallabagMajorVersion;
        this.settings = App.getInstance().getSettings();
    }

    public FeedsCredentials getCredentials() throws IOException {
        return getServiceEndpoint().getCredentials();
    }

    public boolean addLink(String link) throws IOException {
        return getServiceEndpoint().addLink(link);
    }

    public boolean toggleArchive(int articleId) throws IOException {
        return getServiceEndpoint().toggleArchive(articleId);
    }

    public boolean toggleFavorite(int articleId) throws IOException {
        return getServiceEndpoint().toggleFavorite(articleId);
    }

    public boolean deleteArticle(int articleId) throws IOException {
        return getServiceEndpoint().deleteArticle(articleId);
    }

    public int testConnection() throws IOException {
        return getServiceEndpoint().testConnection();
    }

    private WallabagServiceEndpoint getServiceEndpoint() {
        if(serviceEndpoint == null) {
            if(this.wallabagMajorVersion == -1) {
                this.wallabagMajorVersion = guessWallabagVersion();
            }

            switch (this.wallabagMajorVersion) {
                case 2:
                    serviceEndpoint = new WallabagServiceEndpointV2(endpoint, username, password, client);
                    break;

                default:
                    Log.w(TAG, "Falling back to V1 endpoint; wallabagMajorVersion=" + this.wallabagMajorVersion);
                case 1:
                    serviceEndpoint = new WallabagServiceEndpointV1(endpoint, username, password, client);
                    break;
            }
        }

        return serviceEndpoint;
    }

    /**
     * try to guess the version of the wallabag instance
     * and if valid version is found, save the wallabag version to settings
     *
     * @return the version as integer, e.g. for wallabag v2 the number 2. if detection fails
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

        // TODO: this should be done once on set up; also it shouldn't be saved _here_
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
                && (body.contains(WALLABAG_LOGIN_FORM_V1) || body.contains(WALLABAG_LOGOUT_LINK_V1))
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
            Log.i(TAG, "getBodyFromHttpResponse() IOException", e);
            return "";
        }
        if(response.code() != 200) {
            Log.d(TAG, "getBodyFromHttpResponse() response.code is not OK: " + response.code());
            return "";
        }
        String body = "";
        try {
            body = response.body().string();
        } catch (NullPointerException e) {
            Log.d(TAG, "getBodyFromHttpResponse() NullPointerException", e);
        } catch (IOException e) {
            Log.d(TAG, "getBodyFromHttpResponse() IOException", e);
        }
        return body;
    }
}
