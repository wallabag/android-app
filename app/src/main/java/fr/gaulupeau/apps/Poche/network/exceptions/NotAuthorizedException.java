package fr.gaulupeau.apps.Poche.network.exceptions;

public class NotAuthorizedException extends RequestException {

    public NotAuthorizedException() {}

    public NotAuthorizedException(String detailMessage) {
        super(detailMessage);
    }

    public NotAuthorizedException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public NotAuthorizedException(Throwable throwable) {
        super(throwable);
    }

}
