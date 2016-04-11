package fr.gaulupeau.apps.Poche.network;

import com.squareup.okhttp.OkHttpClient;

/**
 * Created by strubbl on 11.04.16.
 */
public class WallabagServiceEndpointV1 extends WallabagServiceEndpoint {
    public WallabagServiceEndpointV1(String endpoint, String username, String password, OkHttpClient client) {
        super(endpoint, username, password, client);
    }
}
