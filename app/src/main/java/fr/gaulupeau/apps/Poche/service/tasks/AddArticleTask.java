package fr.gaulupeau.apps.Poche.service.tasks;

import android.content.Context;

import fr.gaulupeau.apps.Poche.service.workers.OperationsWorker;

public class AddArticleTask extends GenericFieldsTask {

    public AddArticleTask(String url, String originUrl) {
        this.genericStringField1 = url;
        this.genericStringField2 = originUrl;
    }

    @Override
    public void run(Context context) {
        new OperationsWorker(context).addArticle(genericStringField1, genericStringField2);
    }

    // Parcelable implementation

    @SuppressWarnings("unused") // needed for CREATOR
    protected AddArticleTask() {}

    public static final TaskCreator<AddArticleTask> CREATOR
            = new TaskCreator<>(AddArticleTask.class);

}
