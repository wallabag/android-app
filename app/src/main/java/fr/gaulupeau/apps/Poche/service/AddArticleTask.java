package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

import fr.gaulupeau.apps.Poche.data.OperationsHelper;

public class AddArticleTask extends GenericFieldsTask {

    public AddArticleTask(String url, String originUrl) {
        this.genericStringField1 = url;
        this.genericStringField2 = originUrl;
    }

    @Override
    public void run(Context context) {
        OperationsHelper.addArticleBG(genericStringField1, genericStringField2);
    }

    // Parcelable implementation

    @SuppressWarnings("unused") // needed for CREATOR
    protected AddArticleTask() {}

    public static final TaskCreator<AddArticleTask> CREATOR
            = new TaskCreator<>(AddArticleTask.class);

}
