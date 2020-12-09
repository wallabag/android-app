package fr.gaulupeau.apps.Poche.data.dao;

import org.greenrobot.greendao.database.Database;

import fr.gaulupeau.apps.Poche.App;

public class FtsDao {

    public static final String TABLE_NAME = "article_fts";

    public static final String COLUMN_ID = "docid";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_CONTENT = "content";

    public static final String VIEW_FOR_FTS_NAME = "article_content_for_fts";

    private static final String[] TRIGGER_NAMES = new String[]{
            "article_added_insert_fts_tr",
            "article_added_update_fts_tr",
            "article_content_added_insert_fts_tr",
            "article_content_added_update_fts_tr",
            "article_before_updated_tr",
            "article_after_updated_tr",
            "article_content_before_updated_tr",
            "article_content_after_updated_tr",
            "article_before_delete_tr",
            "article_after_delete_tr",
            "article_content_before_delete_tr",
            "article_content_after_delete_tr"
    };

    public static String getQueryString() {
        return "select " + COLUMN_ID + " from " + TABLE_NAME + " where " + TABLE_NAME + " match ";
    }

    public static void createAll(Database db, boolean ifNotExists) {
        createViewForFts(db, ifNotExists);
        createTable(db, ifNotExists);
        createTriggers(db, ifNotExists);
    }

    public static void dropAll(Database db, boolean ifExists) {
        dropTriggers(db, ifExists);
        dropTable(db, ifExists);
        dropViewForFts(db, ifExists);
    }

    public static void deleteAllArticles(Database db) {
        dropTable(db, true);
        createTable(db, true);
    }

    private static void createTable(Database db, boolean ifNotExists) {
        String options = ", content=\"" + VIEW_FOR_FTS_NAME + "\""
                + ", tokenize="
                + (App.getSettings().isFtsIcuTokenizerEnabled() ? "icu" : "unicode61");

        db.execSQL("create virtual table " + getIfNotExistsConstraint(ifNotExists) +
                TABLE_NAME + " using fts4(" +
                COLUMN_TITLE + ", " +
                COLUMN_CONTENT +
                options + ");");
    }

    private static void dropTable(Database db, boolean ifExists) {
        db.execSQL("drop table " + getIfExistsConstraint(ifExists) + TABLE_NAME + ";");
    }

    private static void createViewForFts(Database db, boolean ifNotExists) {
        final String id = ArticleDao.Properties.Id.columnName;

        String viewQuery = "select a." + id + " rowid" +
                ", a." + ArticleDao.Properties.Title.columnName +
                ", c." + ArticleContentDao.Properties.Content.columnName +
                " from " + ArticleDao.TABLENAME + " a join " + ArticleContentDao.TABLENAME + " c" +
                " using (" + id + ")";

        String createViewQuery = "create view " + getIfNotExistsConstraint(ifNotExists) +
                VIEW_FOR_FTS_NAME + " as " + viewQuery;

        db.execSQL(createViewQuery);
    }

    private static void dropViewForFts(Database db, boolean ifExists) {
        db.execSQL("drop view " + getIfExistsConstraint(ifExists) + VIEW_FOR_FTS_NAME);
    }

