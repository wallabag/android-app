package fr.gaulupeau.apps.Poche.data;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;

public class GetCredentialsTask extends AsyncTask<Void, Void, Boolean> {

    private Context context;
    private final String endpoint;
    private final String username;
    private final String password;
    private EditText userId;
    private EditText token;
    private ProgressDialog progressDialog;
    private WallabagService.FeedsCredentials credentials;

    public GetCredentialsTask(Context context, String endpoint, String username, String password,
                              EditText userId, EditText token, ProgressDialog progressDialog) {
        this.context = context;
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
        this.userId = userId;
        this.token = token;
        this.progressDialog = progressDialog;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        WallabagService service = new WallabagService(endpoint, username, password);
        try {
            credentials = service.getCredentials();

            return credentials != null;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if(progressDialog != null) progressDialog.dismiss();

        if (success) {
            userId.setText(credentials.userID);
            token.setText(credentials.token);

            if(context != null)
                Toast.makeText(context, "Credentials filled", Toast.LENGTH_SHORT).show();
        } else {
            if(context != null)
                Toast.makeText(context, "Couldn't get credentials", Toast.LENGTH_SHORT).show();
        }
    }

}
