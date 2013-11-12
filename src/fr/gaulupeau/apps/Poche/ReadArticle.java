package fr.gaulupeau.apps.Poche;

import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARCHIVE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_CONTENT;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_ID;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TITLE;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_URL;
import static fr.gaulupeau.apps.Poche.ArticlesSQLiteOpenHelper.ARTICLE_TABLE;
import static fr.gaulupeau.apps.Poche.Helpers.getInputStreamFromUrl;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;
import fr.gaulupeau.apps.InThePoche.R;

public class ReadArticle extends Activity {
	TextView txtTitre;
	TextView txtContent;
    SQLiteDatabase database;
    String id = "";
    
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.article);
		ArticlesSQLiteOpenHelper helper = new ArticlesSQLiteOpenHelper(getApplicationContext());
		database = helper.getWritableDatabase();
		String[] getStrColumns = new String[] {ARTICLE_URL, ARTICLE_ID, ARTICLE_TITLE, ARTICLE_CONTENT, ARCHIVE};
		Bundle data = getIntent().getExtras();
		if(data != null) {
			id = data.getString("id");
		}
		Cursor ac = database.query(ARTICLE_TABLE, getStrColumns, ARTICLE_ID + "=" + id, null, null, null, null);
		ac.moveToFirst();
		txtTitre = (TextView)findViewById(R.id.txtTitre);
		txtTitre.setText(ac.getString(2));
		txtContent = (TextView)findViewById(R.id.txtContent);
		txtContent.setText(ac.getString(3));
//		String ret = getInputStreamFromUrl("http://poche.gaulupeau.fr/toto.php");
//		try {
//			JSONObject rootobj = new JSONObject(ret);
//			System.out.println(rootobj);
//			txtTitre = (TextView)findViewById(R.id.txtTitre);
//			txtContent = (TextView)findViewById(R.id.txtContent);
//			txtTitre.setText(Html.fromHtml(rootobj.getString("titre")));
//			txtContent.setText(Html.fromHtml(rootobj.getString("content")));
//		} catch (JSONException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

	}
	
}
