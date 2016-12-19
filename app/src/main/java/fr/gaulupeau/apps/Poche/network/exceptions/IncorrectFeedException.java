package fr.gaulupeau.apps.Poche.network.exceptions;

public class IncorrectFeedException extends Exception {

    public IncorrectFeedException() {}

    public IncorrectFeedException(String message) {
        super(message);
    }

    public IncorrectFeedException(String message, Throwable cause) {
        super(message, cause);
    }

    public IncorrectFeedException(Throwable cause) {
        super(cause);
    }

}
