package fr.gaulupeau.apps.Poche.service;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import wallabag.apiwrapper.WallabagService;

import fr.gaulupeau.apps.Poche.data.dao.entities.QueueItem;
import fr.gaulupeau.apps.Poche.network.Updater;

public class ActionRequest implements Parcelable {

    public enum Action {
        ADD_LINK, ARTICLE_CHANGE, ARTICLE_TAGS_DELETE, ARTICLE_DELETE,
        SYNC_QUEUE, UPDATE_ARTICLES, SWEEP_DELETED_ARTICLES, FETCH_IMAGES, DOWNLOAD_AS_FILE
    }

    public enum RequestType {
        AUTO, MANUAL, MANUAL_BY_OPERATION
    }

    public static final String ACTION_REQUEST = "wallabag.extra.action_request";

    private Action action;
    private RequestType requestType = RequestType.MANUAL;
    private Long operationID;

    private Integer articleID;
    private QueueItem.ArticleChangeType articleChangeType;
    private String extra;
    private Updater.UpdateType updateType;
    private WallabagService.ResponseFormat downloadFormat;

    private ActionRequest nextRequest;

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

    public QueueItem.ArticleChangeType getArticleChangeType() {
        return articleChangeType;
    }

    public void setArticleChangeType(QueueItem.ArticleChangeType articleChangeType) {
        this.articleChangeType = articleChangeType;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public Updater.UpdateType getUpdateType() {
        return updateType;
    }

    public void setUpdateType(Updater.UpdateType updateType) {
        this.updateType = updateType;
    }

    public WallabagService.ResponseFormat getDownloadFormat() {
        return downloadFormat;
    }

    public void setDownloadFormat(WallabagService.ResponseFormat downloadFormat) {
        this.downloadFormat = downloadFormat;
    }

    public ActionRequest getNextRequest() {
        return nextRequest;
    }

    public void setNextRequest(ActionRequest nextRequest) {
        this.nextRequest = nextRequest;
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
        writeInteger(articleChangeType != null ? articleChangeType.ordinal() : null, out);
        writeString(extra, out);
        writeInteger(updateType != null ? updateType.ordinal() : null, out);
        writeInteger(downloadFormat != null ? downloadFormat.ordinal() : null, out);
        out.writeParcelable(nextRequest, 0);
    }

    private ActionRequest(Parcel in) {
        action = Action.values()[in.readInt()];
        requestType = RequestType.values()[in.readInt()];
        operationID = readLong(in);

        articleID = readInteger(in);
        Integer articleChangeTypeInteger = readInteger(in);
        if(articleChangeTypeInteger != null) {
            articleChangeType = QueueItem.ArticleChangeType.values()[articleChangeTypeInteger];
        }
        extra = readString(in);
        Integer feedUpdateUpdateTypeInteger = readInteger(in);
        if(feedUpdateUpdateTypeInteger != null) {
            updateType = Updater.UpdateType.values()[feedUpdateUpdateTypeInteger];
        }
        Integer downloadFormatInteger = readInteger(in);
        if(downloadFormatInteger != null) {
            downloadFormat = WallabagService.ResponseFormat.values()[downloadFormatInteger];
        }
        nextRequest = in.readParcelable(getClass().getClassLoader());
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
