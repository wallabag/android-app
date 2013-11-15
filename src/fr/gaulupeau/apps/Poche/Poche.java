/**
 * Android to Poche
 * A simple app to make the full save bookmark to Poche
 * web page available via the Share menu on Android tablets
 * @author GAULUPEAU Jonathan
 * August 2013
 */

package fr.gaulupeau.apps.Poche;

import fr.gaulupeau.apps.InThePoche.R;
import java.io.UnsupportedEncodingException;
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
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.text.Html;
import android.util.Base64;
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
	
    /** Called when the activity is first created. 
     * Will act differently depending on whether sharing or
     * displaying information page. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        String action = intent.getAction();
        settings = getSharedPreferences(PREFS_NAME, 0);
        pocheUrl = settings.getString("pocheUrl", "http://");
        globalToken = settings.getString("globalToken", "");
        apiUsername = settings.getString("APIUsername", "");
        apiToken = settings.getString("APIToken", "");
        
        // Find out if Sharing or if app has been launched from icon
        if (action.equals(Intent.ACTION_SEND) && pocheUrl != "http://") {
        	// ACTION_SEND is called when sharing, get the title and URL from 
        	// the call
        	String pageUrl = extras.getString("android.intent.extra.TEXT");
            // Start to build the poche URL
			Uri.Builder pocheSaveUrl = Uri.parse(pocheUrl).buildUpon();
			// Add the parameters from the call
			pocheSaveUrl.appendQueryParameter("action", "add");
			byte[] data = null;
			try {
				data = pageUrl.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			String base64 = Base64.encodeToString(data, Base64.DEFAULT);
			pocheSaveUrl.appendQueryParameter("url", base64);
			
			// Load the constructed URL in the browser
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(pocheSaveUrl.build());
			i.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
			// If user has more then one browser installed give them a chance to
			// select which one they want to use 
			
			startActivity(i);
			this.finish();
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
    
    @Override
    protected void onResume() {
    	super.onResume();
    	updateUnread();
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
    	database.close();
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

}
