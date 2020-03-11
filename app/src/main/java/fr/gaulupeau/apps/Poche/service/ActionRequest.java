package fr.gaulupeau.apps.Poche.service;

import android.os.Parcel;
import android.os.Parcelable;

import wallabag.apiwrapper.WallabagService;

import fr.gaulupeau.apps.Poche.service.workers.ArticleUpdater;

import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readEnum;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readInteger;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.readLong;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeEnum;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeInteger;
import static fr.gaulupeau.apps.Poche.service.ParcelableUtils.writeLong;

public class ActionRequest implements Parcelable {

    public enum Action {
        SYNC_QUEUE, UPDATE_ARTICLES, SWEEP_DELETED_ARTICLES, FETCH_IMAGES, DOWNLOAD_AS_FILE
    }

    public enum RequestType {
        AUTO, MANUAL, MANUAL_BY_OPERATION
    }

    private Action action;
    private RequestType requestType = RequestType.MANUAL;
    private Long operationID;

    private Integer articleID;
    private ArticleUpdater.UpdateType updateType;
    private WallabagService.ResponseFormat downloadFormat;

    private ActionRequest nextRequest;

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

    public ArticleUpdater.UpdateType getUpdateType() {
        return updateType;
    }

    public void setUpdateType(ArticleUpdater.UpdateType updateType) {
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
        writeEnum(updateType, out);
        writeEnum(downloadFormat, out);
        out.writeParcelable(nextRequest, 0);
    }

    private ActionRequest(Parcel in) {
        action = Action.values()[in.readInt()];
        requestType = RequestType.values()[in.readInt()];
        operationID = readLong(in);

        articleID = readInteger(in);
        updateType = readEnum(ArticleUpdater.UpdateType.class, in);
        downloadFormat = readEnum(WallabagService.ResponseFormat.class, in);
        nextRequest = in.readParcelable(getClass().getClassLoader());
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
