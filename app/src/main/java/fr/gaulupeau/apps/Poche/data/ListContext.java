package fr.gaulupeau.apps.Poche.data;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import fr.gaulupeau.apps.InThePoche.R;
import fr.gaulupeau.apps.Poche.App;
import fr.gaulupeau.apps.Poche.data.dao.TagDao;
import fr.gaulupeau.apps.Poche.data.dao.entities.Tag;
import fr.gaulupeau.apps.Poche.ui.Sortable;

import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readBool;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readBoolean;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readEnum;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readList;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readString;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeBool;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeBoolean;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeEnum;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeList;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeString;

public class ListContext implements Parcelable {

    protected Boolean archived;
    protected Boolean favorite;

    protected String searchQuery;

    protected String tagLabel;
    protected boolean tagsCached;
    protected boolean untagged;
    protected List<Long> tagIds;

    protected Sortable.SortOrder sortOrder;

    public ListContext() {}

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public boolean setSearchQuery(String searchQuery) {
        if (TextUtils.equals(searchQuery, this.searchQuery)) return false;

        this.searchQuery = searchQuery;
        return true;
    }

    public String getTagLabel() {
        return tagLabel;
    }

    public boolean setTagLabel(String tagLabel) {
        if (TextUtils.equals(tagLabel, this.tagLabel)) return false;

        this.tagLabel = tagLabel;
        resetTagsCache();
        return true;
    }

    public boolean isUntagged(TagDao tagDao) {
        cacheTags(tagDao);
        return untagged;
    }

    public List<Long> getTagIds(TagDao tagDao) {
        cacheTags(tagDao);
        return tagIds;
    }

    public Sortable.SortOrder getSortOrder() {
        return sortOrder;
    }

    public boolean setSortOrder(Sortable.SortOrder sortOrder) {
        if (Objects.equals(sortOrder, this.sortOrder)) return false;

        this.sortOrder = sortOrder;
        return true;
    }

    public void resetCache() {
        resetTagsCache();
    }

    protected void resetTagsCache() {
        tagsCached = false;
        untagged = false;
        tagIds = null;
    }

    protected void cacheTags(TagDao tagDao) {
        if (tagsCached) return;

        if (App.getInstance().getString(R.string.untagged).equals(tagLabel)) {
            untagged = true;
        } else if (tagLabel != null) {
            List<Tag> tags = tagDao.queryBuilder()
                    .where(TagDao.Properties.Label.eq(tagLabel))
                    .orderDesc(TagDao.Properties.Label)
                    .list();

            tagIds = new ArrayList<>(tags.size());
            for (Tag t : tags) {
                tagIds.add(t.getId());
            }
        }

        tagsCached = true;
    }

    // Parcelable implementation

    protected ListContext(Parcel parcel) {
        this();
        readFromParcel(parcel);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeBoolean(archived, dest);
        writeBoolean(favorite, dest);

        writeString(searchQuery, dest);

        writeString(tagLabel, dest);
        writeBool(tagsCached, dest);
        writeBool(untagged, dest);
        writeList(dest, tagIds, Parcel::writeLong);

        writeEnum(sortOrder, dest);
    }

    protected void readFromParcel(Parcel in) {
        archived = readBoolean(in);
        favorite = readBoolean(in);

        searchQuery = readString(in);

        tagLabel = readString(in);
        tagsCached = readBool(in);
        untagged = readBool(in);
        tagIds = readList(in, Parcel::readLong);

        sortOrder = readEnum(Sortable.SortOrder.class, in);
    }

    public static final Parcelable.Creator<ListContext> CREATOR
            = new Parcelable.Creator<ListContext>() {
        @Override
        public ListContext createFromParcel(Parcel in) {
            return new ListContext(in);
        }

        @Override
        public ListContext[] newArray(int size) {
            return new ListContext[size];
        }
    };

}
