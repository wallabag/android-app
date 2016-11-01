package fr.gaulupeau.apps.Poche.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.stetho.okhttp3.StethoInterceptor;
import com.franmontiel.persistentcookiejar.ClearableCookieJar;
import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;

public class WallabagConnection {

    private static final String TAG = WallabagConnection.class.getSimpleName();

    public static HttpUrl getHttpURL(String url) throws IncorrectConfigurationException {
        HttpUrl httpUrl = HttpUrl.parse(url);

        if(httpUrl == null) throw new IncorrectConfigurationException("Incorrect URL");

        return httpUrl;
    }

    public static boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) App.getInstance()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    public static void clearCookies(Context context) {
        new SharedPrefsCookiePersistor(context).clear();
        replaceClient();
    }

    public static OkHttpClient replaceClient() {
        return Holder.client = createClient();
    }

    public static OkHttpClient getClient() {
        if (Holder.client != null)
            return Holder.client;

        OkHttpClient client = createClient();

        Holder.client = client;
        return client;
    }

    public static OkHttpClient createClient() {
        Settings settings = App.getInstance().getSettings();

        return createClient(true, settings.isCustomSSLSettings(), settings.isAcceptAllCertificates());
    }

    public static OkHttpClient createClient(
            boolean persistentCookies, boolean customSSLSettings, boolean acceptAllCertificates) {
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .readTimeout(45, TimeUnit.SECONDS);

        ClearableCookieJar cookieJar = new PersistentCookieJar(
                new SetCookieCache(),
                persistentCookies
                        ? new SharedPrefsCookiePersistor(App.getInstance())
                        : new FakeCookiePersistor());
        b.cookieJar(cookieJar);

        if(customSSLSettings) {
            try {
                b.sslSocketFactory(new CustomSSLSocketFactory());
            } catch(Exception e) {
                Log.w(TAG, "Couldn't init custom socket library", e);
            }
        } else if(acceptAllCertificates) {
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
                                return new java.security.cert.X509Certificate[0];
                            }
                        }
                };

                // Install the all-trusting trust manager
                final SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, null);
                // Create an ssl socket factory with our all-trusting manager
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                b.sslSocketFactory(sslSocketFactory).hostnameVerifier(
                        new HostnameVerifier() {
                            @Override
                            public boolean verify(String hostname, SSLSession session) {
                                return true;
                            }
                        }
                );
            } catch(Exception e) {
                Log.w(TAG, "Couldn't init all-trusting client", e);
            }
        }

        if(BuildConfig.DEBUG) {
            b.addInterceptor(new LoggingInterceptor());
            b.addNetworkInterceptor(new StethoInterceptor());
        }

        return b.build();
    }

    private static class Holder {
        private static OkHttpClient client = getClient();
    }

    static class FakeCookiePersistor implements CookiePersistor {

        @Override
        public List<Cookie> loadAll() {
            return new ArrayList<>(0);
        }

        @Override
        public void saveAll(Collection<Cookie> cookies) {}

        @Override
        public void removeAll(Collection<Cookie> cookies) {}

        @Override
        public void clear() {}

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

    /**
     * Custom socket factory to enable TLSv1.1 and TLSv1.2.
     * Based on:
     * https://gitlab.com/bitfireAT/davdroid/blob/master/app/src/main/java/at/bitfire/davdroid/SSLSocketFactoryCompat.java
     * http://blog.dev-area.net/2015/08/13/android-4-1-enable-tls-1-1-and-tls-1-2/
     */
    static class CustomSSLSocketFactory extends SSLSocketFactory {

        private static final String TAG = CustomSSLSocketFactory.class.getSimpleName();

        // do not rename; https://github.com/square/okhttp/issues/2327
        private SSLSocketFactory delegate;

        static String protocols[], cipherSuites[];

        static {
            initParameters();
        }

        private static void initParameters() {
            SSLSocket socket = null;
            try {
                socket = (SSLSocket)SSLSocketFactory.getDefault().createSocket();
                if(socket == null) return;

                /* set reasonable protocol versions */
                // - enable all supported protocols (enables TLSv1.1 and TLSv1.2 on Android <5.0)
                // - remove all SSL versions (especially SSLv3) because they're insecure now
                List<String> protocols = new LinkedList<>();
                for(String protocol: socket.getSupportedProtocols())
                    if(!protocol.toUpperCase(Locale.US).contains("SSL"))
                        protocols.add(protocol);

                Log.d(TAG, "Allowed protocols: " + TextUtils.join(", ", protocols));
                CustomSSLSocketFactory.protocols = protocols.toArray(new String[protocols.size()]);

                /* set up reasonable cipher suites */
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    // choose known secure cipher suites
                    List<String> allowedCiphers = Arrays.asList(
                            // TLS 1.2
                            "TLS_RSA_WITH_AES_256_GCM_SHA384",
                            "TLS_RSA_WITH_AES_128_GCM_SHA256",
                            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                            // maximum interoperability
                            "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
                            "TLS_RSA_WITH_AES_128_CBC_SHA",
                            // additionally
                            "TLS_RSA_WITH_AES_256_CBC_SHA",
                            "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
                            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                            "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
                            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"
                    );

                    List<String> availableCiphers = Arrays.asList(socket.getSupportedCipherSuites());

                    Log.d(TAG, "Available cipher suites: " + TextUtils.join(", ", availableCiphers));
                    Log.d(TAG, "Cipher suites enabled by default: " + TextUtils.join(", ", socket.getEnabledCipherSuites()));

                    // take all allowed ciphers that are available and put them into ciphers
                    HashSet<String> ciphers = new HashSet<>(allowedCiphers);
                    ciphers.retainAll(availableCiphers);

                    // add enabled by default (for compatibility)
                    ciphers.addAll(Arrays.asList(socket.getEnabledCipherSuites()));

                    Log.d(TAG, "Enabling (only) those ciphers: " + TextUtils.join(", ", ciphers));
                    CustomSSLSocketFactory.cipherSuites = ciphers.toArray(new String[ciphers.size()]);
                }
            } catch(IOException e) {
                Log.w(TAG, "Couldn't select protocols and ciphers", e);
            } finally {
                if(socket != null) {
                    try {
                        socket.close();
                    } catch(IOException ignored) {}
                }
            }
        }

        public CustomSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            delegate = sslContext.getSocketFactory();
        }

        private Socket setParameters(Socket socket) {
            if(socket != null && (socket instanceof SSLSocket)) {
                SSLSocket s = (SSLSocket)socket;
                if(protocols != null) s.setEnabledProtocols(protocols);
                if(cipherSuites != null) s.setEnabledCipherSuites(cipherSuites);
            }
            return socket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return cipherSuites;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return cipherSuites;
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return setParameters(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return setParameters(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return setParameters(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return setParameters(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return setParameters(delegate.createSocket(address, port, localAddress, localPort));
        }

    }

}
