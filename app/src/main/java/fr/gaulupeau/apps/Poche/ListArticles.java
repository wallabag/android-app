package fr.gaulupeau.apps.Poche;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_CONTENT;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_DATE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.MY_ID;

public class ListArticles extends BaseActionBarActivity {

	private ArrayList<Article> readArticlesInfo;
	private SQLiteDatabase database;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list);
		setupDB();
		setupList(false);
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
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuShowAll:
				setupList(true);
				return true;
			case R.id.menuWipeDb:
				ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
				helper.truncateTables(database);
				setupList(false);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	public void setupDB() {
		ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(this);
		database = helper.getWritableDatabase();
	}

	public void setupList(Boolean showAll) {
		ListView readList = (ListView) findViewById(R.id.liste_articles);
		readArticlesInfo = new ArrayList<Article>();
		String filter = null;
		if (!showAll) {
			filter = ARCHIVE + "=0";
		}
		ReadingListAdapter ad = getAdapterQuery(filter, readArticlesInfo);
		readList.setAdapter(ad);

		readList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent i = new Intent(getBaseContext(), ReadArticle.class);
				i.putExtra("id", readArticlesInfo.get(position).id);
				startActivity(i);
			}

		});
	}

	public ReadingListAdapter getAdapterQuery(String filter, ArrayList<Article> articleInfo) {
		//Log.e("getAdapterQuery", "running query");
		//String url, String domain, String id, String title, String content
		String[] getStrColumns = new String[]{ARTICLE_URL, MY_ID, ARTICLE_TITLE, ARTICLE_CONTENT, ARCHIVE};
		Cursor ac = database.query(
				ARTICLE_TABLE,
				getStrColumns,
				filter, null, null, null, ARTICLE_DATE + " DESC");
		ac.moveToFirst();
		if (!ac.isAfterLast()) {
			do {
				Article tempArticle = new Article(ac.getString(0), ac.getString(1), ac.getString(2), ac.getString(3), ac.getString(4));
				articleInfo.add(tempArticle);
			} while (ac.moveToNext());
		}
		ac.close();
		return new ReadingListAdapter(getBaseContext(), articleInfo);
	}

}
