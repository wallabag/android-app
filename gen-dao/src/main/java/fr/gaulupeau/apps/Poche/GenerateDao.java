package fr.gaulupeau.apps.Poche;

import de.greenrobot.daogenerator.DaoGenerator;
import de.greenrobot.daogenerator.Entity;
import de.greenrobot.daogenerator.Schema;

public class GenerateDao {
    public static void main(String[] args) throws Exception {
        Schema schema = new Schema(4, "fr.gaulupeau.apps.Poche.entity");

        Entity article = schema.addEntity("Article");
        article.addIdProperty();
        article.addIntProperty("articleId").columnName("article_id").unique();
        article.addStringProperty("content").columnName("content");
        article.addStringProperty("author").columnName("author");
        article.addStringProperty("title").columnName("title");
        article.addStringProperty("url").columnName("url");
        article.addBooleanProperty("favorite").columnName("favorite");
        article.addBooleanProperty("archive").columnName("archive");
        article.addBooleanProperty("sync").columnName("sync"); // TODO: remove
        article.addDateProperty("updateDate").columnName("update_date"); // TODO: check: what is this prop for?
        article.addDoubleProperty("articleProgress").columnName("article_progress");

        // TODO: remove
        Entity offlineURL = schema.addEntity("OfflineURL");
        offlineURL.addIdProperty();
        offlineURL.addStringProperty("url").columnName("url").unique();

        Entity queueItem = schema.addEntity("QueueItem");
        queueItem.addIdProperty();
        queueItem.addLongProperty("queueNumber").columnName("queue_number"); // not sure it is actually needed
        queueItem.addIntProperty("action").notNull();
        queueItem.addIntProperty("articleId").columnName("article_id");
        queueItem.addStringProperty("extra");

        new DaoGenerator().generateAll(schema, "./app/src-gen");
    }
}
