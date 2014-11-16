package fr.gaulupeau.apps.Poche;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static fr.gaulupeau.apps.Poche.Helpers.PREFS_NAME;
import static fr.gaulupeau.apps.Poche.Helpers.zeroUpdate;


public class ArticlesSQLiteOpenHelper extends SQLiteOpenHelper {


	public static final int VERSION = 1;
	public static final String DB_NAME = "article_db.sqlite";
	public static String MY_ID = "my_id";
	public static String ARTICLE_TABLE = "article";
	public static String ARTICLE_DATE = "update_date";
	public static String ARTICLE_ID = "article_id";
	public static String ARTICLE_AUTHOR = "author";
	public static String ARTICLE_CONTENT = "content";
	public static String ARTICLE_TITLE = "title";
	public static String ARTICLE_URL = "url";
	public static String ARCHIVE = "archive";
	public static String ARTICLE_SYNC = "sync";
	public static String ARTICLE_READAT = "read_at";
	Context c;

	public ArticlesSQLiteOpenHelper(Context context) {
		super(context, DB_NAME, null, VERSION);
		c = context;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		createTables(db);
	}


	@Override
	public void onOpen(SQLiteDatabase db) {
		// TODO Auto-generated method stub
		super.onOpen(db);
	}


	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.delete(ARTICLE_TABLE, null, null);
		SharedPreferences preferences = c.getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("previous_update", zeroUpdate);
		editor.commit();
	}

	protected void createTables(SQLiteDatabase db) {
		db.execSQL(
				"create table " + ARTICLE_TABLE + " (" +
						MY_ID + " integer primary key autoincrement not null, " +
						ARTICLE_AUTHOR + " text, " +
						ARTICLE_DATE + " datetime, " +
						ARTICLE_CONTENT + " text, " +
						ARTICLE_TITLE + " text, " +
						ARTICLE_URL + " text, " +
						ARTICLE_ID + " integer, " +
						ARCHIVE + " integer," +
						ARTICLE_SYNC + " integer," +
						ARTICLE_READAT + " integer," +
						"UNIQUE (" + ARTICLE_URL + ")" +
						");"
		);
	}

	public void truncateTables(SQLiteDatabase db) {
		db.execSQL("DELETE FROM " + ARTICLE_TABLE + ";");
	}

}
