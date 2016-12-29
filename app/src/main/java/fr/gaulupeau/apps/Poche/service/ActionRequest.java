package fr.gaulupeau.apps.Poche.service;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import fr.gaulupeau.apps.Poche.network.FeedUpdater;

public class ActionRequest implements Parcelable {

    public enum Action {
        AddLink, Archive, Unarchive, Favorite, Unfavorite, Delete, SyncQueue, UpdateFeed,
        DownloadAsFile, FetchImages
    }

    public enum RequestType {
        Auto, Manual, ManualByOperation
    }

    public enum DownloadFormat {
        EPUB, MOBI, PDF, CSV, JSON, TXT, XML;

        public String asString() {
            return name().toLowerCase();
        }

    }

    public static final String ACTION_REQUEST = "wallabag.extra.action_request";

    private Action action;
    private RequestType requestType = RequestType.Manual;
    private Long operationID;

    private Integer articleID;
    private String link;
    private Long queueLength;
    private FeedUpdater.FeedType feedUpdateFeedType;
    private FeedUpdater.UpdateType feedUpdateUpdateType;
    private DownloadFormat downloadFormat;

    public static ActionRequest fromIntent(Intent intent) {
        return intent.getParcelableExtra(ACTION_REQUEST);
    }

    public ActionRequest(Action action) {
        this.action = action;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public Long getOperationID() {
        return operationID;
    }

    public void setOperationID(Long operationID) {
        this.operationID = operationID;
    }

    public Integer getArticleID() {
        return articleID;
    }

    public void setArticleID(Integer articleID) {
        this.articleID = articleID;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Long getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(Long queueLength) {
        this.queueLength = queueLength;
    }

    public FeedUpdater.FeedType getFeedUpdateFeedType() {
        return feedUpdateFeedType;
    }

    public void setFeedUpdateFeedType(FeedUpdater.FeedType feedUpdateFeedType) {
        this.feedUpdateFeedType = feedUpdateFeedType;
    }

    public FeedUpdater.UpdateType getFeedUpdateUpdateType() {
        return feedUpdateUpdateType;
    }

    public void setFeedUpdateUpdateType(FeedUpdater.UpdateType feedUpdateUpdateType) {
        this.feedUpdateUpdateType = feedUpdateUpdateType;
    }

    public DownloadFormat getDownloadFormat() {
        return downloadFormat;
    }

    public void setDownloadFormat(DownloadFormat downloadFormat) {
        this.downloadFormat = downloadFormat;
    }

// Parcelable implementation

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(action.ordinal());
        out.writeInt(requestType.ordinal());
        writeLong(operationID, out);

        writeInteger(articleID, out);
        writeString(link, out);
        writeLong(queueLength, out);
        writeInteger(this.feedUpdateFeedType != null ? this.feedUpdateFeedType.ordinal() : null, out);
        writeInteger(this.feedUpdateUpdateType != null ? this.feedUpdateUpdateType.ordinal() : null, out);
        writeInteger(this.downloadFormat != null ? this.downloadFormat.ordinal() : null, out);
    }

    private ActionRequest(Parcel in) {
        action = Action.values()[in.readInt()];
        requestType = RequestType.values()[in.readInt()];
        operationID = readLong(in);

        articleID = readInteger(in);
        link = readString(in);
        queueLength = readLong(in);
        Integer feedUpdateFeedTypeInteger = readInteger(in);
        if(feedUpdateFeedTypeInteger != null) {
            feedUpdateFeedType = FeedUpdater.FeedType.values()[feedUpdateFeedTypeInteger];
        }
        Integer feedUpdateUpdateTypeInteger = readInteger(in);
        if(feedUpdateUpdateTypeInteger != null) {
            feedUpdateUpdateType = FeedUpdater.UpdateType.values()[feedUpdateUpdateTypeInteger];
        }
        Integer downloadFormatInteger = readInteger(in);
        if(downloadFormatInteger != null) {
            downloadFormat = DownloadFormat.values()[downloadFormatInteger];
        }
    }

    private void writeLong(Long value, Parcel out) {
        out.writeByte((byte)(value == null ? 0 : 1));

        if(value != null) out.writeLong(value);
    }

    private Long readLong(Parcel in) {
        if(in.readByte() == 0) return null;

        return in.readLong();
    }

    private void writeInteger(Integer value, Parcel out) {
        out.writeByte((byte)(value == null ? 0 : 1));

        if(value != null) out.writeInt(value);
    }

    private Integer readInteger(Parcel in) {
        if(in.readByte() == 0) return null;

        return in.readInt();
    }

    private void writeString(String value, Parcel out) {
        out.writeByte((byte)(value == null ? 0 : 1));

        if(value != null) out.writeString(value);
    }

    private String readString(Parcel in) {
        if(in.readByte() == 0) return null;

        return in.readString();
    }

    public static final Parcelable.Creator<ActionRequest> CREATOR
            = new Parcelable.Creator<ActionRequest>() {
        @Override
        public ActionRequest createFromParcel(Parcel in) {
            return new ActionRequest(in);
        }

        @Override
        public ActionRequest[] newArray(int size) {
            return new ActionRequest[size];
        }
    };

}
