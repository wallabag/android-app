package fr.gaulupeau.apps.Poche.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.facebook.stetho.okhttp.StethoInterceptor;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;

/**
 * @author Victor Häggqvist
 * @since 10/20/15
 */
public class WallabagConnection {

    private static String basicAuthCredentials;

    public static void init(App app) {
        Settings settings = app.getSettings();

        setBasicAuthCredentials(
                settings.getString(Settings.HTTP_AUTH_USERNAME, null),
                settings.getString(Settings.HTTP_AUTH_PASSWORD, null)
        );
    }

    public static void setBasicAuthCredentials(String username, String password) {
        if((username == null || username.length() == 0)
                && (password == null || password.length() == 0)) {
            basicAuthCredentials = null;
        } else {
            basicAuthCredentials = Credentials.basic(username, password);
        }
    }

    public static Request.Builder getRequestBuilder() {
        Request.Builder b = new Request.Builder();

        // we use this method instead of OkHttpClient.setAuthenticator()
        // to save time on 401 responses
        if(basicAuthCredentials != null) b.header("Authorization", basicAuthCredentials);

        return b;
    }

    public static Request getRequest(HttpUrl url) {
        return getRequestBuilder().url(url).build();
    }

    public static HttpUrl getHttpURL(String url) throws IOException {
        HttpUrl httpUrl = HttpUrl.parse(url);

        if(httpUrl == null) throw new IOException("Illegal URL");

        return httpUrl;
    }

    public static boolean isNetworkOnline() {
        ConnectivityManager cm = (ConnectivityManager) App.getInstance()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    public static OkHttpClient getClient() {
        if (Holder.client != null)
            return Holder.client;

        OkHttpClient client = createClient();

        Holder.client = client;
        return client;
    }

    public static OkHttpClient createClient() {
        OkHttpClient client = new OkHttpClient();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            client.setCookieHandler(cookieManager);
        }

        if(App.getInstance().getSettings().getBoolean(Settings.ALL_CERTS, false)) {
            try {
                final TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                                    throws CertificateException {}

                            @Override
                            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                                    throws CertificateException {}

                            @Override
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }
                        }
                };

                // Install the all-trusting trust manager
                final SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                // Create an ssl socket factory with our all-trusting manager
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                client.setSslSocketFactory(sslSocketFactory);
                client.setHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
            } catch (Exception ignored) {}
        }

        if (BuildConfig.DEBUG) {
            client.interceptors().add(new LoggingInterceptor());
            client.networkInterceptors().add(new StethoInterceptor());
        }

        return client;
    }

    private static class Holder {
        private static OkHttpClient client = getClient();
    }

    /**
     * OkHttp Logging interceptor
     * http://stackoverflow.com/a/30625572/1592572
     */
    static class LoggingInterceptor implements Interceptor {
        @Override public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            long t1 = System.nanoTime();
            Log.d("OkHttp", String.format("Sending request %s on %s%n%s",
                    request.url(), chain.connection(), request.headers()));

            Response response = chain.proceed(request);

            long t2 = System.nanoTime();
            Log.d("OkHttp", String.format("Received response for %s in %.1fms, status %d%n%s",
                    response.request().url(), (t2 - t1) / 1e6d, response.code(), response.headers()));

            return response;
        }
    }
}
