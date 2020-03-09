package fr.gaulupeau.apps.Poche.service;

import android.content.Context;

public class DownloadArticleAsFileTask extends ActionRequestTask {

    public DownloadArticleAsFileTask(ActionRequest actionRequest) {
        super(actionRequest);
    }

    @Override
    protected ActionResult run(Context context, ActionRequest actionRequest) {
        return new ArticleAsFileDownloader(context).download(actionRequest);
    }

    // Parcelable implementation

    @SuppressWarnings("unused") // needed for CREATOR
    protected DownloadArticleAsFileTask() {}

    public static final TaskCreator<DownloadArticleAsFileTask> CREATOR
            = new TaskCreator<>(DownloadArticleAsFileTask.class);

}
