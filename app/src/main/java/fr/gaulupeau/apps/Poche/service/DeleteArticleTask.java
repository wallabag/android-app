package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

public class DeleteArticleTask extends GenericFieldsTask {

    public DeleteArticleTask(int articleId) {
        this.genericIntField1 = articleId;
    }

    @Override
    public void run(Context context) {
        new OperationsWorker(context).deleteArticle(genericIntField1);
    }

    // Parcelable implementation

    @SuppressWarnings("unused") // needed for CREATOR
    protected DeleteArticleTask() {}

    public static final TaskCreator<DeleteArticleTask> CREATOR
            = new TaskCreator<>(DeleteArticleTask.class);

}
