package fr.gaulupeau.apps.Poche.ui.wizard;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import java.util.List;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.network.WallabagServiceEndpoint;
import fr.gaulupeau.apps.Poche.network.tasks.TestConnectionTask;

public class ConfigurationTestHelper {

    private static final String TAG = ConfigurationTestHelper.class.getSimpleName();

    public interface ResultHandler {
        void connectionTestOnSuccess(String url, Integer serverVersion);
        void connectionTestOnFail();
    }

    public static void processTestResults(Context context, String originalUrl,
                                          List<TestConnectionTask.TestResult> results,
                                          ResultHandler handler) {
        if(results != null) {
            if(results.isEmpty()) { // should not happen?
                Log.w(TAG, "processTestResults(): results are empty");
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
                    if(TestConnectionTask.areUrlsEqual(url, originalUrl)) {
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
                    handler.connectionTestOnSuccess(bestUrl, bestUrlServerVersion);
                } else if(bestUrl != null) { // there is a better option
                    showSuggestionDialog(context, handler, bestUrl, bestUrlServerVersion,
                            originalUrlServerVersion, isOriginalOk);
                } else { // all the options have failed
                    showErrorDialog(context, error, errorMessage);
                    handler.connectionTestOnFail();
                }
            }
        } else { // should not happen
            Log.w(TAG, "processTestResults(): results are null");
        }
    }

    private static void showSuggestionDialog(final Context context,
                                               final ResultHandler handler,
                                               final String suggestedUrl,
                                               final int suggestedUrlServerVersion,
                                               final int originalUrlServerVersion,
                                               boolean allowToDecline) {
        // TODO: string constants
        AlertDialog.Builder b = new AlertDialog.Builder(context)
                .setTitle("Consider changing URL")
                .setPositiveButton("Use suggested", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handler.connectionTestOnSuccess(
                                suggestedUrl, suggestedUrlServerVersion);
                    }
                });

        if(allowToDecline) {
            b.setMessage(String.format("It is recommended to change URL to: \"%s\". "
                    + "But you may use the entered one too.", suggestedUrl))
                    .setNegativeButton("Use mine", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            handler.connectionTestOnSuccess(
                                    null, originalUrlServerVersion);
                        }
                    });
        } else {
            b.setMessage(String.format("It is suggested to change URL to: \"%s\"", suggestedUrl))
                    .setNegativeButton("Cancel", null);
        }
        b.show();
    }

    private static void showErrorDialog(Context context,
                                          WallabagServiceEndpoint.ConnectionTestResult error,
                                          String errorMessage) {
        // TODO: string constants
        // TODO: human-readable error message
        new AlertDialog.Builder(context)
                .setTitle("Connection test failed")
                .setMessage("Error: " + ((error != null) ? error : errorMessage))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

}
