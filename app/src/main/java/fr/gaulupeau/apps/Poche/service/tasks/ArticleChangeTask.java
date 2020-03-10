package fr.gaulupeau.apps.Poche.service.tasks;

import android.content.Context;
import android.os.Parcel;

import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.service.workers.OperationsWorker;

import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readEnum;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeEnum;

public class ArticleChangeTask extends GenericFieldsTask {

    protected QueueItem.ArticleChangeType articleChangeType;

    public static ArticleChangeTask newArchiveTask(int articleId, boolean archive) {
        ArticleChangeTask task = new ArticleChangeTask(articleId, QueueItem.ArticleChangeType.ARCHIVE);
        task.genericBooleanField1 = archive;
        return task;
    }

    public static ArticleChangeTask newFavoriteTask(int articleId, boolean favorite) {
        ArticleChangeTask task = new ArticleChangeTask(articleId, QueueItem.ArticleChangeType.FAVORITE);
        task.genericBooleanField1 = favorite;
        return task;
    }

    public static ArticleChangeTask newChangeTitleTask(int articleId, String title) {
        ArticleChangeTask task = new ArticleChangeTask(articleId, QueueItem.ArticleChangeType.TITLE);
        task.genericStringField1 = title;
        return task;
    }

    protected ArticleChangeTask(int articleId, QueueItem.ArticleChangeType articleChangeType) {
        this.genericIntField1 = articleId;
        this.articleChangeType = articleChangeType;
    }

    @Override
    public void run(Context context) {
        OperationsWorker operationsWorker = new OperationsWorker(context);

        switch (articleChangeType) {
            case ARCHIVE:
                operationsWorker.archiveArticle(genericIntField1, genericBooleanField1);
                return;

            case FAVORITE:
                operationsWorker.favoriteArticle(genericIntField1, genericBooleanField1);
                return;

            case TITLE:
                operationsWorker.changeArticleTitle(genericIntField1, genericStringField1);
                return;

            default:
                throw new RuntimeException("Type not implemented: " + articleChangeType);
        }
    }

    // Parcelable implementation

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);

        writeEnum(articleChangeType, dest);
    }

    @Override
    protected void readFromParcel(Parcel in) {
        super.readFromParcel(in);

        articleChangeType = readEnum(QueueItem.ArticleChangeType.class, in);
    }

    @SuppressWarnings("unused") // needed for CREATOR
    protected ArticleChangeTask() {}

    public static final TaskCreator<ArticleChangeTask> CREATOR
            = new TaskCreator<>(ArticleChangeTask.class);

}
