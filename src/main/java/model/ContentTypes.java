package model;

public enum ContentTypes {
    TEXT("text/plain"),
    OCTET_STREAM("application/octet-stream");

    private final String value;

    private ContentTypes(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
