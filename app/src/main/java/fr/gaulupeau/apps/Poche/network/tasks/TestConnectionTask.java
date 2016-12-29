package fr.gaulupeau.apps.Poche.network.tasks;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import fr.gaulupeau.apps.Poche.network.RequestCreator;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static fr.gaulupeau.apps.Poche.network.WallabagWebService.WALLABAG_LOGIN_FORM_V2;

public class TestConnectionTask extends AsyncTask<Void, Void, List<TestConnectionTask.TestResult>> {

    public static class TestResult {
        public String url;
        public WallabagWebService.ConnectionTestResult result;
        public String errorMessage;
    }

    private static final String TAG = TestConnectionTask.class.getSimpleName();

    private static final String PROTO_HTTP = "http://";
    private static final String PROTO_HTTPS = "https://";

    private final String endpointUrl;
    private final String username;
    private final String password;
    private String httpAuthUsername;
    private String httpAuthPassword;
    private boolean customSSLSettings;
    private boolean tryPossibleURLs;

    private ResultHandler resultHandler;

    public TestConnectionTask(String endpointUrl, String username, String password,
                              String httpAuthUsername, String httpAuthPassword,
                              boolean customSSLSettings, boolean tryPossibleURLs,
                              ResultHandler resultHandler) {
        this.endpointUrl = endpointUrl;
        this.username = username;
        this.password = password;
        this.httpAuthUsername = httpAuthUsername;
        this.httpAuthPassword = httpAuthPassword;
        this.customSSLSettings = customSSLSettings;
        this.tryPossibleURLs = tryPossibleURLs;
        this.resultHandler = resultHandler;
    }

    @Override
    protected List<TestResult> doInBackground(Void... params) {
        String srcUrl = endpointUrl;

        srcUrl = removeTrailingSlashes(srcUrl);

        List<String> urls = new ArrayList<>();
        if(tryPossibleURLs) {
            boolean httpsOnly = srcUrl.toLowerCase(Locale.US).startsWith(PROTO_HTTPS);

            String urlWithoutProto = removeProto(srcUrl);
            if(urlWithoutProto == null) urlWithoutProto = srcUrl;

            if(!httpsOnly) {
                urls.add(PROTO_HTTP + urlWithoutProto);
            }
            urls.add(PROTO_HTTPS + urlWithoutProto);

            List<String> redirects = new ArrayList<>();
            for(String url: urls) {
                try {
                    Log.d(TAG, "Detecting redirection for: " + url);
                    String redirection = detectRedirection(url);
                    if(redirection != null && !urls.contains(redirection)
                            && !redirects.contains(redirection)) {
                        Log.d(TAG, "Found redirection: " + redirection);
                        redirects.add(0, redirection);
                    }
                } catch(IOException e) {
                    Log.i(TAG, "IOException while trying to detect redirection; url: " + url, e);
                }
            }
            urls.addAll(redirects);
        } else {
            urls.add(srcUrl);
        }

        Log.d(TAG, "URLs: ");
        for(String url1: urls) {
            Log.d(TAG, url1);
        }

        List<TestResult> results = new ArrayList<>(urls.size());
        for(String url: urls) {
            Log.i(TAG, "Testing " + url);

            TestResult testResult = new TestResult();
            testResult.url = url;

            WallabagWebService service = new WallabagWebService(url, username, password,
                    httpAuthUsername, httpAuthPassword,
                    WallabagConnection.createClient(true, customSSLSettings));

            try {
                testResult.result = service.testConnection();

                Log.d(TAG, "Connection test result: " + testResult.result);
            } catch(IncorrectConfigurationException e) {
                Log.d(TAG, "Connection test: Exception", e);
                testResult.result = WallabagWebService.ConnectionTestResult.INCORRECT_URL;
            } catch(IOException e) {
                Log.d(TAG, "Connection test: Exception", e);
                testResult.errorMessage = e.getLocalizedMessage();
            }

            results.add(testResult);
        }

        return results;
    }

    @Override
    protected void onPostExecute(List<TestResult> results) {
        if(resultHandler != null) resultHandler.onTestConnectionResult(results);
    }

    // well, it's a mess
    private String detectRedirection(String url) throws IOException {
        OkHttpClient client = WallabagConnection.createClient(true, customSSLSettings);

        HttpUrl httpUrl = HttpUrl.parse(url + "/");
        if(httpUrl == null) {
            return null;
        }

        Request request = new RequestCreator(httpAuthUsername, httpAuthPassword).getRequest(httpUrl);
        Response response = client.newCall(request).execute();

        if(!response.isSuccessful()) {
            Log.d(TAG, "detectRedirection(): response in unsuccessful: status code: "
                    + response.code());
            return null;
        }

        if(response.priorResponse() == null) {
            return url; // no redirection
        }

        String newUrl = response.request().url().toString();
        newUrl = removeTrailingSlashes(newUrl);
        if(url.equals(newUrl)) {
            return url; // no redirection
        }

        String srcUrlWithoutProto = removeProto(url);
        if(srcUrlWithoutProto == null) return null;

        String newUrlWithoutProto = removeProto(newUrl);
        if(newUrlWithoutProto == null) return null;

        if(srcUrlWithoutProto.equals(newUrlWithoutProto)) {
            return url; // we don't care about protocol redirection here; report no redirection
        }

        String loginPath = "/login";
        if(newUrlWithoutProto.endsWith(loginPath)
                && response.body().string().contains(WALLABAG_LOGIN_FORM_V2)) {
            // presumably Wallabag v2 login page

            newUrlWithoutProto = newUrlWithoutProto.substring(
                    0, newUrlWithoutProto.length() - loginPath.length());
            newUrl = newUrl.substring(0, newUrl.length() - loginPath.length());
        }

        if(srcUrlWithoutProto.equals(newUrlWithoutProto)) {
            return url; // no redirection
        }

        return newUrl; // redirected
    }

    private static String removeTrailingSlashes(String s) {
        while(s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }

        return s;
    }

    private static String removeProto(String s) {
        if(s.toLowerCase(Locale.US).startsWith(PROTO_HTTP)) {
            return s.substring(PROTO_HTTP.length());
        } else if(s.toLowerCase(Locale.US).startsWith(PROTO_HTTPS)) {
            return s.substring(PROTO_HTTPS.length());
        } else {
            return null;
        }
    }

    public static boolean areUrlsEqual(String url1, String url2) {
        HttpUrl httpUrl1 = HttpUrl.parse(url1);
        HttpUrl httpUrl2 = HttpUrl.parse(url2);

        if(httpUrl1 == null && httpUrl2 == null) return false; // we're doomed anyway

        return httpUrl1 != null && httpUrl1.equals(httpUrl2);
    }

    public interface ResultHandler {
        void onTestConnectionResult(List<TestResult> results);
    }

}
