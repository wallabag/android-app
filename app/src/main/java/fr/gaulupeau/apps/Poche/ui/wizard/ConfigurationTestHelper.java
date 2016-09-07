package fr.gaulupeau.apps.Poche.ui.wizard;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.network.tasks.TestConnectionTask;
import fr.gaulupeau.apps.Poche.network.tasks.TestFeedsTask;

public class ConfigurationTestHelper
        implements ConnectionTestHelper.ResultHandler,
        TestConnectionTask.ResultHandler, TestFeedsTask.ResultHandler {

    private static final String TAG = ConfigurationTestHelper.class.getSimpleName();

    public interface ResultHandler {
        void configurationTestOnSuccess(String url);
        void configurationTestOnFail(TestFeedsTask.Result result, String details);
    }

    protected Context context;
    protected String url;
    protected String username, password;
    protected String feedsUserID, feedsToken;
    protected String httpAuthUsername, httpAuthPassword;
    protected boolean customSSLSettings = Settings.getDefaultCustomSSLSettingsValue();
    protected boolean acceptAllCertificates;
    protected int wallabagServerVersion = -1;

    private ResultHandler handler;

    private ProgressDialog progressDialog;

    private TestConnectionTask testConnectionTask;
    private TestFeedsTask testFeedsTask;

    private boolean canceled;

    private String newUrl;

    public ConfigurationTestHelper(Context context, ResultHandler handler, Settings settings) {
        this(context, handler, settings.getUrl(), settings.getUsername(), settings.getPassword(),
                settings.getFeedsUserID(), settings.getFeedsToken(),
                settings.getHttpAuthUsername(), settings.getHttpAuthPassword(),
                settings.isCustomSSLSettings(), settings.isAcceptAllCertificates(),
                settings.getWallabagServerVersion());
    }

    public ConfigurationTestHelper(Context context, ResultHandler handler, String url,
                                   String username, String password,
                                   String feedsUserID, String feedsToken,
                                   String httpAuthUsername, String httpAuthPassword,
                                   boolean customSSLSettings, boolean acceptAllCertificates,
                                   int wallabagServerVersion) {
        this.context = context;
        this.handler = handler;
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

    @Override
    public void onTestConnectionResult(List<TestConnectionTask.TestResult> results) {
        if(progressDialog != null) progressDialog.dismiss();

        if(canceled) return;

        ConnectionTestHelper.processTestResults(context, url, results, this);
    }

    @Override
    public void connectionTestOnSuccess(String url, Integer serverVersion) {
        Log.d(TAG, "connectionTestOnSuccess() new url: " + url);
        newUrl = url;

        // TODO: string constants
        progressDialog.setMessage("Testing feeds...");
        progressDialog.show();

        testFeedsTask = new TestFeedsTask(url, feedsUserID, feedsToken,
                httpAuthUsername, httpAuthPassword,
                customSSLSettings, acceptAllCertificates,
                wallabagServerVersion, this);
        testFeedsTask.execute();
    }

    @Override
    public void connectionTestOnFail() {
        Log.i(TAG, "connectionTestOnFail() no luck");
    }

    @Override
    public void testFeedsTaskOnResult(TestFeedsTask.Result result, String details) {
        if(progressDialog != null) progressDialog.dismiss();

        if(result == TestFeedsTask.Result.OK) {
            if(handler != null) {
                handler.configurationTestOnSuccess(newUrl);
            } else {
                Settings settings = App.getInstance().getSettings();

                if(newUrl != null) {
                    settings.setUrl(newUrl);
                }

                settings.setConfigurationOk(true);

                // TODO: string constants
                new AlertDialog.Builder(context)
                        .setTitle("Test completed")
                        .setMessage("Configuration is fine")
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        } else {
            if(handler != null) {
                handler.configurationTestOnFail(result, details);
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
        cancelTask(testFeedsTask);
    }

    private void cancelTask(AsyncTask task) {
        if(task != null) task.cancel(true);
    }

}
