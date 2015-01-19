package fr.gaulupeau.apps.Poche;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_DATE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.MY_ID;
import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;

public class ListArticles extends ActionBarActivity implements FeedUpdaterInterface {

    private SQLiteDatabase database;
    private FeedUpdater feedUpdater;

    private WallabagSettings settings;

    private ListView readList;
    private boolean showAll = false;

    private boolean updatedInitially = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list);
        readList = (ListView) findViewById(R.id.liste_articles);

        ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
        database = helper.getWritableDatabase();

        checkAndHandleAfterUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateList();

        settings = WallabagSettings.settingsFromDisk(this);
        if (settings.isValid()) {
            //TODO Maybe should update once in a while, if data is getting old.
            if (!updatedInitially) {
                updatedInitially = true;
                updateFeed();
            }
        } else {
            AlertDialog.Builder messageBox = new AlertDialog.Builder(ListArticles.this);
            messageBox.setTitle("Welcome to wallabag");
            messageBox.setMessage("Please configure this app with your hosted wallabag to get started.");
            messageBox.setPositiveButton("OK",new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(getBaseContext(), Settings.class));
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
    protected void onDestroy() {
		super.onDestroy();
		database.close();
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
                startActivity(new Intent(getBaseContext(), Settings.class));
                return true;
			case R.id.menuShowAll:
                showAll = !showAll;
				updateList();
				return true;
			case R.id.menuWipeDb:
				ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
				helper.truncateTables(database);
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
        if (!settings.isValid()) {
            showToast(getString(R.string.txtConfigNotSet));
        } else if (activeNetwork != null && activeNetwork.isConnected()) {
            // Run update task
            ArticlesSQLiteOpenHelper sqLiteOpenHelper = new ArticlesSQLiteOpenHelper(this);
            feedUpdater = new FeedUpdater(settings.wallabagURL, settings.userID, settings.userToken, sqLiteOpenHelper.getWritableDatabase(), this);
            feedUpdater.execute();
        } else {
            // Show message if not connected
            showToast(getString(R.string.txtNetOffline));
        }
    }

    @Override
    public void feedUpdatedFinishedSuccessfully() {
        updateList();
        showToast("Updated Feed");
    }

    @Override
    public void feedUpdaterFinishedWithError(String errorMessage) {
        showErrorMessage(errorMessage);
    }

    private void updateList() {
        CursorAdapter adapter = (CursorAdapter) readList.getAdapter();
        if (adapter != null) {
            adapter.changeCursor(getCursor());
        } else {
            setupListAdapter();
        }
        setTitle(getString(R.string.app_name) + " | " + getResources().getQuantityString(R.plurals.numberOfArticles, readList.getCount(), readList.getCount()));
    }

	private void setupListAdapter() {
        CursorAdapter adapter = getCursorAdapter();
        readList.setAdapter(adapter);

		readList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent i = new Intent(getBaseContext(), ReadArticle.class);
                // As we use a CursorAdapter the id's are the same as in our SQLite Database.
				i.putExtra("id", id);
				startActivity(i);
			}
		});
	}

    private Cursor getCursor() {
        String filter = null;
        if (!showAll) {
            filter = ARCHIVE + "=0";
        }
        // the " as _id" extension is important, as a CursorAdapter needs a column named '_id' to work
        // with this extension we get something starting like "Select id as _id,"...
        String[] columns = new String[]{MY_ID + " as _id", ARTICLE_TITLE, ARTICLE_URL};
        return database.query(
                ARTICLE_TABLE,
                columns,
                filter, null, null, null, ARTICLE_DATE + " DESC");
    }

    @TargetApi(11)
    private CursorAdapter getCursorAdapter() {
        int layout = R.layout.article_list;
        String[] columns = new String[]{ARTICLE_TITLE, ARTICLE_URL};
        int[] toIds = new int[]{R.id.listitem_titre, R.id.listitem_textview_url};

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return getCursorAdapterPreHoneycomb(layout, columns, toIds);
        }
        return new SimpleCursorAdapter(this, layout, getCursor(), columns, toIds, 0);
    }

    @TargetApi(8)
    private CursorAdapter getCursorAdapterPreHoneycomb(int layout, String[] from, int[] to) {
        return new SimpleCursorAdapter(this, layout, getCursor(), from, to);
    }

    private void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(ListArticles.this, toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showErrorMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder messageBox = new AlertDialog.Builder(ListArticles.this);
                messageBox.setMessage(message);
                messageBox.setTitle(getString(R.string.error));
                messageBox.setPositiveButton("OK", null);
                messageBox.setCancelable(false);
                messageBox.create().show();
            }
        });
    }

    private void checkAndHandleAfterUpdate() {
        SharedPreferences pref = getSharedPreferences(PREFS_NAME, 0);

        if (pref.getInt("update_checker", 0) < 9) {
            // Wipe Database, because we now save HTML content instead of plain text
            ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
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
}
