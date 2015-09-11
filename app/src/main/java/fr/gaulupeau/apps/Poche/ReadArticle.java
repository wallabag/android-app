package fr.gaulupeau.apps.Poche;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ScrollView;

import java.net.URL;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_AUTHOR;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_CONTENT;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_ID;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.MY_ID;

public class ReadArticle extends BaseActionBarActivity {
	WebView webViewContent;
	SQLiteDatabase database;
	String id = "";
	ScrollView view;
	boolean nightmode;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle data = getIntent().getExtras();
		if (data != null) {
			id = String.valueOf(data.getLong("id"));
			nightmode = data.getBoolean("NIGHTMODE", false);
		}
		setNightViewTheme();

		setContentView(R.layout.article);
		view = (ScrollView) findViewById(R.id.scroll);
		if (nightmode) {
			view.setBackgroundColor(Color.BLACK); //darkens the scrollview to avoid white flashing
		}
		ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(getApplicationContext());
		database = helper.getWritableDatabase();
		String[] getStrColumns = new String[]{ARTICLE_URL, MY_ID, ARTICLE_TITLE, ARTICLE_CONTENT, ARCHIVE, ARTICLE_AUTHOR};

		Cursor ac = database.query(ARTICLE_TABLE, getStrColumns, MY_ID + "=" + id, null, null, null, null);
		ac.moveToFirst();

		String titleText = ac.getString(2);
		String originalUrlText = ac.getString(0);
		String originalUrlDesc = originalUrlText;
		String htmlContent = ac.getString(3);

        setTitle(titleText);

		try {
			URL originalUrl = new URL(originalUrlText);
			originalUrlDesc = originalUrl.getHost();
		} catch (Exception e) {
			//
		}
		String usedCSS = "main";
		//load nightmode css file
		if (nightmode) {
			usedCSS = "nightview";
		}
		String htmlHeader = "<html>\n" +
				"\t<head>\n" +
				"\t\t<meta name=\"viewport\" content=\"initial-scale=1.0, maximum-scale=1.0, user-scalable=no\" />\n" +
				"\t\t<meta charset=\"utf-8\">\n" +
				"\t\t<link rel=\"stylesheet\" href=\"" + usedCSS + ".css\" media=\"all\" id=\"main-theme\">\n" +
				"\t\t<link rel=\"stylesheet\" href=\"ratatouille.css\" media=\"all\" id=\"extra-theme\">\n" +
				"\t</head>\n" +
				"\t\t<div id=\"main\">\n" +
				"\t\t\t<body>\n" +
				"\t\t\t\t<div id=\"content\" class=\"w600p center\">\n" +
				"\t\t\t\t\t<div id=\"article\">\n" +
				"\t\t\t\t\t\t<header class=\"mbm\">\n" +
				"\t\t\t\t\t\t\t<h1>" + titleText + "</h1>\n" +
				"\t\t\t\t\t\t\t<p>Open Original: <a href=\"" + originalUrlText + "\">" + originalUrlDesc + "</a></p>\n" +
				"\t\t\t\t\t\t</header>\n" +
				"\t\t\t\t\t\t<article>";
		String htmlFooter = "</article>\n" +
				"\t\t\t\t\t</div>\n" +
				"\t\t\t\t</div>\n" +
				"\t\t\t</body>\n" +
				"\t\t</div>\n" +
				"</html>";


		webViewContent = (WebView) findViewById(R.id.webViewContent);
		webViewContent.loadDataWithBaseURL("file:///android_asset/", htmlHeader + htmlContent + htmlFooter, "text/html", "utf-8", null);

		Button btnMarkRead = (Button) findViewById(R.id.btnMarkRead);
		btnMarkRead.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				markAsReadAndClose();
			}
		});
	}

    private void markAsReadAndClose() {
        ContentValues values = new ContentValues();
        values.put(ARCHIVE, 1);
        database.update(ARTICLE_TABLE, values, MY_ID + "=" + id, null);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_article, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuArticleMarkAsRead:
                markAsReadAndClose();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
	protected void onStop() {
		// TODO Auto-generated method stub

		ContentValues values = new ContentValues();
		values.put("read_at", view.getScrollY());
		database.update(ARTICLE_TABLE, values, ARTICLE_ID + "=" + id, null);
		System.out.println(view.getScrollY());
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		database.close();
	}

	private void setNightViewTheme() {
		if (nightmode) {
			this.setTheme(R.style.app_theme_dark);
		}
	}
}
