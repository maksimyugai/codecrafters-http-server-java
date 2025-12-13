package model;

public enum Headers {
    CONTENT_TYPE("Content-Type"),
    ACCEPT_ENCODING("Accept-Encoding"),
    CONTENT_ENCODING("Content-Encoding"),
    CONTENT_LENGTH("Content-Length"),
    USER_AGENT("User-Agent"),
    CONNECTION("Connection");

    private final String value;

    private Headers(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
