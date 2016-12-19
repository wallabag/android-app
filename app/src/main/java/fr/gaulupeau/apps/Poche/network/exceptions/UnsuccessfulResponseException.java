package fr.gaulupeau.apps.Poche.network.exceptions;

public class UnsuccessfulResponseException extends RequestException {

    private int responseCode;
    private String responseMessage;
    private String responseBody;
    private String url;

    public UnsuccessfulResponseException(int responseCode,
                                         String responseMessage,
                                         String responseBody,
                                         String url) {
        super("HTTP "+ responseCode + ": " + responseMessage + ". Requested for URL: " + url);
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.responseBody = responseBody;
        this.url = url;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getUrl() {
        return url;
    }

}
