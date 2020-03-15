package fr.gaulupeau.apps.Poche.events;

import java.util.Collections;
import java.util.EnumSet;

public class FeedsChangedEvent {

    public enum FeedType { MAIN, FAVORITE, ARCHIVE }

    public enum ChangeType {
        ARCHIVED, UNARCHIVED, FAVORITED, UNFAVORITED, TITLE_CHANGED, DOMAIN_CHANGED, URL_CHANGED,
        ORIGIN_URL_CHANGED, ESTIMATED_READING_TIME_CHANGED, LANGUAGE_CHANGED,
        PREVIEW_PICTURE_URL_CHANGED, AUTHORS_CHANGED, CREATED_DATE_CHANGED, UPDATED_DATE_CHANGED,
        PUBLISHED_AT_CHANGED, STARRED_AT_CHANGED, IS_PUBLIC_CHANGED, PUBLIC_UID_CHANGED,
        TAG_SET_CHANGED, TAGS_CHANGED_GLOBALLY, ANNOTATIONS_CHANGED, CONTENT_CHANGED,
        FETCHED_IMAGES_CHANGED, ADDED, DELETED, UNSPECIFIED
    }

    protected EnumSet<ChangeType> invalidateAllChanges = EnumSet.noneOf(ChangeType.class);

    protected EnumSet<ChangeType> mainFeedChanges = EnumSet.noneOf(ChangeType.class);
    protected EnumSet<ChangeType> favoriteFeedChanges = EnumSet.noneOf(ChangeType.class);
    protected EnumSet<ChangeType> archiveFeedChanges = EnumSet.noneOf(ChangeType.class);

    public FeedsChangedEvent() {}

    public static boolean containsAny(EnumSet<ChangeType> set1, EnumSet<ChangeType> set2) {
        return !Collections.disjoint(set1, set2);
    }

    public boolean contains(ChangeType change) {
        return invalidateAllChanges.contains(change)
                || invalidateAllChanges.contains(ChangeType.UNSPECIFIED);
    }

    public boolean containsAny(EnumSet<ChangeType> changes) {
        return containsAny(invalidateAllChanges, changes)
                || invalidateAllChanges.contains(ChangeType.UNSPECIFIED);
    }

    public boolean isInvalidateAll() {
        return !invalidateAllChanges.isEmpty();
    }

    public void invalidateAll() {
        invalidateAll((EnumSet<ChangeType>)null);
    }

    public void invalidateAll(ChangeType changeType) {
        invalidateAll(EnumSet.of(changeType));
    }

    public void invalidateAll(EnumSet<ChangeType> changes) {
        if(changes == null) changes = EnumSet.of(ChangeType.UNSPECIFIED);

        invalidateAllChanges.addAll(changes);
        addChanges(changes);
    }

    public EnumSet<ChangeType> getInvalidateAllChanges() {
        return invalidateAllChanges;
    }

    public boolean isMainFeedChanged() {
        return !mainFeedChanges.isEmpty();
    }

    public void setMainFeedChanged() {
        mainFeedChanges.add(ChangeType.UNSPECIFIED);
    }

    public EnumSet<ChangeType> getMainFeedChanges() {
        return mainFeedChanges;
    }

    public boolean isFavoriteFeedChanged() {
        return !favoriteFeedChanges.isEmpty();
    }

    public void setFavoriteFeedChanged() {
        favoriteFeedChanges.add(ChangeType.UNSPECIFIED);
    }

    public EnumSet<ChangeType> getFavoriteFeedChanges() {
        return favoriteFeedChanges;
    }

    public boolean isArchiveFeedChanged() {
        return !archiveFeedChanges.isEmpty();
    }

    public void setArchiveFeedChanged() {
        archiveFeedChanges.add(ChangeType.UNSPECIFIED);
    }

    public EnumSet<ChangeType> getArchiveFeedChanges() {
        return archiveFeedChanges;
    }

    public boolean isAnythingChanged() {
        return isInvalidateAll() || isMainFeedChanged()
                || isFavoriteFeedChanged() || isArchiveFeedChanged();
    }

    public void addChanges(EnumSet<ChangeType> changes) {
        if(changes == null) changes = EnumSet.of(ChangeType.UNSPECIFIED);

        mainFeedChanges.addAll(changes);
        favoriteFeedChanges.addAll(changes);
        archiveFeedChanges.addAll(changes);
    }

    public void addChangesByFeedType(FeedType feedType, EnumSet<ChangeType> changes) {
        if(changes == null) changes = EnumSet.of(ChangeType.UNSPECIFIED);

        switch(feedType) {
            case MAIN:
                mainFeedChanges.addAll(changes);
                break;

            case FAVORITE:
                favoriteFeedChanges.addAll(changes);
                break;

            case ARCHIVE:
                archiveFeedChanges.addAll(changes);
                break;
        }
    }

}
