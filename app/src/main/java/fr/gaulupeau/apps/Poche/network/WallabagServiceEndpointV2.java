package fr.gaulupeau.apps.Poche.network;

import com.squareup.okhttp.OkHttpClient;

/**
 * Created by strubbl on 11.04.16.
 */
public class WallabagServiceEndpointV2 extends WallabagServiceEndpoint {
    public WallabagServiceEndpointV2(String endpoint, String username, String password, OkHttpClient client) {
        super(endpoint, username, password, client);
    }
}
