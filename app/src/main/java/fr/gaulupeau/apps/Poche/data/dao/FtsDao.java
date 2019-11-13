package fr.gaulupeau.apps.Poche.data.dao;

import android.os.Build;

import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import java.util.Collection;
import java.util.Collections;

import fr.gaulupeau.apps.Poche.data.dao.entities.Article;

public class FtsDao {

    public static final String TABLE_NAME = "article_fts";

    public static final String COLUMN_ID = "docid";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_CONTENT = "content";

    public static String getQueryString() {
        return "SELECT " + COLUMN_ID + " FROM " + TABLE_NAME + " WHERE " + TABLE_NAME + " MATCH ";
    }

    public static void createTable(Database db, boolean ifNotExists) {
        String options = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            options += ", tokenize=unicode61";
        }

        String constraint = ifNotExists ? "IF NOT EXISTS ": "";
        db.execSQL("CREATE VIRTUAL TABLE " + constraint + TABLE_NAME + " USING fts4(" +
                COLUMN_TITLE + ", " +
                COLUMN_CONTENT +
                options + ");");
    }

    public static void dropTable(Database db, boolean ifExists) {
        db.execSQL("DROP TABLE " + (ifExists ? "IF EXISTS " : "") + TABLE_NAME + ";");
    }

    public static void addArticles(Database db, Collection<Article> articles) {
        DatabaseStatement statement = getInsertStatement(db);
        try {
            for (Article article : articles) {
                int ix = 1;
                statement.bindLong(ix++, article.getId());
                statement.bindString(ix++, article.getTitle());
                statement.bindString(ix++, article.getContent());
                statement.executeInsert();
            }
        } finally {
            statement.close();
        }
    }

    public static void updateArticle(Database db, Article article) {
        updateArticles(db, Collections.singleton(article));
    }

    public static void updateArticles(Database db, Collection<Article> articles) {
        DatabaseStatement statement = getUpdateStatement(db);
        DatabaseStatement titleStatement = getUpdateTitleStatement(db);
        try {
            for (Article article : articles) {
                int ix = 1;

                if (article.getContent() != null) {
                    statement.bindString(ix++, article.getTitle());
                    statement.bindString(ix++, article.getContent());
                    statement.bindLong(ix++, article.getId());
                    statement.execute();
                } else {
                    titleStatement.bindString(ix++, article.getTitle());
                    titleStatement.bindLong(ix++, article.getId());
                    titleStatement.execute();
                }
            }
        } finally {
            statement.close();
            titleStatement.close();
        }
    }

    public static void deleteArticle(Database db, long id) {
        deleteArticles(db, Collections.singleton(id));
    }

    public static void deleteArticles(Database db, Collection<Long> ids) {
        DatabaseStatement statement = getDeleteStatement(db);
        try {
            // TODO: optimize: pass array
            for (Long id : ids) {
                statement.bindLong(1, id);
                statement.execute();
            }
        } finally {
            statement.close();
        }
    }

    public static void deleteAllArticles(Database db) {
        dropTable(db, true);
        createTable(db, true);
    }

    private static DatabaseStatement getInsertStatement(Database db) {
        return db.compileStatement("INSERT INTO " + TABLE_NAME +
                "(" + COLUMN_ID + ", " + COLUMN_TITLE + ", " + COLUMN_CONTENT + ")" +
                " VALUES(?, ?, ?)");
    }

    private static DatabaseStatement getUpdateStatement(Database db) {
        return db.compileStatement("UPDATE " + TABLE_NAME + " SET " +
                COLUMN_TITLE + " = ?, " + COLUMN_CONTENT + " = ?" +
                " WHERE " + COLUMN_ID + " = ?");
    }

    private static DatabaseStatement getUpdateTitleStatement(Database db) {
        return db.compileStatement("UPDATE " + TABLE_NAME + " SET " +
                COLUMN_TITLE + " = ?" +
                " WHERE " + COLUMN_ID + " = ?");
    }

    private static DatabaseStatement getDeleteStatement(Database db) {
        return db.compileStatement("DELETE FROM " + TABLE_NAME + " WHERE " + COLUMN_ID + " = ?");
    }

}
