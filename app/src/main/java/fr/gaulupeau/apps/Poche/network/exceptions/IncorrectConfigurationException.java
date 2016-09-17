package fr.gaulupeau.apps.Poche.network.exceptions;

public class IncorrectConfigurationException extends RequestException {

    public IncorrectConfigurationException() {}

    public IncorrectConfigurationException(String detailMessage) {
        super(detailMessage);
    }

    public IncorrectConfigurationException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public IncorrectConfigurationException(Throwable throwable) {
        super(throwable);
    }

}
