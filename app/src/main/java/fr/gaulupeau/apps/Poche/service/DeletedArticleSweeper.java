package fr.gaulupeau.apps.Poche.service;

import android.content.Context;
import android.util.Log;

import java.io.IOException;

import fr.gaulupeau.apps.Poche.events.ArticlesChangedEvent;
import fr.gaulupeau.apps.Poche.events.SweepDeletedArticlesFinishedEvent;
import fr.gaulupeau.apps.Poche.events.SweepDeletedArticlesProgressEvent;
import fr.gaulupeau.apps.Poche.events.SweepDeletedArticlesStartedEvent;
import fr.gaulupeau.apps.Poche.network.Updater;
import fr.gaulupeau.apps.Poche.network.WallabagConnection;
import wallabag.apiwrapper.exceptions.UnsuccessfulResponseException;

import static fr.gaulupeau.apps.Poche.events.EventHelper.postEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.postStickyEvent;
import static fr.gaulupeau.apps.Poche.events.EventHelper.removeStickyEvent;

public class DeletedArticleSweeper extends BaseNetworkWorker {

    private static final String TAG = DeletedArticleSweeper.class.getSimpleName();

    public DeletedArticleSweeper(Context context) {
        super(context);
    }

    public ActionResult sweep(ActionRequest actionRequest) {
        ActionResult result = null;

        SweepDeletedArticlesStartedEvent startEvent
                = new SweepDeletedArticlesStartedEvent(actionRequest);
        postStickyEvent(startEvent);

        try {
            result = sweepDeletedArticles(actionRequest);
        } finally {
            removeStickyEvent(startEvent);

            if (result == null) result = new ActionResult(ActionResult.ErrorType.UNKNOWN);
            postEvent(new SweepDeletedArticlesFinishedEvent(actionRequest, result));
        }

        return result;
    }

    private ActionResult sweepDeletedArticles(final ActionRequest actionRequest) {
        Log.d(TAG, "sweepDeletedArticles() started");

        ActionResult result = new ActionResult();
        ArticlesChangedEvent event = null;

        if (WallabagConnection.isNetworkAvailable()) {
            try {
                Updater.ProgressListener progressListener = (current, total) ->
                        postEvent(new SweepDeletedArticlesProgressEvent(
                                actionRequest, current, total));

                event = getUpdater().sweepDeletedArticles(progressListener);
            } catch (UnsuccessfulResponseException | IOException e) {
                ActionResult r = processException(e, "sweepDeletedArticles()");
                result.updateWith(r);
            } catch (Exception e) {
                Log.e(TAG, "sweepDeletedArticles() exception", e);

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

        Log.d(TAG, "sweepDeletedArticles() finished");
        return result;
    }

}
