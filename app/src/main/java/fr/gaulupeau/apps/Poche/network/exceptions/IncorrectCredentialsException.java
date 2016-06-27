package fr.gaulupeau.apps.Poche.network.exceptions;

public class IncorrectCredentialsException extends IncorrectConfigurationException {

    public IncorrectCredentialsException() {}

    public IncorrectCredentialsException(String detailMessage) {
        super(detailMessage);
    }

    public IncorrectCredentialsException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public IncorrectCredentialsException(Throwable throwable) {
        super(throwable);
    }

}
