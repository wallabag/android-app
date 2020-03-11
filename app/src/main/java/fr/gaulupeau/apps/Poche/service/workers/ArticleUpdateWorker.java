package fr.gaulupeau.apps.Poche.service.workers;

import android.content.Context;
import android.util.Log;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.data.Settings;
import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesProgressEvent;
import fr.gaulupeau.apps.Poche.events.UpdateArticlesStartedEvent;
import fr.gaulupeau.apps.Poche.network.Updater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import fr.gaulupeau.apps.Poche.service.ActionRequest;
import fr.gaulupeau.apps.Poche.service.ActionResult;
import wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class ArticleUpdateWorker extends BaseNetworkWorker {

    private static final String TAG = ArticleUpdateWorker.class.getSimpleName();

    public ArticleUpdateWorker(Context context) {
        super(context);
    }

    public ActionResult update(ActionRequest actionRequest) {
        ActionResult result = null;

        UpdateArticlesStartedEvent startEvent = new UpdateArticlesStartedEvent(actionRequest);
        postStickyEvent(startEvent);

        try {
            result = updateArticles(actionRequest);
        } finally {
            removeStickyEvent(startEvent);

            if (result == null) result = new ActionResult(ActionResult.ErrorType.UNKNOWN);
            postEvent(new UpdateArticlesFinishedEvent(actionRequest, result));
        }

        return result;
    }

    private ActionResult updateArticles(final ActionRequest actionRequest) {
        Updater.UpdateType updateType = actionRequest.getUpdateType();
        Log.d(TAG, String.format("updateArticles(%s) started", updateType));

        ActionResult result = new ActionResult();
        ArticlesChangedEvent event = null;

        if (WallabagConnection.isNetworkAvailable()) {
            final Settings settings = getSettings();

            try {
                Updater.UpdateListener updateListener = new Updater.UpdateListener() {
                    @Override
                    public void onProgress(int current, int total) {
                        postEvent(new UpdateArticlesProgressEvent(
                                actionRequest, current, total));
                    }

                    @Override
                    public void onSuccess(long latestUpdatedItemTimestamp) {
                        Log.i(TAG, "updateArticles() update successful, saving timestamps");

                        settings.setLatestUpdatedItemTimestamp(latestUpdatedItemTimestamp);
                        settings.setLatestUpdateRunTimestamp(System.currentTimeMillis());
                        settings.setFirstSyncDone(true);
                    }
                };

                event = getUpdater().update(updateType,
                        settings.getLatestUpdatedItemTimestamp(), updateListener);
            } catch (UnsuccessfulResponseException | IOException e) {
                ActionResult r = processException(e, "updateArticles()");
                result.updateWith(r);
            } catch (Exception e) {
                Log.e(TAG, "updateArticles() exception", e);

                result.setErrorType(ActionResult.ErrorType.UNKNOWN);
                result.setMessage(e.toString());
                result.setException(e);
            }
        } else {
            result.setErrorType(ActionResult.ErrorType.NO_NETWORK);
        }

        if (event != null && event.isAnythingChanged()) {
            postEvent(event);
        }

        Log.d(TAG, "updateArticles() finished");
        return result;
    }

}
