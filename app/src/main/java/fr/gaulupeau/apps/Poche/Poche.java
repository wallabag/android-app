/**
 * Android to Poche
 * A simple app to make the full save bookmark to Poche
 * web page available via the Share menu on Android tablets
 * @author GAULUPEAU Jonathan
 * August 2013
 */

package fr.gaulupeau.apps.Poche;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.util.Base64;
import android.util.Patterns;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_CONTENT;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_DATE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_SYNC;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;
import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;


/**
 * Main activity class
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class Poche extends Activity {

    WallabagSettings settings;
    String action;

    /**
     * Called when the activity is first created.
     * Will act differently depending on whether sharing or
     * displaying information page.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        action = intent.getAction();

        settings = WallabagSettings.settingsFromDisk(this);

        // Find out if Sharing or if app has been launched from icon
        if (action.equals(Intent.ACTION_SEND) && settings.isValid()) {
            setContentView(R.layout.main);
            findViewById(R.id.btnSync).setVisibility(View.GONE);
            findViewById(R.id.btnGetPost).setVisibility(View.GONE);
            findViewById(R.id.progressBar1).setVisibility(View.VISIBLE);

            addLink(extras);
        } else {
            showToast("Please configure wallabag before adding links.");
        }
    }

    private void addLink(Bundle extras) {
        final String extraText = extras.getString("android.intent.extra.TEXT");
        final String pageUrl;

        // Parsing string for urls.
        Matcher matcher = Patterns.WEB_URL.matcher(extraText);
        if (matcher.find()) {
            pageUrl = matcher.group();
        } else {
            showToast("Couldn't find a URL in share string:\n" + extraText);
            return;
        }


        // Vérification de la connectivité Internet
        final ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnected()) {
            // Start to build the poche URL
            Uri.Builder pocheSaveUrl = Uri.parse(settings.wallabagURL).buildUpon();
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
        } else {
            // Afficher alerte connectivité
            showToast(getString(R.string.txtNetOffline));
        }
    }

    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(Poche.this, toast, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
