package fr.gaulupeau.apps.Poche.service;

public class ActionResult {

    public enum ErrorType {
        TEMPORARY, NO_NETWORK,
        INCORRECT_CONFIGURATION, INCORRECT_CREDENTIALS,
        NOT_FOUND, NOT_FOUND_LOCALLY, NEGATIVE_RESPONSE, SERVER_ERROR, UNKNOWN
    }

    private boolean success = true;
    private ErrorType errorType;
    private String message;
    private Exception exception;

    public ActionResult() {}

    public ActionResult(ErrorType errorType) {
        this(errorType, null, null);
    }

    public ActionResult(ErrorType errorType, String message) {
        this(errorType, message, null);
    }

    public ActionResult(ErrorType errorType, Exception exception) {
        this(errorType, exception.toString(), exception);
    }

    public ActionResult(ErrorType errorType, String message, Exception exception) {
        setErrorType(errorType);
        this.message = message;
        this.exception = exception;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
        if(errorType != null) success = false;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public void updateWith(ActionResult r) {
        if(r == null || r.isSuccess()) return;

        success = false;
        errorType = r.getErrorType();
        message = r.getMessage();
        exception = r.getException();
    }

}
