package fr.gaulupeau.apps.Poche.ui;

import android.app.AlertDialog;
import android.app.ProgressDialog;
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
import java.util.Random;

import de.greenrobot.dao.query.LazyList;
import de.greenrobot.dao.query.QueryBuilder;
import fr.gaulupeau.apps.InThePoche.BuildConfig;
import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.data.DbConnection;
import fr.gaulupeau.apps.Poche.data.FeedUpdater;
import fr.gaulupeau.apps.Poche.data.FeedUpdaterInterface;
import fr.gaulupeau.apps.Poche.data.ListAdapter;
import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.data.UploadOfflineURLsTask;
import fr.gaulupeau.apps.Poche.data.WallabagSettings;
import fr.gaulupeau.apps.Poche.entity.Article;
import fr.gaulupeau.apps.Poche.entity.ArticleDao;
import fr.gaulupeau.apps.Poche.entity.DaoSession;

public class ListArticlesActivity extends AppCompatActivity implements ListAdapter.OnItemClickListener, FeedUpdaterInterface {

    private FeedUpdater feedUpdater;

    private Settings settings;

    private SwipeRefreshLayout refreshLayout;
    private RecyclerView readList;
    private boolean showArchived = false;
    private boolean prioritizeFavorites = false;

	private DaoSession mSession;
    private List<Article> mArticles;
    private ArticleDao mArticleDao;
    private ListAdapter mAdapter;

    private boolean firstTimeShown = true;

    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list);

        settings = new Settings(this);

        readList = (RecyclerView) findViewById(R.id.article_list);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        readList.setLayoutManager(layoutManager);

        refreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateFeed(true, true);
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

        if(firstTimeShown) {
            firstTimeShown = false;

            WallabagSettings wallabagSettings = WallabagSettings.settingsFromDisk(settings);
            if (!wallabagSettings.isValid()) {
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
        menu.findItem(R.id.menuShowArchived).setTitle(
                getString(showArchived ? R.string.menu_show_unread : R.string.menu_show_archived)
        );
        menu.findItem(R.id.menuPrioritizeFavorites).setTitle(
                getString(prioritizeFavorites
                        ? R.string.menu_dont_prioritize_favorites
                        : R.string.menu_prioritize_favorites)
        );

        return true;
    }

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
            case R.id.menuFastSync:
                updateFeed(true, true);
                return true;
            case R.id.menuSync:
                updateFeed(true);
                return true;
            case R.id.menuSettings:
                startActivity(new Intent(getBaseContext(), SettingsActivity.class));
                return true;
            case R.id.menuBagPage:
                startActivity(new Intent(getBaseContext(), AddActivity.class));
                return true;
			case R.id.menuShowArchived:
                showArchived = !showArchived;
				updateList();
				return true;
			case R.id.menuPrioritizeFavorites:
                prioritizeFavorites = !prioritizeFavorites;
				updateList();
				return true;
            case R.id.menuOpenRandomArticle:
                openRandomArticle();
                return true;
            case R.id.menuUploadOfflineURLs:
                uploadOfflineURLs();
                return true;
			case R.id.menuWipeDb: {
                AlertDialog.Builder b = new AlertDialog.Builder(ListArticlesActivity.this);
                b.setTitle(R.string.wipe_db_dialog_title);
                b.setMessage(R.string.wipe_db_dialog_message);
                b.setPositiveButton(R.string.position_answer, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSession.getArticleDao().deleteAll();
                        updateList();
                    }
                });
                b.setNegativeButton(R.string.negative_answer, null);
                b.create().show();
                return true;
            }
		}

        return super.onOptionsItemSelected(item);
	}

    private void updateFeed(boolean showErrors) {
        updateFeed(showErrors, false);
    }

    private void updateFeed(boolean showErrors, boolean fast) {
        WallabagSettings wallabagSettings = WallabagSettings.settingsFromDisk(settings);
        if (!wallabagSettings.isValid()) {
            refreshLayout.setRefreshing(false);
            if(showErrors) {
                Toast.makeText(ListArticlesActivity.this,
                        getString(R.string.txtConfigNotSet), Toast.LENGTH_SHORT).show();
            }
        } else if (isConnected()) {
            if (!refreshLayout.isRefreshing()) {
                refreshLayout.setRefreshing(true);
            }

            FeedUpdater.FeedType feedType = null;
            FeedUpdater.UpdateType updateType = null;
            if(fast) {
                feedType = FeedUpdater.FeedType.Main;
                updateType = FeedUpdater.UpdateType.Fast;
            }
            feedUpdater = new FeedUpdater(wallabagSettings.wallabagURL,
                    wallabagSettings.userID, wallabagSettings.userToken, this, feedType, updateType);
            feedUpdater.execute();
        } else {
            refreshLayout.setRefreshing(false);
            if(showErrors) {
                Toast.makeText(ListArticlesActivity.this,
                        getString(R.string.txtNetOffline), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isConnected() {
        ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = conMgr.getActiveNetworkInfo();

        return activeNetwork != null && activeNetwork.isConnected();
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
        QueryBuilder<Article> qb = mArticleDao.queryBuilder()
                .where(ArticleDao.Properties.Archive.eq(showArchived));

        if(prioritizeFavorites) qb.orderDesc(ArticleDao.Properties.Favorite);

        qb.orderDesc(ArticleDao.Properties.ArticleId);

        int limit = settings.getInt(Settings.LIST_LIMIT, -1);
        if(limit > 0) qb.limit(limit);

        LazyList<Article> articles = qb.listLazy();

        mArticles.clear();
        mArticles.addAll(articles);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(int position) {
        Article article = mArticles.get(position);
        openArticle(article.getId());
    }

    private void openRandomArticle() {
        QueryBuilder<Article> qb = mArticleDao.queryBuilder()
                .where(ArticleDao.Properties.Archive.eq(showArchived))
                .orderDesc(ArticleDao.Properties.ArticleId);

        LazyList<Article> articles = qb.listLazyUncached();

        if(!articles.isEmpty()) {
            long id = articles.get(new Random().nextInt(articles.size())).getId();
            articles.close();

            openArticle(id);
        } else {
            Toast.makeText(ListArticlesActivity.this, R.string.no_articles, Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadOfflineURLs() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Uploading offline URLs");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(true);

        final UploadOfflineURLsTask uploadOfflineURLsTask
                = new UploadOfflineURLsTask(this, progressDialog);

        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                uploadOfflineURLsTask.cancel(false);
            }
        });

        progressDialog.show();
        uploadOfflineURLsTask.execute();
    }

    private void openArticle(long id) {
        Intent intent = new Intent(this, ReadArticleActivity.class);
        intent.putExtra(ReadArticleActivity.EXTRA_ID, id);
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
