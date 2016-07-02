package fr.gaulupeau.apps.Poche.network.tasks;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import java.io.IOException;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.network.WallabagService;
import fr.gaulupeau.apps.Poche.network.exceptions.IncorrectConfigurationException;

public class TestConnectionTask extends AsyncTask<Void, Void, Integer> {

    private static final String TAG = TestConnectionTask.class.getSimpleName();

    private Context context;
    private final String endpoint;
    private final String username;
    private final String password;
    private ProgressDialog progressDialog;
    private ResultHandler resultHandler;
    private String errorMessage;

    public TestConnectionTask(Context context, String endpoint, String username, String password,
                              ResultHandler resultHandler, ProgressDialog progressDialog) {
        this.context = context;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.resultHandler = resultHandler;
        this.progressDialog = progressDialog;
    }

    @Override
    protected Integer doInBackground(Void... params) {
        WallabagService service = new WallabagService(endpoint, username, password, -1);
        try {
            int result = service.testConnection();

            Log.d(TAG, "Connection test result code: " + result);

            return result;
        } catch (IncorrectConfigurationException | IOException e) {
            Log.d(TAG, "Connection test: Exception", e);
            errorMessage = e.getMessage();
            return null;
        }
    }

    @Override
    protected void onPostExecute(Integer result) {
        boolean success = result != null && result == 0;
        if(resultHandler != null) resultHandler.onTestConnectionResult(success);
        if(progressDialog != null) progressDialog.dismiss();

        if(context == null) return;

        if(success) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.d_testConnection_success_title)
                    .setMessage(R.string.d_connectionTest_success_text)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else {
            if(result != null) {
                switch(result) {
                    case 1:
                        errorMessage = context.getString(R.string.testConnection_errorMessage1,
                                result);
                        break;

                    case 2:
                        errorMessage = context.getString(R.string.testConnection_errorMessage2,
                                result);
                        break;

                    case 3:
                        errorMessage = context.getString(R.string.testConnection_errorMessage3,
                                result);
                        break;

                    case 5:
                        errorMessage = context.getString(R.string.testConnection_errorMessage5,
                                result);
                        break;

                    case 6:
                        errorMessage = context.getString(R.string.testConnection_errorMessage6,
                                result);
                        break;

                    default:
                        errorMessage = context.getString(R.string.testConnection_errorMessage_unknown,
                                result);
                }
            }
            new AlertDialog.Builder(context)
                    .setTitle(R.string.d_testConnection_fail_title)
                    .setMessage(errorMessage)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    public interface ResultHandler {
        void onTestConnectionResult(boolean success);
    }

}
