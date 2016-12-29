package fr.gaulupeau.apps.Poche.service;

public class ActionResult {

    public enum ErrorType {
        TEMPORARY, NO_NETWORK,
        INCORRECT_CONFIGURATION, INCORRECT_CREDENTIALS,
        NOT_FOUND, NEGATIVE_RESPONSE, UNKNOWN
    }

    private boolean success = true;
    private ErrorType errorType;
    private String message;

    public ActionResult() {}

    public ActionResult(ErrorType errorType) {
        setErrorType(errorType);
    }

    public ActionResult(ErrorType errorType, String message) {
        this(errorType);
        this.message = message;
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

    void updateWith(ActionResult r) {
        if(r == null || r.isSuccess()) return;

        success = false;
        errorType = r.getErrorType();
        message = r.getMessage();
    }

}
