package fr.gaulupeau.apps.Poche;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_DATE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.MY_ID;

public class ListArticles extends BaseActionBarActivity {

	private SQLiteDatabase database;
    private ListView readList;
    private boolean showAll = false;
    private boolean nightmode;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        //11.09.2015 Nightmode
        nightmode = getIntent().getBooleanExtra("NIGHTMODE", false);
        if (nightmode) {
            Helpers myHelper = new Helpers();
            myHelper.setNightViewTheme(nightmode, this);
        }

        setContentView(R.layout.list);
        readList = (ListView) findViewById(R.id.liste_articles);
        ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
        database = helper.getWritableDatabase();
		updateList();
	}

    @Override
    protected void onResume() {
        super.onResume();
        updateList();
    }

    public void onDestroy() {
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

    private void updateList() {
        MyCursorAdapter adapter = (MyCursorAdapter) readList.getAdapter();
        if (adapter != null) {
            adapter.changeCursor(getCursor());
        } else {
            setupListAdapter();
        }
        setTitle(getString(R.string.app_name) + " | " + getResources().getQuantityString(R.plurals.numberOfArticles, readList.getCount(), readList.getCount()));
    }

	private void setupListAdapter() {
        MyCursorAdapter adapter = getCursorAdapter();
        readList.setAdapter(adapter);

		readList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(getBaseContext(), ReadArticle.class);
                // As we use a CursorAdapter the id's are the same as in our SQLite Database.
                i.putExtra("id", id);
                i.putExtra("NIGHTMODE", nightmode);
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

    private MyCursorAdapter getCursorAdapter() {
        int layout = R.layout.article_list;
        String[] columns = new String[]{ARTICLE_TITLE, ARTICLE_URL};
        int[] toIds = new int[]{R.id.listitem_titre, R.id.listitem_textview_url};
        return new MyCursorAdapter(this, layout, getCursor(), columns, toIds, 0);
    }

    /**
     * Should not be needed with android.support.v4.widget.SimpleCursorAdapter;
     * @TargetApi(8)
    private CursorAdapter getCursorAdapterPreHoneycomb(int layout, String[] from, int[] to) {
        return new SimpleCursorAdapter(this, layout, getCursor(), from, to);
    }
     */


    private class MyCursorAdapter extends SimpleCursorAdapter {

        public MyCursorAdapter(android.content.Context context, int layout, Cursor c,
                               String[] a, int[] b, int flags) {
            super(context, layout, c, a, b, flags);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            //check for odd or even to set alternate colors to the row background
            if (nightmode) {
                view.setBackgroundColor(Color.rgb(0, 0, 0));
            }

            return view;
        }


    }
}
