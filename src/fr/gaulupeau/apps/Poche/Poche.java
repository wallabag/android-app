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

import org.alexd.jsonrpc.JSONRPCClient;
import org.alexd.jsonrpc.JSONRPCException;
import org.alexd.jsonrpc.JSONRPCParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_ID;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_CONTENT;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.Helpers.getInputStreamFromUrl;

/**
 * Main activity class
 */
@TargetApi(Build.VERSION_CODES.FROYO) public class Poche extends Activity {
	TextView authorSite;
	private SQLiteDatabase database;
	Button btnDone;
	Button btnGetPost;
	Button btnSync;
	EditText editPocheUrl;
    /** Called when the activity is first created. 
     * Will act differently depending on whether sharing or
     * displaying information page. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        String action = intent.getAction();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String pocheUrl = settings.getString("pocheUrl", "http://");
        
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
				// TODO Auto-generated catch block
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
			// That is all this app needs to do, so call finish()
			this.finish();
        }
        else {
        	// app has been launched from menu - show information window
        	setContentView(R.layout.main);
            // handle done/close button


            btnSync = (Button)findViewById(R.id.btnSync);
            btnSync.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
//					JSONRPCClient client = JSONRPCClient.create("http://v2.inthepoche.com/jsonrpc.php", JSONRPCParams.Versions.VERSION_2);
//					client.setConnectionTimeout(8000);
//					client.setSoTimeout(8000);
//					JSONObject params = new JSONObject();
//					try {
//						params.put("admin", "PYBRAYc4ebUuRoa");
//						String ret = client.callString("authentication");
//						Log.e("API", ret);
//					} catch (Exception e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
					
					String ret = getInputStreamFromUrl("http://poche.gaulupeau.fr/toto.php");
					ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(getApplicationContext());
					database = helper.getWritableDatabase();
					try {
						JSONArray rootobj = new JSONArray(ret);
						for (int i=0; i<rootobj.length(); i++) {
							JSONObject article = rootobj.getJSONObject(i);
							ContentValues values = new ContentValues();
							values.put(ARTICLE_TITLE, Html.fromHtml(article.getString("titre")).toString());
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
					}
				}
			});
            
            btnGetPost = (Button)findViewById(R.id.btnGetPost);
			btnGetPost.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(getBaseContext(), ListArticles.class));
				}
			});
			
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
                // write code to execute when clicked on this option
            	//return true;   
    		default:
    			return super.onOptionsItemSelected(item);
        }
    }
    
}
