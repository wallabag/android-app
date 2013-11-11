/**
 * Android to Poche
 * A simple app to make the full save bookmark to Poche
 * web page available via the Share menu on Android tablets
 * @author GAULUPEAU Jonathan
 * August 2013
 */

package fr.gaulupeau.apps.Poche;

import fr.gaulupeau.apps.InThePoche.R;
import static fr.gaulupeau.apps.Poche.Helpers.getInputStreamFromUrl;

import java.io.UnsupportedEncodingException;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.text.Html;
import android.util.Base64;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;

/**
 * Main activity class
 */
@TargetApi(Build.VERSION_CODES.FROYO) public class Poche extends Activity {
	TextView authorSite;
	
	Button btnDone;
	Button btnGetPost;
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
            // Start to build the Delicious URL
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
			System.out.println("base64 : " + base64);
			System.out.println("pageurl : " + pageUrl);
			
			
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
        	editPocheUrl = (EditText)findViewById(R.id.pocheUrl);
        	editPocheUrl.setText(pocheUrl);
        	
            btnDone = (Button)findViewById(R.id.btnDone);
            btnDone.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					// close the app
					SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		        	SharedPreferences.Editor editor = settings.edit();
		        	editor.putString("pocheUrl", editPocheUrl.getText().toString());
					editor.commit();
					Poche.this.finish();
				}
            });

            
            btnGetPost = (Button)findViewById(R.id.btnGetPost);
			btnGetPost.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(getBaseContext(), ReadArticle.class));
				}
			});
			
        }
    }


    
}
