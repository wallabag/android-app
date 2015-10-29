package fr.gaulupeau.apps.Poche.data;

/**
 * Created by kevinmeyer on 13/12/14.
 */

public interface FeedUpdaterInterface {
    void feedUpdaterFinishedWithError(String errorMessage);
    void feedUpdatedFinishedSuccessfully();
}
