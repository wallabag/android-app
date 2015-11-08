package com.snilius;

import de.greenrobot.daogenerator.DaoGenerator;
import de.greenrobot.daogenerator.Entity;
import de.greenrobot.daogenerator.Schema;

public class GenerateDao {
    public static void main(String[] args) throws Exception {
        Schema schema = new Schema(2, "fr.gaulupeau.apps.Poche.entity");

        Entity article = schema.addEntity("Article");
        article.addIdProperty();
        article.addIntProperty("articleId").columnName("article_id").unique();
        article.addStringProperty("content").columnName("content");
        article.addStringProperty("author").columnName("author");
        article.addStringProperty("title").columnName("title");
        article.addStringProperty("url").columnName("url");
        article.addBooleanProperty("favorite").columnName("favorite");
        article.addBooleanProperty("archive").columnName("archive");
        article.addBooleanProperty("sync").columnName("sync");
        article.addDateProperty("updateDate").columnName("update_date");
        article.addDoubleProperty("articleProgress").columnName("article_progress");

        Entity offlineURL = schema.addEntity("OfflineURL");
        offlineURL.addIdProperty();
        offlineURL.addStringProperty("url").columnName("url").unique();

        new DaoGenerator().generateAll(schema, "../app/src-gen");
    }
}
