package fr.gaulupeau.apps.Poche.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.dao.query.LazyList;
import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.FeedUpdater;
import fr.gaulupeau.apps.Poche.data.FeedUpdaterInterface;
import fr.gaulupeau.apps.Poche.data.ListAdapter;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.WallabagSettings;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;

public class ListArticlesActivity extends AppCompatActivity implements ListAdapter.OnItemClickListener, FeedUpdaterInterface {

    private FeedUpdater feedUpdater;

    private Settings settings;
    private WallabagSettings wallabagSettings;

    private SwipeRefreshLayout refreshLayout;
    private RecyclerView readList;
    private boolean showAll = false;

	private DaoSession mSession;
    private List<Article> mArticles;
    private ArticleDao mArticleDao;
    private ListAdapter mAdapter;

    private boolean updatedInitially = false;

    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list);

        settings = new Settings(this);
        wallabagSettings = WallabagSettings.settingsFromDisk(settings);

        readList = (RecyclerView) findViewById(R.id.article_list);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        readList.setLayoutManager(layoutManager);

        refreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateFeed();
            }
        });

		mSession = DbConnection.getSession();
        mArticleDao = mSession.getArticleDao();

        mArticles = new ArrayList<>();

        mAdapter = new ListAdapter(mArticles, this);
        readList.setAdapter(mAdapter);

        checkAndHandleAfterUpdate();
	}

    @Override
    protected void onResume() {
        super.onResume();
        updateList();

        if (wallabagSettings.isValid()) {
            //TODO Maybe should update once in a while, if data is getting old.
            if (!updatedInitially) {
                updatedInitially = true;
                updateFeed();
            }
        } else {
            AlertDialog.Builder messageBox = new AlertDialog.Builder(ListArticlesActivity.this);
            messageBox.setTitle("Welcome to wallabag");
            messageBox.setMessage("Please configure this app with your hosted wallabag to get started.");
            messageBox.setPositiveButton("OK",new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(getBaseContext(), SettingsActivity.class));
                }
            });
            messageBox.setCancelable(false);
            messageBox.create().show();
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
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_list, menu);
		return true;
	}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menuShowAll).setTitle(getString(showAll ? R.string.menuShowUnread : R.string.menuShowAll));
        return true;
    }

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
            case R.id.menuSync:
                updateFeed();
                return true;
            case R.id.menuSettings:
                startActivity(new Intent(getBaseContext(), SettingsActivity.class));
                return true;
            case R.id.menuBagPage:
                startActivity(new Intent(getBaseContext(), AddActivity.class));
                return true;
			case R.id.menuShowAll:
                showAll = !showAll;
				updateList();
				return true;
			case R.id.menuWipeDb:
                mSession.getArticleDao().deleteAll();
				updateList();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

    private void updateFeed() {
        // Ensure Internet connectivity
        final ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();
        if (!wallabagSettings.isValid()) {
            Toast.makeText(ListArticlesActivity.this, getString(R.string.txtConfigNotSet), Toast.LENGTH_SHORT).show();
            refreshLayout.setRefreshing(false);
        } else if (activeNetwork != null && activeNetwork.isConnected()) {
            if (!refreshLayout.isRefreshing()) {
                refreshLayout.setRefreshing(true);
            }

            // Run update task
            feedUpdater = new FeedUpdater(wallabagSettings.wallabagURL, wallabagSettings.userID, wallabagSettings.userToken, this);
            feedUpdater.execute();
        } else {
            // Show message if not connected
            refreshLayout.setRefreshing(false);
            Toast.makeText(ListArticlesActivity.this, getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void feedUpdatedFinishedSuccessfully() {
        updateList();
        refreshLayout.setRefreshing(false);
        Toast.makeText(ListArticlesActivity.this, R.string.txtSyncDone, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void feedUpdaterFinishedWithError(String errorMessage) {
        refreshLayout.setRefreshing(false);
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.error_feed) + ": " + errorMessage)
                .setTitle(getString(R.string.error))
                .setPositiveButton("OK", null)
                .setCancelable(false)
                .create().show();
    }

    private void updateList() {
        LazyList<Article> articles = mArticleDao.queryBuilder()
                .where(ArticleDao.Properties.Archive.notEq(true))
                .orderDesc(ArticleDao.Properties.ArticleId)
                .limit(50)
                .listLazy();
        mArticles.clear();
        mArticles.addAll(articles);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(int position) {
        Article article = mArticles.get(position);
        Intent intent = new Intent(this, ReadArticleActivity.class);
        intent.putExtra(ReadArticleActivity.EXTRA_ID, article.getId());
        startActivity(intent);
    }

    private void checkAndHandleAfterUpdate() {
        if (settings.hasUpdateChecker() && settings.getPrevAppVersion() < BuildConfig.VERSION_CODE) {
            new AlertDialog.Builder(this)
                    .setTitle("App update")
                    .setMessage("This a breaking update.\n\nMake sure you fill in your Username and Password in settings, otherwise things will be broken.")
                    .setPositiveButton("OK", null)
                    .setCancelable(false)
                    .create().show();

            Log.d("Poche", "Do upgrade stuff if needed");
        }

        settings.setAppVersion(BuildConfig.VERSION_CODE);
    }
}
