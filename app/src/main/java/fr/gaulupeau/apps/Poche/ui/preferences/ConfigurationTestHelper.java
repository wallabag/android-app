package fr.gaulupeau.apps.Poche.ui.preferences;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.FeedsCredentials;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagServiceEndpoint;
import fr.gaulupeau.apps.Poche.network.tasks.GetCredentialsTask;
import fr.gaulupeau.apps.Poche.network.tasks.TestConnectionTask;
import fr.gaulupeau.apps.Poche.network.tasks.TestFeedsTask;

public class ConfigurationTestHelper
        implements GetCredentialsTask.ResultHandler,
        TestConnectionTask.ResultHandler,
        TestFeedsTask.ResultHandler {

    private static final String TAG = ConfigurationTestHelper.class.getSimpleName();

    public interface ResultHandler {
        void onConfigurationTestSuccess(String url, Integer serverVersion);
        void onConnectionTestFail(WallabagServiceEndpoint.ConnectionTestResult result, String details);
        void onFeedsTestFail(TestFeedsTask.Result result, String details);
    }

    public interface GetCredentialsHandler {
        void onGetCredentialsResult(String feedsUserID, String feedsToken);
        void onGetCredentialsFail();
    }

    protected Context context;
    protected String url;
    protected String username, password;
    protected String feedsUserID, feedsToken;
    protected String httpAuthUsername, httpAuthPassword;
    protected boolean customSSLSettings = Settings.getDefaultCustomSSLSettingsValue();
    protected boolean acceptAllCertificates;
    protected int wallabagServerVersion = -1;

    protected boolean tryPossibleURLs;

    private ResultHandler handler;
    private GetCredentialsHandler credentialsHandler;

    private ProgressDialog progressDialog;

    private TestConnectionTask testConnectionTask;
    private GetCredentialsTask getCredentialsTask;
    private TestFeedsTask testFeedsTask;

    private boolean canceled;

    private String newUrl;
    private Integer newWallabagServerVersion;

    public ConfigurationTestHelper(Context context, ResultHandler handler,
                                   GetCredentialsHandler credentialsHandler,
                                   Settings settings, boolean detectVersion) {
        this(context, handler, credentialsHandler, settings.getUrl(),
                settings.getUsername(), settings.getPassword(),
                settings.getFeedsUserID(), settings.getFeedsToken(),
                settings.getHttpAuthUsername(), settings.getHttpAuthPassword(),
                settings.isCustomSSLSettings(), settings.isAcceptAllCertificates(),
                detectVersion ? -1 : settings.getWallabagServerVersion(), false);
    }

    public ConfigurationTestHelper(Context context, ResultHandler handler,
                                   GetCredentialsHandler credentialsHandler, String url,
                                   String username, String password,
                                   String feedsUserID, String feedsToken,
                                   String httpAuthUsername, String httpAuthPassword,
                                   boolean customSSLSettings, boolean acceptAllCertificates,
                                   int wallabagServerVersion, boolean tryPossibleURLs) {
        this.context = context;
        this.handler = handler;
        this.credentialsHandler = credentialsHandler;
        this.url = url;
        this.username = username;
        this.password = password;
        this.feedsUserID = feedsUserID;
        this.feedsToken = feedsToken;
        this.httpAuthUsername = httpAuthUsername;
        this.httpAuthPassword = httpAuthPassword;
        this.customSSLSettings = customSSLSettings;
        this.acceptAllCertificates = acceptAllCertificates;
        this.wallabagServerVersion = wallabagServerVersion;

        this.tryPossibleURLs = tryPossibleURLs;

        progressDialog = new ProgressDialog(context);
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                cancel();
            }
        });
    }

    public void test() {
        canceled = false;

        // TODO: string constants
        progressDialog.setMessage("Testing connection...");
        progressDialog.show();

        testConnectionTask = new TestConnectionTask(url, username, password,
                httpAuthUsername, httpAuthPassword, customSSLSettings, acceptAllCertificates,
                wallabagServerVersion, true, this);
        testConnectionTask.execute();
    }

    public void cancel() {
        canceled = true;

        if(progressDialog != null) progressDialog.dismiss();

        cancelTasks();
    }

    protected String getUrl() {
        return newUrl != null ? newUrl : url;
    }

    protected int getWallabagServerVersion() {
        return newWallabagServerVersion != null ? newWallabagServerVersion : wallabagServerVersion;
    }

    @Override
    public void onTestConnectionResult(List<TestConnectionTask.TestResult> results) {
        if(progressDialog != null) progressDialog.dismiss();

        if(canceled) return;

        if(results != null) {
            if(results.isEmpty()) { // should not happen?
                Log.w(TAG, "onTestConnectionResult(): results are empty");
            } else {
                String bestUrl = null;
                int bestUrlServerVersion = -1;
                int originalUrlServerVersion = -1;
                boolean isOriginalBest = false;
                boolean isOriginalOk = false;
                WallabagServiceEndpoint.ConnectionTestResult error = null;
                String errorMessage = null;

                for(int i = results.size() - 1; i >= 0; i--) {
                    TestConnectionTask.TestResult result = results.get(i);
                    String url = result.url;
                    if(TestConnectionTask.areUrlsEqual(url, this.url)) {
                        if(result.result == WallabagServiceEndpoint.ConnectionTestResult.OK) {
                            isOriginalOk = true;
                            originalUrlServerVersion = result.wallabagServerVersion;

                            if(bestUrl == null) {
                                bestUrl = result.url;
                                bestUrlServerVersion = result.wallabagServerVersion;
                                isOriginalBest = true;
                                break;
                            }
                        } else {
                            error = result.result;
                            errorMessage = result.errorMessage;
                        }
                    } else if(result.result == WallabagServiceEndpoint.ConnectionTestResult.OK) {
                        if(bestUrl == null) {
                            bestUrl = result.url;
                            bestUrlServerVersion = result.wallabagServerVersion;
                        }
                    }
                }

                if(isOriginalBest) { // the original URL is just fine
                    connectionTestOnSuccess(bestUrl, bestUrlServerVersion);
                } else if(bestUrl != null) { // there is a better option
                    showSuggestionDialog(bestUrl, bestUrlServerVersion,
                            originalUrlServerVersion, isOriginalOk);
                } else { // all the options have failed
                    if(handler != null) handler.onConnectionTestFail(error, errorMessage);
                    showErrorDialog(error, errorMessage);
                }
            }
        } else { // should not happen
            Log.w(TAG, "onTestConnectionResult(): results are null");
        }
    }

    protected void showSuggestionDialog(final String suggestedUrl,
                                        final int suggestedUrlServerVersion,
                                        final int originalUrlServerVersion,
                                        boolean allowToDecline) {
        // TODO: string constants
        AlertDialog.Builder b = new AlertDialog.Builder(context)
                .setTitle("Consider changing URL")
                .setPositiveButton("Use suggested", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        connectionTestOnSuccess(suggestedUrl, suggestedUrlServerVersion);
                    }
                });

        if(allowToDecline) {
            b.setMessage(String.format("It is recommended to change URL to: \"%s\". "
                    + "But you may use the entered one too.", suggestedUrl))
                    .setNegativeButton("Use mine", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            connectionTestOnSuccess(null, originalUrlServerVersion);
                        }
                    });
        } else {
            b.setMessage(String.format("It is suggested to change URL to: \"%s\"", suggestedUrl))
                    .setNegativeButton("Cancel", null);
        }
        b.show();
    }

    protected void showErrorDialog(WallabagServiceEndpoint.ConnectionTestResult error,
                                   String errorMessage) {
        // TODO: string constants
        // TODO: human-readable error message
        new AlertDialog.Builder(context)
                .setTitle("Connection test failed")
                .setMessage("Error: " + ((error != null) ? error : errorMessage))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    protected void connectionTestOnSuccess(String url, Integer serverVersion) {
        Log.d(TAG, "connectionTestOnSuccess() new url: " + url);
        newUrl = url;
        newWallabagServerVersion = serverVersion;

        if(canceled) return;

        if(credentialsHandler != null) {
            getFeedsCredentials();
        } else {
            testFeedsCredentials();
        }
    }

    protected void getFeedsCredentials() {
        progressDialog.setMessage("Setting up feeds...");
        progressDialog.show();

        getCredentialsTask = new GetCredentialsTask(this, getUrl(),
                username, password,
                httpAuthUsername, httpAuthPassword, getWallabagServerVersion());
        getCredentialsTask.execute();
    }

    @Override
    public void handleGetCredentialsResult(boolean success,
                                           FeedsCredentials credentials,
                                           int wallabagVersion) {
        progressDialog.dismiss();

        if(canceled) return;

        if(success) {
            feedsUserID = credentials.userID;
            feedsToken = credentials.token;

            if(credentialsHandler != null) {
                credentialsHandler.onGetCredentialsResult(credentials.userID, credentials.token);
            }

            testFeedsCredentials();
        } else {
            if(credentialsHandler != null) {
                credentialsHandler.onGetCredentialsFail();
            }

            // TODO: string constants
            new AlertDialog.Builder(context)
                    .setTitle("Couldn't set up feeds")
                    .setMessage("Something went wrong. Check your internet connection and try again.")
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    protected void testFeedsCredentials() {
        // TODO: string constants
        progressDialog.setMessage("Checking feeds...");
        progressDialog.show();

        testFeedsTask = new TestFeedsTask(getUrl(), feedsUserID, feedsToken,
                httpAuthUsername, httpAuthPassword,
                customSSLSettings, acceptAllCertificates,
                getWallabagServerVersion(), this);
        testFeedsTask.execute();
    }

    @Override
    public void testFeedsTaskOnResult(TestFeedsTask.Result result, String details) {
        if(progressDialog != null) progressDialog.dismiss();

        if(canceled) return;

        if(result == TestFeedsTask.Result.OK) {
            if(handler != null) {
                handler.onConfigurationTestSuccess(newUrl, newWallabagServerVersion);
            } else {
                Settings settings = App.getInstance().getSettings();

                if(newUrl != null) {
                    settings.setUrl(newUrl);
                }

                settings.setConfigurationOk(true);
                settings.setConfigurationErrorShown(false);

                Toast.makeText(context, R.string.settings_parametersAreOk, Toast.LENGTH_SHORT).show();
            }
        } else {
            if(handler != null) {
                handler.onFeedsTestFail(result, details);
            } else {
                // TODO: detailed message
                // TODO: string constants
                new AlertDialog.Builder(context)
                        .setTitle("Feeds test failed")
                        .setMessage("Something went wrong.")
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        }
    }

    private void cancelTasks() {
        cancelTask(testConnectionTask);
        cancelTask(getCredentialsTask);
        cancelTask(testFeedsTask);
    }

    private void cancelTask(AsyncTask task) {
        if(task != null) task.cancel(true);
    }

}
