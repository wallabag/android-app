/**
 * Android to Poche
 * A simple app to make the full save bookmark to Poche
 * web page available via the Share menu on Android tablets
 * @author GAULUPEAU Jonathan
 * August 2013
 */

package fr.gaulupeau.apps.Poche.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import de.greenrobot.dao.query.LazyList;
import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.FeedUpdater;
import fr.gaulupeau.apps.Poche.data.FeedUpdaterInterface;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;


/**
 * Main activity class
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class PocheActivity extends Activity implements FeedUpdaterInterface {

    private static final String TAG = PocheActivity.class.getSimpleName();
    Button btnGetPost;
    Button btnSync;
    Button btnSettings;
    String action;

    private FeedUpdater feedUpdater;
    private Settings settings;

    private String mUrl;
    private String mUserId;
    private String mToken;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        action = intent.getAction();

        settings = ((App) getApplication()).getSettings();

        getSettings();

        setContentView(R.layout.main);
        checkAndHandleAfterUpdate();

        btnSync = (Button) findViewById(R.id.btnSync);
        btnSync.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateFeed();
            }
        });

        btnGetPost = (Button) findViewById(R.id.btnArticles);
        btnGetPost.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getBaseContext(), ListArticlesActivity.class));
            }
        });

        btnSettings = (Button) findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getBaseContext(), SettingsActivity.class));
            }
        });

        findViewById(R.id.add).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PocheActivity.this, AddActivity.class));
            }
        });
    }

    private void updateFeed() {
        if (mUrl == null) {
            Toast.makeText(PocheActivity.this, R.string.txtConfigNotSet, Toast.LENGTH_SHORT).show();
            return;
        }

        // Run update task
        findViewById(R.id.progressBar1).setVisibility(View.VISIBLE);
        feedUpdater = new FeedUpdater(mUrl, mUserId, mToken, this);
        feedUpdater.execute();
    }

    private void checkAndHandleAfterUpdate() {
        if (settings.hasUpdateChecher() && settings.getPrevAppVersion() < BuildConfig.VERSION_CODE) {
            new AlertDialog.Builder(this)
                    .setTitle("App update")
                    .setMessage("This a breaking update.\n\nMake sure you fill in your Username and Password in settings, otherwise things will be broken.")
                    .setPositiveButton("OK", null)
                    .setCancelable(false)
                    .create().show();
        } else if (settings.getPrevAppVersion() < BuildConfig.VERSION_CODE) {
            Log.d(TAG, "Do upgrade stuff if needed");
        }

        settings.setAppVersion(BuildConfig.VERSION_CODE);
    }

    private void getSettings() {
        mUrl = settings.getKey(Settings.URL);
        mUserId = settings.getKey(Settings.USER_ID);
        mToken = settings.getKey(Settings.TOKEN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSettings();
        updateUnread();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (feedUpdater != null) {
            feedUpdater.cancel(true);
        }
    }

    private void updateUnread() {
        DaoSession session = DbConnection.getSession();
        ArticleDao articleDao = session.getArticleDao();
        LazyList<fr.gaulupeau.apps.Poche.entity.Article> articles = articleDao.queryBuilder().where(ArticleDao.Properties.Archive.eq(false)).build().listLazy();
        btnGetPost.setText(String.format(getString(R.string.btnGetPost), articles.size()));
    }

    @Override
    public void feedUpdatedFinishedSuccessfully() {
        Toast.makeText(PocheActivity.this, R.string.txtSyncDone, Toast.LENGTH_SHORT).show();
        updateUnread();
        findViewById(R.id.progressBar1).setVisibility(View.GONE);
    }

    @Override
    public void feedUpdaterFinishedWithError(String errorMessage) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.error_feed) + errorMessage)
                .setTitle(getString(R.string.error))
                .setPositiveButton("OK", null)
                .setCancelable(false)
                .create().show();

        updateUnread();
        findViewById(R.id.progressBar1).setVisibility(View.GONE);
    }
}
