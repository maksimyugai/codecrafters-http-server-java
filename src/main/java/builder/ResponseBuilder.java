package builder;

import static util.HttpUtil.COLUMN_WITH_WHITESPACE;
import static util.HttpUtil.CRLF;

import model.ContentEncodings;
import model.ContentTypes;
import model.Headers;

public class ResponseBuilder {

  private String responseLine;
  private String body;
  private Integer contentLength;
  private ContentTypes contentTypes;
  private ContentEncodings contentEncodings;

  public ResponseBuilder() {
  }

  public static ResponseBuilder builder() {
    return new ResponseBuilder();
  }

  public static ResponseBuilder builder(String responseLine, String body, Integer contentLength,
      ContentTypes contentTypes, ContentEncodings contentEncodings) {
    return ResponseBuilder.builder()
        .setResponseLine(responseLine)
        .setBody(body)
        .setContentLength(contentLength)
        .setContentType(contentTypes)
        .setContentEncoding(contentEncodings);
  }


  public String build() {
    var builder = new StringBuilder(responseLine);

    if (contentTypes != null) {
      builder
          .append(Headers.CONTENT_TYPE.getValue())
          .append(COLUMN_WITH_WHITESPACE)
          .append(contentTypes.getValue())
          .append(CRLF);
    }
    if (contentEncodings != null) {
      builder
          .append(Headers.CONTENT_ENCODING.getValue())
          .append(COLUMN_WITH_WHITESPACE)
          .append(contentEncodings.getValue())
          .append(CRLF);
    }
    if (contentLength != null) {
      builder
          .append(Headers.CONTENT_LENGTH.getValue())
          .append(COLUMN_WITH_WHITESPACE)
          .append(contentLength)
          .append(CRLF);
    }

    builder.append(CRLF);

    if (body != null) {
      builder.append(body);
    }

    return builder.toString();
  }

  public ResponseBuilder setResponseLine(String responseLine) {
    this.responseLine = responseLine;
    return this;
  }

  public ResponseBuilder setBody(String body) {
    this.body = body;
    return this;
  }

  public ResponseBuilder setContentLength(int contentLength) {
    this.contentLength = contentLength;
    return this;
  }

  public ResponseBuilder setContentType(ContentTypes contentTypes) {
    this.contentTypes = contentTypes;
    return this;
  }

  public ResponseBuilder setContentEncoding(ContentEncodings contentEncodings) {
    this.contentEncodings = contentEncodings;
    return this;
  }
}
