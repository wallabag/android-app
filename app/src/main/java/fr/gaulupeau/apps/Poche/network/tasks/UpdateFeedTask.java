package fr.gaulupeau.apps.Poche.network.tasks;

import android.os.AsyncTask;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class UpdateFeedTask extends AsyncTask<Void, Void, Void> { // TODO: remove

    private String baseURL;
    private String apiUserId;
    private String apiToken;
    private int wallabagVersion;
    private FeedUpdater.FeedType feedType;
    private FeedUpdater.UpdateType updateType;
    private CallbackInterface callback;

    private String errorMessage;

    public UpdateFeedTask(String baseURL, String apiUserId, String apiToken, int wallabagVersion,
                          CallbackInterface callback,
                          FeedUpdater.FeedType feedType, FeedUpdater.UpdateType updateType) {
        this.baseURL = baseURL;
        this.apiUserId = apiUserId;
        this.apiToken = apiToken;
        this.wallabagVersion = wallabagVersion;
        this.callback = callback;
        this.feedType = feedType;
        this.updateType = updateType;
    }

    @Override
    protected Void doInBackground(Void... params) {
        FeedUpdater feedUpdater = new FeedUpdater(baseURL, apiUserId, apiToken, wallabagVersion);

        try {
            feedUpdater.update(feedType, updateType);
        } catch(FeedUpdater.FeedUpdaterException e) {
            errorMessage = e.getMessage();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (callback == null)
            return;

        if (errorMessage == null) {
            callback.feedUpdateFinishedSuccessfully();
        } else {
            callback.feedUpdateFinishedWithError(errorMessage);
        }
    }

    public interface CallbackInterface {
        void feedUpdateFinishedWithError(String errorMessage);
        void feedUpdateFinishedSuccessfully();
    }

}
