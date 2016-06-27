package fr.gaulupeau.apps.Poche.network.exceptions;

public class RequestException extends Exception {

    public RequestException() {}

    public RequestException(String detailMessage) {
        super(detailMessage);
    }

    public RequestException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public RequestException(Throwable throwable) {
        super(throwable);
    }

}
