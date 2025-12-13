package model;

import java.util.Arrays;

public enum ContentEncodings {
    GZIP("gzip"),
    DEFLATE("deflate"),
    GZIP_DEFLATE("gzip, deflate");

    private final String value;

    private ContentEncodings(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ContentEncodings fromStrings(String[] values) {
        for (var enc : values) {
            var encoding = fromString(enc.trim());
            if (encoding != null) {
                return encoding;
            }
        }
        return null;
    }

    public static ContentEncodings fromString(String value) {
        return Arrays.stream(ContentEncodings.values())
            .filter(v -> v.value.equalsIgnoreCase(value))
            .findFirst()
            .orElse(null);
    }
}
