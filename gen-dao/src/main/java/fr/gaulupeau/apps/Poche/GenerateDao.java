package fr.gaulupeau.apps.Poche;

import de.greenrobot.daogenerator.DaoGenerator;
import de.greenrobot.daogenerator.Entity;
import de.greenrobot.daogenerator.Schema;

public class GenerateDao {
    public static void main(String[] args) throws Exception {
        Schema schema = new Schema(5, "fr.gaulupeau.apps.Poche.entity");

        Entity article = schema.addEntity("Article");
        article.addIdProperty();
        article.addIntProperty("articleId").columnName("article_id").unique();
        article.addStringProperty("content").columnName("content");
        article.addStringProperty("author").columnName("author");
        article.addStringProperty("title").columnName("title");
        article.addStringProperty("url").columnName("url");
        article.addBooleanProperty("favorite").columnName("favorite");
        article.addBooleanProperty("archive").columnName("archive");
        article.addDateProperty("updateDate").columnName("update_date"); // not used
        article.addDoubleProperty("articleProgress").columnName("article_progress");

        Entity queueItem = schema.addEntity("QueueItem");
        queueItem.addIdProperty();
        queueItem.addLongProperty("queueNumber").columnName("queue_number"); // not sure it is actually needed
        queueItem.addIntProperty("action").notNull();
        queueItem.addIntProperty("articleId").columnName("article_id");
        queueItem.addStringProperty("extra");

        new DaoGenerator().generateAll(schema, "./app/src-gen");
    }
}
