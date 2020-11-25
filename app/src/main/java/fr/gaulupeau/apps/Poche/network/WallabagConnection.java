package fr.gaulupeau.apps.Poche.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.stetho.okhttp3.StethoInterceptor;

import org.conscrypt.Conscrypt;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.security.Security;
import java.util.concurrent.TimeUnit;

import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import wallabag.apiwrapper.ParameterHandler;
import wallabag.apiwrapper.WallabagService;
import wallabag.apiwrapper.models.TokenResponse;

public class WallabagConnection {

    private static final String TAG = WallabagConnection.class.getSimpleName();

    private static boolean conscryptInitialized;

    private static WallabagService wallabagService;
    private static final Object wallabagServiceLock = new Object();

    public static void initConscrypt() {
        if (conscryptInitialized) return;

        synchronized (WallabagConnection.class) {
            if (conscryptInitialized) return;

            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1);
            } catch (Throwable t) {
                Log.w(TAG, "initConscrypt() error", t);
            }

            conscryptInitialized = true;
        }
    }

    public static WallabagService getWallabagService() throws IncorrectConfigurationException {
        synchronized(wallabagServiceLock) {
            if(wallabagService == null) {
                wallabagService = createWallabagService();
            }
            return wallabagService;
        }
    }

    public static void resetWallabagService() {
        synchronized(wallabagServiceLock) {
            wallabagService = null;
        }
    }

    private static WallabagService createWallabagService() throws IncorrectConfigurationException {
        final Settings settings = App.getSettings();

        String url = settings.getUrl();
        if(TextUtils.isEmpty(url)) throw new IncorrectConfigurationException("URL is empty");

        return WallabagService.instance(url, new ParameterHandler() {
            @Override
            public String getUsername() {
                return settings.getUsername();
            }

            @Override
            public String getPassword() {
                return settings.getPassword();
            }

            @Override
            public String getClientID() {
                return settings.getApiClientID();
            }

            @Override
            public String getClientSecret() {
                return settings.getApiClientSecret();
            }

            @Override
            public String getRefreshToken() {
                return settings.getApiRefreshToken();
            }

            @Override
            public String getAccessToken() {
                Long expirationDate = settings.getApiAccessTokenExpirationDate();
                if (expirationDate != null && System.currentTimeMillis() > expirationDate) {
                    return null;
                }

                return settings.getApiAccessToken();
            }

            @Override
            public boolean tokensUpdated(TokenResponse token) {
                if(token.refreshToken != null) settings.setApiRefreshToken(token.refreshToken);
                settings.setApiAccessToken(token.accessToken);

                if (token.expiresIn > TimeUnit.MINUTES.toSeconds(1)) {
                    settings.setApiAccessTokenExpirationDate(System.currentTimeMillis()
                            + TimeUnit.SECONDS.toMillis(token.expiresIn)
                            - TimeUnit.SECONDS.toMillis(30)); // subtract 30 seconds to be on the safe side
                } else {
                    settings.setApiAccessTokenExpirationDate(null);
                }

                return !TextUtils.isEmpty(token.accessToken);
            }
        }, createClient(false), null);
    }

    public static boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) App.getInstance()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    public static OkHttpClient createClient() {
        return createClient(true);
    }

    public static OkHttpClient createClient(boolean addCookieManager) {
        return getClientBuilder(addCookieManager).build();
    }

    private static OkHttpClient.Builder getClientBuilder(boolean addCookieManager) {
        initConscrypt();

        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .readTimeout(45, TimeUnit.SECONDS);

        if(addCookieManager) {
            b.cookieJar(new JavaNetCookieJar(new CookieManager(null, CookiePolicy.ACCEPT_ALL)));
        }

        if(BuildConfig.DEBUG) {
            b.addInterceptor(new LoggingInterceptor());
            b.addNetworkInterceptor(new StethoInterceptor());
        }

        return b;
    }

    /**
     * OkHttp Logging interceptor
     * http://stackoverflow.com/a/30625572/1592572
     */
    private static class LoggingInterceptor implements Interceptor {
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
