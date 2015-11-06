package fr.gaulupeau.apps.Poche.network.tasks;

/**
 * Created by kevinmeyer on 13/12/14.
 */

public interface FeedUpdaterInterface {
    void feedUpdaterFinishedWithError(String errorMessage);
    void feedUpdatedFinishedSuccessfully();
}