    private static void createTriggers(Database db, boolean ifNotExists) {
        final String sqlId = "rowid";
        final String daoId = ArticleDao.Properties.Id.columnName;
        final String fts = TABLE_NAME;
        final String ftsId = COLUMN_ID;
        final String ftsTitle = COLUMN_TITLE;
        final String ftsContent = COLUMN_CONTENT;
        final String article =  ArticleDao.TABLENAME;
        final String articleTitle =  ArticleDao.Properties.Title.columnName;
        final String articleContent = ArticleContentDao.TABLENAME;
        final String articleContentContent = ArticleContentDao.Properties.Content.columnName;

        String[] triggers = {
                "after insert on " + article +
                        " when not exists (select " + ftsId + " from " + fts + " where " + ftsId + " = new." + sqlId + ")" +
                        " begin" +
                        "   insert into " + fts + "(" + ftsId + ", " + ftsTitle + ", " + ftsContent + ")" +
                        "     values(new." + sqlId + ", new." + articleTitle + "," +
                        "       coalesce((select " + articleContentContent + " from " + articleContent +
                        "         where " + daoId + " = new." + sqlId + "), null)" +
                        "     );" +
                        " end",
                "after insert on " + article +
                        " when exists (select " + ftsId + " from " + fts + " where " + ftsId + " = new." + sqlId + ")" +
                        " begin" +
                        "   update " + fts + " set " + ftsTitle + " = new." + articleTitle +
                        "     where " + ftsId + " = new." + sqlId + ";" +
                        " end",
                "after insert on " + articleContent +
                        " when not exists (select " + ftsId + " from " + fts + " where " + ftsId + " = new." + sqlId + ")" +
                        " begin" +
                        "   insert into " + fts + "(" + ftsId + ", " + ftsTitle + ", " + ftsContent + ")" +
                        "     values(new." + sqlId + ", coalesce((select " + articleTitle + " from " + article +
                        "       where " + daoId + " = new." + sqlId + "), null), new." + articleContentContent + ");" +
                        " end",
                "after insert on " + articleContent +
                        " when exists (select " + ftsId + " from " + fts + " where " + ftsId + " = new." + sqlId + ")" +
                        " begin" +
                        "   update " + fts + " set " + ftsTitle + " = coalesce((select " + articleTitle + " from " + article +
                        "       where " + daoId + " = new." + sqlId + "), null), " + ftsContent + " = new." + articleContentContent +
                        "     where " + ftsId + " = new." + sqlId + ";" +
                        " end",
                "before update of " + articleTitle + " on " + article +
                        " begin" +
                        "   update " + fts + " set " + ftsTitle + " = null where " + ftsId + " = old." + sqlId + ";" +
                        " end",
                "after update of " + articleTitle + " on " + article +
                        " begin" +
                        "   update " + fts + " set " + ftsTitle + " = new." + articleTitle +
                        "     where " + ftsId + " = new." + sqlId + ";" +
                        " end",
                "before update of " + articleContentContent + " on " + articleContent +
                        " begin" +
                        "   update " + fts + " set " + ftsContent + " = null" +
                        "     where " + ftsId + " = old." + sqlId + ";" +
                        " end",
                "after update of " + articleContentContent + " on " + articleContent +
                        " begin" +
                        "   update " + fts + " set " + ftsContent + " = new." + articleContentContent +
                        "     where " + ftsId + " = new." + sqlId + ";" +
                        " end",
                "before delete on " + article +
                        " when exists (select " + daoId + " from " + articleContent + " where " + daoId + " = old." + sqlId + ")" +
                        " begin" +
                        "   update " + fts + " set " + ftsTitle + " = null where " + ftsId + " = old." + sqlId + ";" +
                        " end",
                "before delete on " + article +
                        " when not exists (select " + daoId + " from " + articleContent + " where " + daoId + " = old." + sqlId + ")" +
                        " begin" +
                        "   delete from " + fts + " where " + ftsId + " = old." + sqlId + ";" +
                        " end",
                "before delete on " + articleContent +
                        " when exists (select " + daoId + " from " + article + " where " + daoId + " = old." + sqlId + ")" +
                        " begin" +
                        "   update " + fts + " set " + ftsContent + " = null where " + ftsId + " = old." + sqlId + ";" +
                        " end",
                "before delete on " + articleContent +
                        " when not exists (select " + daoId + " from " + article + " where " + daoId + " = old." + sqlId + ")" +
                        " begin" +
                        "   delete from " + fts + " where " + ftsId + " = old." + sqlId + ";" +
                        " end"
        };

        for (int i = 0; i < triggers.length; i++) {
            String triggerBody = triggers[i];
            db.execSQL("create trigger " + getIfNotExistsConstraint(ifNotExists) +
                    TRIGGER_NAMES[i] + " " + triggerBody);
        }
    }

    private static void dropTriggers(Database db, boolean ifExists) {
        for (String trigger : TRIGGER_NAMES) {
            db.execSQL("drop trigger " + getIfExistsConstraint(ifExists) + trigger);
        }
    }

    private static String getIfNotExistsConstraint(boolean ifNotExists) {
        return ifNotExists ? "if not exists ": "";
    }

    private static String getIfExistsConstraint(boolean ifExists) {
        return ifExists ? "if exists " : "";
    }

}
