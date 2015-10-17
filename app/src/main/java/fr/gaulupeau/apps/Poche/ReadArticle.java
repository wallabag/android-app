package fr.gaulupeau.apps.Poche;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ScrollView;

import java.net.URL;

import fr.gaulupeau.apps.InThePoche.R;

import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_AUTHOR;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_CONTENT;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_READAT;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.MY_ID;

public class ReadArticle extends BaseActionBarActivity {
	WebView webViewContent;
	SQLiteDatabase database;
	String id = "";
	ScrollView view;

	String titleText;
	String originalUrlText;
	String originalUrlDesc;
	String htmlContent;
	int positionToRestore = -1;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.article);

		view = (ScrollView) findViewById(R.id.scroll);
		ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(getApplicationContext());
		database = helper.getWritableDatabase();
		String[] getStrColumns = new String[]{ARTICLE_URL, MY_ID, ARTICLE_TITLE, ARTICLE_CONTENT, ARCHIVE, ARTICLE_AUTHOR, ARTICLE_READAT};
		Bundle data = getIntent().getExtras();
		if (data != null) {
			id = String.valueOf(data.getLong("id"));
		}
		Cursor ac = database.query(ARTICLE_TABLE, getStrColumns, MY_ID + "=" + id, null, null, null, null);
		ac.moveToFirst();

		titleText = ac.getString(2);
		originalUrlText = ac.getString(0);
		originalUrlDesc = originalUrlText;
		htmlContent = ac.getString(3);
		positionToRestore = ac.isNull(6) ? 0 : ac.getInt(6);

		ac.close();

        setTitle(titleText);

		try {
			URL originalUrl = new URL(originalUrlText);
			originalUrlDesc = originalUrl.getHost();
		} catch (Exception e) {
			//
		}

		Boolean hicontrast = false;
		if (android.os.Build.MODEL.equals("NOOK")) {
			hicontrast = true;
		}

		String htmlHeader = "<html>\n" +
				"\t<head>\n" +
				"\t\t<meta name=\"viewport\" content=\"initial-scale=1.0, maximum-scale=1.0, user-scalable=no\" />\n" +
				"\t\t<meta charset=\"utf-8\">\n" +
				"\t\t<link rel=\"stylesheet\" href=\"main.css\" media=\"all\" id=\"main-theme\">\n" +
				"\t\t<link rel=\"stylesheet\" href=\"ratatouille.css\" media=\"all\" id=\"extra-theme\">\n" +
				"\t</head>\n" +
				"\t\t<div id=\"main\">\n" +
				"\t\t\t<body" + (hicontrast?" class=\"hicontrast\"":"") + ">\n" +
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

		if(positionToRestore > 0) {
			// dirty. Looks like there is no good solution
			webViewContent.setWebViewClient(new WebViewClient() {
				@Override
				public void onPageFinished(WebView view, String url) {
					view.postDelayed(new Runnable() {
						@Override
						public void run() {
							if(webViewContent.getHeight() == 0) {
								webViewContent.postDelayed(this, 10);
							} else {
								restoreReadingPosition();
							}
						}
					}, 10);

					super.onPageFinished(view, url);
				}
			});
		}

		Button btnMarkRead = (Button) findViewById(R.id.btnMarkRead);
		btnMarkRead.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				markAsReadAndClose();
			}
		});
	}

	private boolean shareArticle() {
		Intent send = new Intent(Intent.ACTION_SEND);
		send.setType("text/plain");
		send.putExtra(Intent.EXTRA_SUBJECT, titleText);
		send.putExtra(Intent.EXTRA_TEXT, originalUrlText + " via @wallabagapp");
		startActivity(Intent.createChooser(send, "Share article"));
		return true;
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
	    case R.id.menuShare:
		return shareArticle();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
	protected void onStop() {
		ContentValues values = new ContentValues();
		values.put(ARTICLE_READAT, getReadingPosition());
		database.update(ARTICLE_TABLE, values, MY_ID + "=" + id, null);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		database.close();
	}

	private int getReadingPosition() {
		String t = "ReadArticle.getPos";

		int yOffset = view.getScrollY();
		int viewHeight = view.getHeight();
		int totalHeight = view.getChildAt(0).getHeight();
		// id/btnMarkRead height; not necessary; insignificantly increases accuracy
//		int appendixHeight = ((LinearLayout)view.getChildAt(0)).getChildAt(1).getHeight();
		Log.d(t, "yOffset: " + yOffset + ", viewHeight: " + viewHeight + ", totalHeight: " + totalHeight);

//		totalHeight -= appendixHeight;
		totalHeight -= viewHeight;

		// since "integer" type is used in DB, multiplying by 10000 to keep hundredth of percent
		int percent = totalHeight >= 0 ? yOffset * 10000 / totalHeight : 0;
		Log.d(t, "percent: " + (float)percent / 100);

		return percent;
	}

	private void restoreReadingPosition() {
		String t = "ReadArticle.restorePos";

		Log.d(t, "positionToRestore: " + (float)positionToRestore / 100);
		if(positionToRestore > 0) {
			int viewHeight = view.getHeight();
//			int appendixHeight = ((LinearLayout)view.getChildAt(0)).getChildAt(1).getHeight();
			int totalHeight = view.getChildAt(0).getHeight();
			Log.d(t, "viewHeight: " + viewHeight + ", totalHeight: " + totalHeight);

//			totalHeight -= appendixHeight;
			totalHeight -= viewHeight;

			int yOffset = totalHeight > 0 ? positionToRestore * totalHeight / 10000 : 0;
			Log.d(t, "yOffset: " + yOffset);

			view.scrollTo(view.getScrollX(), yOffset);
		}
	}

}
