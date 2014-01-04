/**
 * Android to Poche
 * A simple app to make the full save bookmark to Poche
 * web page available via the Share menu on Android tablets
 * @author GAULUPEAU Jonathan
 * August 2013
 */

package fr.gaulupeau.apps.Poche;

import fr.gaulupeau.apps.InThePoche.R;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_ID;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_CONTENT;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_SYNC;

/**
 * Main activity class
 */
@TargetApi(Build.VERSION_CODES.FROYO) public class Poche extends Activity {
	private SQLiteDatabase database;
	Button btnDone;
	Button btnGetPost;
	Button btnSync;
	EditText editPocheUrl;
	SharedPreferences settings;
	String globalToken;
	String apiUsername;
	String apiToken;
	String pocheUrl;
	String action;
	
    /** Called when the activity is first created. 
     * Will act differently depending on whether sharing or
     * displaying information page. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        action = intent.getAction();

        getSettings();
        // Find out if Sharing or if app has been launched from icon
        if (action.equals(Intent.ACTION_SEND) && pocheUrl != "http://") {
        	setContentView(R.layout.main);
        	findViewById(R.id.btnSync).setVisibility(View.GONE);
        	findViewById(R.id.btnGetPost).setVisibility(View.GONE);
        	findViewById(R.id.progressBar1).setVisibility(View.VISIBLE);
        	final String pageUrl = extras.getString("android.intent.extra.TEXT");
        	// Vérification de la connectivité Internet
			 final ConnectivityManager conMgr =  (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			 final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
			 if (activeNetwork != null && activeNetwork.isConnected()) {
				 // Exécution de la synchro en arrière-plan
				 new Thread(new Runnable() {
					 public void run() {
						 pocheIt(pageUrl);
					 }
				 }).start();
			 } else {
				 // Afficher alerte connectivité
				 showToast(getString(R.string.txtNetOffline));
			 }
        }
        else {
        	setContentView(R.layout.main);

            btnSync = (Button)findViewById(R.id.btnSync);
            btnSync.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					 // Vérification de la connectivité Internet
					 final ConnectivityManager conMgr =  (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
					 final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
					 if (activeNetwork != null && activeNetwork.isConnected()) {
						 // Exécution de la synchro en arrière-plan
						 new Thread(new Runnable() {
							 public void run() {
								 pushRead();
								 fetchUnread();
							 }
						 }).start();
					 } else {
						 // Afficher alerte connectivité
						 showToast(getString(R.string.txtNetOffline));
					 }
					
				}
			});
            
            btnGetPost = (Button)findViewById(R.id.btnGetPost);
            updateUnread();
            
			btnGetPost.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(getBaseContext(), ListArticles.class));
				}
			});
			
        }
    }
    
    private void getSettings(){
        settings = getSharedPreferences(PREFS_NAME, 0);
        pocheUrl = settings.getString("pocheUrl", "http://");
        globalToken = settings.getString("globalToken", "");
        apiUsername = settings.getString("APIUsername", "");
        apiToken = settings.getString("APIToken", "");
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	getSettings();
    	if (! action.equals(Intent.ACTION_SEND)){
    		updateUnread();
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.option, menu);
    	return true;
    } 

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.menuSettings:
    		startActivity(new Intent(getBaseContext(), Settings.class));
    	default:
    		return super.onOptionsItemSelected(item);
    	}
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if (database != null) {
    		database.close();
		}
    }
    
    private void updateUnread(){
    	runOnUiThread(new Runnable() {
    		public void run()
    		{
    			ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(getApplicationContext());
    			database = helper.getReadableDatabase();
    			int news = database.query(ARTICLE_TABLE, null, ARCHIVE + "=0", null, null, null, null).getCount();
    			btnGetPost.setText(getString(R.string.btnGetPost) + " - " + news + " unread");
    		}
    	});
    }
    
    public void showToast(final String toast)
    {
    	runOnUiThread(new Runnable() {
    		public void run()
    		{
    			Toast.makeText(Poche.this, toast, Toast.LENGTH_SHORT).show();
    		}
    	});
    }
    
    
    public void pocheIt(String url){
    	String id ="req-001";
    	JSONRPC2Request reqOut = null;
    	try{
    		reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.add\",\"id\":\"" + id + "\",\"params\":[{\"username\":\""+ apiUsername + "\",\"api_token\":\""+ apiToken +"\"}, \"" + url + "\", true]}");
    		System.err.println(reqOut.toString());
    		JSONRPC2Response response = sendRequest(reqOut);
    		if (response.indicatesSuccess()) {
    			showToast(getString(R.string.txtSyncDone));
    		}
    	} catch (JSONRPC2ParseException e2) {
    		e2.printStackTrace();
    		showToast(getString(R.string.txtSyncFailed));
    	}
    	finish();
    }
    
    
    public void pushRead(){
    	JSONRPC2Request reqOut = null;
    	String filter = ARCHIVE + "=1 AND " + ARTICLE_SYNC + "=0";
    	String[] getStrColumns = new String[] {ARTICLE_ID};
		Cursor ac = database.query(
				ARTICLE_TABLE,
				getStrColumns,
				filter, null, null, null, null);
		ac.moveToFirst();
		if(!ac.isAfterLast()) {
			do {
				String article_id = ac.getString(0);
				String id ="req-001";
				try{
		    		reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.mark_as_read\",\"id\":\"" + id + "\",\"params\":[{\"username\":\""+ apiUsername + "\",\"api_token\":\""+ apiToken +"\"}, " + article_id + "]}");
		    		System.err.println(reqOut.toString());
		    		JSONRPC2Response response = sendRequest(reqOut);
		    		if (response.indicatesSuccess()) {
		    			ContentValues values = new ContentValues();
		    			values.put(ARTICLE_SYNC, 1);
						database.update(ARTICLE_TABLE, values, ARTICLE_ID + "=" + article_id, null);
					}
		    	} catch (JSONRPC2ParseException e2) {
		    		e2.printStackTrace();
		    	}
			} while (ac.moveToNext());
		}
		ac.close();
    	
    }

    
    public void updateReads(){
    	String lastUpdate = settings.getString("lastUpdate", "");
    	SharedPreferences.Editor editor = settings.edit();
    	editor.putString("lastUpdate", "");
    	//TODO Continuer la fonction en se basant sur le timestamp "update" de Poche
    }
    
    
    public void fetchUnread(){
    	String id = "req-001";
    	JSONRPC2Request reqOut = null;
    	try {
    		// POCHE A LINK
    		//reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.add\",\"id\":\"req-001\",\"params\":[{\"username\":\"poche\",\"api_token\":\"cPG2urVgA+ToMXY\"},\"http://cdetc.fr\",true]}");
    		// GET A LINK
    		//reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.info\",\"id\":\"" + id + "\",\"params\":[{\"username\":\""+ apiUsername + "\",\"api_token\":\""+ apiToken +"\"}, 1]}");
    		// GET ALL UNREAD
    		reqOut = JSONRPC2Request.parse("{\"jsonrpc\":\"2.0\",\"method\":\"item.list_unread\",\"id\":\"" + id + "\",\"params\":[{\"username\":\""+ apiUsername + "\",\"api_token\":\""+ apiToken +"\"}, null, null]}");
    		System.err.println(reqOut.toString());
    	} catch (JSONRPC2ParseException e2) {
    		e2.printStackTrace();
    	}
    	System.out.println(reqOut.toString());
    	URL url = null;
    	try {
    		final String rpcuser ="api_user";
    		final String rpcpassword = globalToken;

    		Authenticator.setDefault(new Authenticator() {
    			protected PasswordAuthentication getPasswordAuthentication() {
    				return new PasswordAuthentication (rpcuser, rpcpassword.toCharArray());
    			}});
    		url = new URL(pocheUrl + "/jsonrpc.php");
    	} catch (MalformedURLException e1) {
    		e1.printStackTrace();
    	}
    	JSONRPC2Session session = new JSONRPC2Session(url);
    	JSONRPC2Response response = null;
    	try{
    		response = session.send(reqOut);
    	} catch (JSONRPC2SessionException e) {

    		System.err.println(e.getMessage());
    	}
    	if (response.indicatesSuccess()){
    		JSONObject article = null;
    		ContentValues values = new ContentValues();
    		try {
    			JSONArray ret = new JSONArray(response.getResult().toString());
    			for (int i = 0; i < ret.length(); i++) {
    				article = ret.getJSONObject(i);
    				values.put(ARTICLE_TITLE, Html.fromHtml(article.getString("title")).toString());
    				values.put(ARTICLE_CONTENT, Html.fromHtml(article.getString("content")).toString());
    				values.put(ARTICLE_ID, Html.fromHtml(article.getString("id")).toString());
    				values.put(ARTICLE_URL, Html.fromHtml(article.getString("url")).toString());
    				values.put(ARCHIVE, 0);
    				values.put(ARTICLE_SYNC, 0);
    				try {
    					database.insertOrThrow(ARTICLE_TABLE, null, values);
    				} catch (SQLiteConstraintException e) {
    					continue;
    				}
    			}
    		} catch (JSONException e) {
    			e.printStackTrace();
    			showToast(getString(R.string.txtSyncFailed));
    		}

    		showToast(getString(R.string.txtSyncDone));
    		updateUnread();
    	}else{
    		System.out.println(response.getError().getMessage( ));
    		showToast(getString(R.string.txtSyncFailed));
    	}
    }
    
    public JSONRPC2Response sendRequest(JSONRPC2Request reqOut){
    	URL url = null;
    	try {
    		final String rpcuser ="api_user";
    		final String rpcpassword = globalToken;

    		Authenticator.setDefault(new Authenticator() {
    			protected PasswordAuthentication getPasswordAuthentication() {
    				return new PasswordAuthentication (rpcuser, rpcpassword.toCharArray());
    			}});
    		url = new URL(pocheUrl + "/jsonrpc.php");
    	} catch (MalformedURLException e1) {
    		e1.printStackTrace();
    	}
    	JSONRPC2Session session = new JSONRPC2Session(url);
    	JSONRPC2Response response = null;
    	try{
    		response = session.send(reqOut);
    	} catch (JSONRPC2SessionException e) {

    		System.err.println(e.getMessage());
    	}
    	return response;
    }

}
