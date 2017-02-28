package fr.gaulupeau.apps.Poche.ui.preferences;

import android.app.Activity;
import android.app.Dialog;
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
import fr.gaulupeau.apps.Poche.network.ClientCredentials;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.WallabagWebService;
import fr.gaulupeau.apps.Poche.network.tasks.GetCredentialsTask;
import fr.gaulupeau.apps.Poche.network.tasks.TestConnectionTask;
import fr.gaulupeau.apps.Poche.network.tasks.TestApiAccessTask;

public class ConfigurationTestHelper
        implements GetCredentialsTask.ResultHandler,
        TestConnectionTask.ResultHandler,
        TestApiAccessTask.ResultHandler {

    private static final String TAG = ConfigurationTestHelper.class.getSimpleName();

    public interface ResultHandler {
        void onConfigurationTestSuccess(String url);
        void onConnectionTestFail(WallabagWebService.ConnectionTestResult result, String details);
        void onApiAccessTestFail(TestApiAccessTask.Result result, String details);
    }

    public interface GetCredentialsHandler {
        void onGetCredentialsResult(ClientCredentials clientCredentials);
        void onGetCredentialsFail();
    }

    private Context context;
    private String url;
    private String httpAuthUsername, httpAuthPassword;
    private String username, password;
    private String clientID, clientSecret;
    private boolean customSSLSettings = Settings.getDefaultCustomSSLSettingsValue();

    private boolean tryPossibleURLs;
    private boolean handleResult;

    private ResultHandler handler;
    private GetCredentialsHandler credentialsHandler;

    private ProgressDialog progressDialog;

    private TestConnectionTask testConnectionTask;
    private GetCredentialsTask getCredentialsTask;
    private TestApiAccessTask testApiAccessTask;

    private boolean canceled;

    private String newUrl;

    public ConfigurationTestHelper(Context context, ResultHandler handler,
                                   GetCredentialsHandler credentialsHandler,
                                   Settings settings, boolean handleResult) {
        this(context, handler, credentialsHandler, settings.getUrl(),
                settings.getHttpAuthUsername(), settings.getHttpAuthPassword(),
                settings.getUsername(), settings.getPassword(),
                settings.getApiClientID(), settings.getApiClientSecret(),
                settings.isCustomSSLSettings(), false, handleResult);
    }

    public ConfigurationTestHelper(Context context, ResultHandler handler,
                                   GetCredentialsHandler credentialsHandler, String url,
                                   String httpAuthUsername, String httpAuthPassword,
                                   String username, String password,
                                   String clientID, String clientSecret,
                                   boolean customSSLSettings, boolean tryPossibleURLs,
                                   boolean handleResult) {
        this.context = context;
        this.handler = handler;
        this.credentialsHandler = credentialsHandler;
        this.url = url;
        this.httpAuthUsername = httpAuthUsername;
        this.httpAuthPassword = httpAuthPassword;
        this.username = username;
        this.password = password;
        this.clientID = clientID;
        this.clientSecret = clientSecret;
        this.customSSLSettings = customSSLSettings;

        this.tryPossibleURLs = tryPossibleURLs;
        this.handleResult = handleResult;

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

        progressDialog.setMessage(context.getString(R.string.testConnection_progressDialogMessage));
        progressDialog.show();

        testConnectionTask = new TestConnectionTask(url, username, password,
                httpAuthUsername, httpAuthPassword,
                customSSLSettings, tryPossibleURLs, this);
        testConnectionTask.execute();
    }

    public void cancel() {
        canceled = true;

        dismissDialog(progressDialog);

        cancelTasks();
    }

    protected String getUrl() {
        return newUrl != null ? newUrl : url;
    }

    @Override
    public void onTestConnectionResult(List<TestConnectionTask.TestResult> results) {
        dismissDialog(progressDialog);

        if(canceled) return;

        if(results != null) {
            if(results.isEmpty()) { // should not happen?
                Log.w(TAG, "onTestConnectionResult(): results are empty");
            } else {
                String bestUrl = null;
                boolean isOriginalBest = false;
                boolean isOriginalOk = false;
                WallabagWebService.ConnectionTestResult error = null;
                String errorMessage = null;

                for(int i = results.size() - 1; i >= 0; i--) {
                    TestConnectionTask.TestResult result = results.get(i);
                    String url = result.url;
                    if(TestConnectionTask.areUrlsEqual(url, this.url)) {
                        if(result.result == WallabagWebService.ConnectionTestResult.OK) {
                            isOriginalOk = true;

                            if(bestUrl == null) {
                                bestUrl = result.url;
                                isOriginalBest = true;
                                break;
                            }
                        } else {
                            error = result.result;
                            errorMessage = result.errorMessage;
                        }
                    } else if(result.result == WallabagWebService.ConnectionTestResult.OK) {
                        if(bestUrl == null) {
                            bestUrl = result.url;
                        }
                    }
                }

                if(isOriginalBest) { // the original URL is just fine
                    connectionTestOnSuccess(bestUrl);
                } else if(bestUrl != null) { // there is a better option
                    showSuggestionDialog(bestUrl, isOriginalOk);
                } else { // all the options have failed
                    if(handler != null) handler.onConnectionTestFail(error, errorMessage);
                    showErrorDialog(error, errorMessage);
                }
            }
        } else { // should not happen
            Log.w(TAG, "onTestConnectionResult(): results are null");
        }
    }

    protected void showSuggestionDialog(final String suggestedUrl, boolean allowToDecline) {
        AlertDialog.Builder b = new AlertDialog.Builder(context)
                .setTitle(R.string.d_testConnection_urlSuggestion_title)
                .setPositiveButton(R.string.d_testConnection_urlSuggestion_acceptButton,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        connectionTestOnSuccess(suggestedUrl);
                    }
                });

        if(allowToDecline) {
            b.setMessage(context.getString(R.string.d_testConnection_urlSuggestion_message, suggestedUrl))
                    .setNegativeButton(R.string.d_testConnection_urlSuggestion_declineButton,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            connectionTestOnSuccess(null);
                        }
                    });
        } else {
            b.setMessage(context.getString(R.string.d_testConnection_urlSuggestion_message_mandatory, suggestedUrl))
                    .setNegativeButton(R.string.d_testConnection_urlSuggestion_cancelButton, null);
        }
        b.show();
    }

    protected void showErrorDialog(WallabagWebService.ConnectionTestResult error,
                                   String errorMessage) {
        String errorStr;
        if(error != null) {
            int stringID;
            switch(error) {
                case INCORRECT_URL:
                    stringID = R.string.testConnection_errorMessage_incorrectUrl;
                    break;
                case UNSUPPORTED_SERVER_VERSION:
                    stringID = R.string.testConnection_errorMessage_unsupportedServerVersion;
                    break;
                case WALLABAG_NOT_FOUND:
                    stringID = R.string.testConnection_errorMessage_wallabagNotFound;
                    break;
                case HTTP_AUTH:
                    stringID = R.string.testConnection_errorMessage_httpAuth;
                    break;
                case NO_CSRF:
                    stringID = R.string.testConnection_errorMessage_noCSRF;
                    break;
                case INCORRECT_CREDENTIALS:
                    stringID = R.string.testConnection_errorMessage_incorrectCredentials;
                    break;
                case AUTH_PROBLEM:
                    stringID = R.string.testConnection_errorMessage_authProblems;
                    break;
                case UNKNOWN_PAGE_AFTER_LOGIN:
                    stringID = R.string.testConnection_errorMessage_unknownPageAfterLogin;
                    break;
                default:
                    stringID = R.string.testConnection_errorMessage_unknown;
                    break;
            }
            errorStr = context.getString(stringID);
        } else {
            if(errorMessage != null && !errorMessage.isEmpty()) {
                errorStr = context.getString(R.string.testConnection_errorMessage_unknownWithMessage, errorMessage);
            } else {
                errorStr = context.getString(R.string.testConnection_errorMessage_unknown);
            }
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.d_testConnection_fail_title)
                .setMessage(errorStr)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    protected void connectionTestOnSuccess(String url) {
        Log.d(TAG, "connectionTestOnSuccess() new url: " + url);
        newUrl = url;

        if(canceled) return;

        if(credentialsHandler != null) {
            getApiCredentials();
        } else {
            testApiCredentials();
        }
    }

    protected void getApiCredentials() {
        progressDialog.setMessage(context.getString(R.string.getCredentials_progressDialogMessage));
        progressDialog.show();

        getCredentialsTask = new GetCredentialsTask(this, getUrl(),
                username, password,
                httpAuthUsername, httpAuthPassword,
                customSSLSettings);
        getCredentialsTask.execute();
    }

    @Override
    public void handleGetCredentialsResult(boolean success, ClientCredentials credentials) {
        dismissDialog(progressDialog);

        if(canceled) return;

        if(success) {
            clientID = credentials.clientID;
            clientSecret = credentials.clientSecret;

            if(credentialsHandler != null) {
                credentialsHandler.onGetCredentialsResult(credentials);
            }

            testApiCredentials();
        } else {
            if(credentialsHandler != null) {
                credentialsHandler.onGetCredentialsFail();
            }

            new AlertDialog.Builder(context)
                    .setTitle(R.string.d_getCredentials_title_fail)
                    .setMessage(R.string.d_getCredentials_title_message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    protected void testApiCredentials() {
        progressDialog.setMessage(context.getString(R.string.checkCredentials_progressDialogMessage));
        progressDialog.show();

        testApiAccessTask = new TestApiAccessTask(getUrl(),
                username, password,
                clientID, clientSecret, null, null,
                customSSLSettings, this);
        testApiAccessTask.execute();
    }

    @Override
    public void onTestApiAccessTaskResult(TestApiAccessTask.Result result, String details) {
        dismissDialog(progressDialog);

        if(canceled) return;

        if(result == TestApiAccessTask.Result.OK) {
            if(handleResult) {
                Settings settings = App.getInstance().getSettings();

                if(newUrl != null) {
                    settings.setUrl(newUrl);
                }

                settings.setConfigurationOk(true);
                settings.setConfigurationErrorShown(false);

                Toast.makeText(context, R.string.settings_parametersAreOk, Toast.LENGTH_SHORT).show();
            }

            handler.onConfigurationTestSuccess(newUrl);
        } else {
            if(handleResult) {
                int basicErrorResId;
                int messageErrorResId;

                if(result == null) result = TestApiAccessTask.Result.UNKNOWN_ERROR;
                switch(result) {
                    case NOT_FOUND:
                        basicErrorResId = R.string.d_checkCredentials_errorMessage_notFound;
                        messageErrorResId = R.string.d_checkCredentials_errorMessage_notFoundWithMessage;
                        break;

                    case NO_ACCESS:
                        basicErrorResId = R.string.d_checkCredentials_errorMessage_noAccess;
                        messageErrorResId = R.string.d_checkCredentials_errorMessage_noAccessWithMessage;
                        break;

                    default:
                        basicErrorResId = R.string.d_checkCredentials_errorMessage_unknown;
                        messageErrorResId = R.string.d_checkCredentials_errorMessage_unknownWithMessage;
                        break;
                }

                String errorMessage;
                if(details == null) {
                    errorMessage = context.getString(basicErrorResId);
                } else {
                    errorMessage = context.getString(messageErrorResId, details);
                }

                new AlertDialog.Builder(context)
                        .setTitle(R.string.d_checkCredentials_title_fail)
                        .setMessage(errorMessage)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }

            handler.onApiAccessTestFail(result, details);
        }
    }

    private void cancelTasks() {
        cancelTask(testConnectionTask);
        cancelTask(getCredentialsTask);
        cancelTask(testApiAccessTask);
    }

    private void cancelTask(AsyncTask task) {
        if(task != null) task.cancel(true);
    }

    private void dismissDialog(Dialog dialog) {
        if(dialog == null || !dialog.isShowing()) return;

        if(context instanceof Activity) {
            if(!((Activity)context).isFinishing()) {
                dialog.dismiss();
            }
        } else {
            dialog.dismiss();
        }
    }

}
