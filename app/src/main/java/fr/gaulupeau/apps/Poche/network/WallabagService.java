package fr.gaulupeau.apps.Poche.network;

import android.util.Log;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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
    private int wallabagVersion;
    private WallabagServiceEndpoint serviceEndpoint;

    public WallabagService(String endpoint, String username, String password) {
        this(endpoint, username, password,
                App.getInstance().getSettings().getInt(Settings.WALLABAG_VERSION, -1));
    }

    public WallabagService(String endpoint, String username, String password, int wallabagVersion) {
        this(endpoint, username, password, WallabagConnection.getClient(), wallabagVersion);
    }

    public WallabagService(String endpoint, String username, String password, OkHttpClient client,
                           int wallabagVersion) {
        this.endpoint = endpoint;
        this.client = client;
        this.username = username;
        this.password = password;
        this.wallabagVersion = wallabagVersion;
    }

    public OkHttpClient getClient() {
        return client;
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

    public String getExportUrl(long articleId, String exportType) throws IOException {
        return getServiceEndpoint().getExportUrl(articleId, exportType);
    }

    public int testConnection() throws IOException {
        return getServiceEndpoint().testConnection();
    }

    public int getWallabagVersion() {
        if(this.wallabagVersion == -1) {
            this.wallabagVersion = detectWallabagVersion();
        }

        return this.wallabagVersion;
    }

    private WallabagServiceEndpoint getServiceEndpoint() {
        if(serviceEndpoint == null) {
            switch(getWallabagVersion()) {
                case 1:
                    serviceEndpoint = new WallabagServiceEndpointV1(endpoint, username, password, client);
                    break;

                default:
                    Log.w(TAG, "Falling back to V2 endpoint; wallabagVersion=" + this.wallabagVersion);
                case 2:
                    serviceEndpoint = new WallabagServiceEndpointV2(endpoint, username, password, client);
                    break;
            }
        }

        return serviceEndpoint;
    }

    /**
     * try to detect the version of the wallabag instance
     * and if valid version is found, save the wallabag version to settings
     *
     * @return the version as integer, e.g. for wallabag v2 the number 2. if detection fails
     *          it returns -1
     */
    private int detectWallabagVersion() {
        int wallabagVersion = -1;
        if(isWallabagVersion2()) {
            wallabagVersion = 2;
        }
        else if(isWallabagVersion1()) {
            wallabagVersion = 1;
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
