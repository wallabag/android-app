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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
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
import android.widget.ImageView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;


/**
 * Main activity class
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class Poche extends Activity implements FeedUpdaterInterface {
    static String apiUsername;
    static String apiToken;
    static String pocheUrl;
    private static SQLiteDatabase database;
    Button btnGetPost;
    Button btnSync;
    Button btnSettings;
    SharedPreferences settings;
    boolean nightmode;
    String action;
    Intent myIntent;

    private FeedUpdater feedUpdater;

    /**
     * Called when the activity is first created.
     * Will act differently depending on whether sharing or
     * displaying information page.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        action = intent.getAction();
        if (action == null) {
            action = Intent.ACTION_MAIN;
        }

        getSettings();
        // Find out if Sharing or if app has been launched from icon

        if (action.equals(Intent.ACTION_SEND) && !pocheUrl.equals("http://")) {
            setContentView(R.layout.main);
            findViewById(R.id.btnSync).setVisibility(View.GONE);
            findViewById(R.id.btnGetPost).setVisibility(View.GONE);
            findViewById(R.id.progressBar1).setVisibility(View.VISIBLE);


            final String extraText = extras.getString("android.intent.extra.TEXT");
            final String pageUrl;

            // Parsing string for urls.
            Matcher matcher = Patterns.WEB_URL.matcher(extraText);
            if (matcher.find()) {
                pageUrl = matcher.group();
            } else {
                showErrorMessage("Couldn't find a URL in share string:\n" + extraText);
                return;
            }


            // Vérification de la connectivité Internet
            final ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
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
        } else {
            setNightViewTheme();
            setContentView(R.layout.main);
            setNightViewIcon();

            checkAndHandleAfterUpdate();

            btnSync = (Button) findViewById(R.id.btnSync);
            btnSync.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateFeed();
                }
            });

            //11.09.2015 pass nightview variable
            final Intent listArticlesIntent = new Intent(getBaseContext(), ListArticles.class);
            listArticlesIntent.putExtra("NIGHTMODE", nightmode);
            btnGetPost = (Button) findViewById(R.id.btnGetPost);
            btnGetPost.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(listArticlesIntent);
                }
            });

            //11.09.2015 pass nightview variable
            final Intent settingsIntent = new Intent(getBaseContext(), Settings.class);
            settingsIntent.putExtra("NIGHTMODE", nightmode);

            btnSettings = (Button) findViewById(R.id.btnSettings);
            btnSettings.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(settingsIntent);
                }
            });
        }
    }

    private void updateFeed() {
        // Ensure Internet connectivity
        final ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
        if (pocheUrl.equals("http://")) {
            showToast(getString(R.string.txtConfigNotSet));
        } else if (activeNetwork != null && activeNetwork.isConnected()) {
            // Run update task
            findViewById(R.id.progressBar1).setVisibility(View.VISIBLE);
            ArticlesSQLiteOpenHelper sqLiteOpenHelper = new ArticlesSQLiteOpenHelper(this);
            feedUpdater = new FeedUpdater(pocheUrl, apiUsername, apiToken, sqLiteOpenHelper.getWritableDatabase(), this);
            feedUpdater.execute();
        } else {
            // Show message if not connected
            showToast(getString(R.string.txtNetOffline));
        }
    }

    private void checkAndHandleAfterUpdate() {
        SharedPreferences pref = getSharedPreferences(PREFS_NAME, 0);

        if (pref.getInt("update_checker", 0) < 9) {
            // Wipe Database, because we now save HTML content instead of plain text
            ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
            getDatabase();
            helper.truncateTables(database);
            showToast("Update: Wiped Database. Please synchronize.");
        }

        int versionCode;
        try {
            versionCode = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionCode;
        } catch (Exception e) {
            versionCode = 0;
        }

        pref.edit().putInt("update_checker", versionCode).commit();
    }

    private void getSettings() {
        settings = getSharedPreferences(PREFS_NAME, 0);
        pocheUrl = settings.getString("pocheUrl", "http://");
        apiUsername = settings.getString("APIUsername", "");
        apiToken = settings.getString("APIToken", "");
        nightmode = settings.getBoolean("Nightmode", false);

    }

    private void getDatabase() {
	    if (database == null) {
		    ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
		    database = helper.getReadableDatabase();
	    }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSettings();
        getDatabase();
        if (!action.equals(Intent.ACTION_SEND)) {
            updateUnread();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (feedUpdater != null) {
            feedUpdater.cancel(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (database != null) {
            database.close();
        }
    }

    private void updateUnread() {
        runOnUiThread(new Runnable() {
            public void run() {
                ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(getApplicationContext());
                getDatabase();
                if (database.isOpen()) { //Avoid attempt to re-open an already-closed object: SQLiteDatabase
                    //there should be a better way to make sure, that the unread post count lookup waits until the database is available again
                    int news = database.query(ARTICLE_TABLE, null, ARCHIVE + "=0", null, null, null, null).getCount();
                    btnGetPost.setText(String.format(getString(R.string.btnGetPost), news));
                }
            }
        });
    }

    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(Poche.this, toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showErrorMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder messageBox = new AlertDialog.Builder(Poche.this);
                messageBox.setMessage(message);
                messageBox.setTitle(getString(R.string.error));
//				messageBox.setIconAttribute(android.R.attr.alertDialogIcon);
                messageBox.setPositiveButton("OK", null);
                messageBox.setCancelable(false);
                messageBox.create().show();
            }
        });
    }

    @Override
    public void feedUpdatedFinishedSuccessfully() {
        showToast(getString(R.string.txtSyncDone));
        updateUnread();
        findViewById(R.id.progressBar1).setVisibility(View.GONE);
    }

    @Override
    public void feedUpdaterFinishedWithError(String errorMessage) {
        showErrorMessage(getString(R.string.error_feed) + errorMessage);
        updateUnread();
        findViewById(R.id.progressBar1).setVisibility(View.GONE);
    }

    private void setNightViewIcon() {
        ImageView imageView = (ImageView) findViewById(R.id.imageView1);
        if (nightmode) {
            //invert colors
            imageView.setImageResource(R.drawable.welcome_night);
            this.setTheme(R.style.app_theme_dark);
        } else {
            //reset to original
            imageView.setImageResource(R.drawable.welcome);
        }
    }

    private void setNightViewTheme() {
        if (nightmode) {
            this.setTheme(R.style.app_theme_dark);
        }
    }

    public void setBackgroundColor(int color) {
        View view = this.getWindow().getDecorView();
        view.setBackgroundColor(color);
    }
}
