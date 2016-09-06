package fr.gaulupeau.apps.Poche.network;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Request;

public class RequestCreator {

    private String basicHttpAuthCredentials;

    public RequestCreator(String httpAuthUsername, String httpAuthPassword) {
        setBasicHttpAuthCredentials(httpAuthUsername, httpAuthPassword);
    }

    public void setBasicHttpAuthCredentials(String username, String password) {
        if((username == null || username.isEmpty())
                && (password == null || password.isEmpty())) {
            basicHttpAuthCredentials = null;
        } else {
            basicHttpAuthCredentials = Credentials.basic(username, password);
        }
    }

    public Request.Builder getRequestBuilder() {
        Request.Builder b = new Request.Builder();

        // we use this method instead of OkHttpClient.setAuthenticator()
        // to save time (also traffic and power) on 401 responses
        if(basicHttpAuthCredentials != null) b.header("Authorization", basicHttpAuthCredentials);

        return b;
    }

    public Request getRequest(HttpUrl url) {
        return getRequestBuilder().url(url).build();
    }

}
