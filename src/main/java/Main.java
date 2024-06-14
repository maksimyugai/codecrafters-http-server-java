import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

public class Main {

  private static final String COLUMN_WITH_WHITESPACE = ": ";
  private static final String CRLF = "\r\n";

  private static final ExecutorService executorService = Executors.newFixedThreadPool(5);

  public static void main(String[] args) {
    try (var serverSocket = new ServerSocket(4221)) {
      // Since the tester restarts your program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);

      while (true) {
        var clientSocket = serverSocket.accept(); // Wait for connection from client.
        executorService.submit(() -> {
          try {
            var filepath = args.length > 1 ? args[1] : "/tmp";
            handleRequest(clientSocket, filepath);
          } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            Thread.currentThread().interrupt();
          }
        });
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  private static void handleRequest(Socket clientSocket, String filepath) throws IOException {
    var successResponse = "HTTP/1.1 200 OK\r\n";
    var createdResponse = "HTTP/1.1 201 Created\r\n\r\n";
    var notFoundResponse = "HTTP/1.1 404 Not Found\r\n\r\n";

    var bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    var httpRequest = new StringBuilder();
    String line;
    while ((line = bufferedReader.readLine()) != null && !line.isEmpty()) {
      httpRequest.append(line).append("\r\n");
    }

    var outputStream = clientSocket.getOutputStream();
    try {
      var requestLine = getLine(httpRequest.toString(), 0);
      var verb = getVerb(requestLine);
      var path = getPath(requestLine);
      var headers = headers(httpRequest.toString());
      var acceptEncoding = headers.get(Header.ACCEPT_ENCODING.value);
      var contentEncoding = acceptEncoding != null
          ? ContentEncoding.fromStrings(acceptEncoding.split(",")) : null;

      if (path.isEmpty() || path.equals("/")) {
        outputStream.write((successResponse + "\r\n").getBytes());
      } else if (path.contains("/echo")) {
        var secondArgument = path.split("/")[2];
        String response;
        if (contentEncoding != null) {
          var compressedBody = compressString(secondArgument);
          response = ResponseBuilder.builder()
              .setResponseLine(successResponse)
              .setContentLength(compressedBody.length)
              .setContentType(ContentType.TEXT)
              .setContentEncoding(contentEncoding)
              .build();
          outputStream.write(response.getBytes());
          outputStream.write(compressedBody);
        } else {
          response = ResponseBuilder.builder()
              .setResponseLine(successResponse)
              .setContentType(ContentType.TEXT)
              .setBody(secondArgument)
              .setContentLength(secondArgument.length())
              .build();
          outputStream.write(response.getBytes());
        }
      } else if (path.contains("/user-agent")) {
        var userAgent = headers.get(Header.USER_AGENT.value);
        var response = ResponseBuilder.builder()
            .setResponseLine(successResponse)
            .setBody(userAgent)
            .setContentLength(userAgent.length())
            .setContentType(ContentType.TEXT)
            .setContentEncoding(contentEncoding)
            .build();
        outputStream.write(response.getBytes());
      } else if (path.contains("/files")) {
        var absolutePath = Path.of(filepath, path.split("/")[2]).toAbsolutePath();
        if (verb.equalsIgnoreCase("POST")) {
          var contentLength = Integer.parseInt(headers.get(Header.CONTENT_LENGTH.value));
          var body = readBody(bufferedReader, contentLength);
          Files.writeString(absolutePath, body);
          outputStream.write(createdResponse.getBytes());
        } else {
          var content = readFile(absolutePath);
          var response = ResponseBuilder.builder()
              .setResponseLine(successResponse)
              .setBody(content)
              .setContentLength(content.length())
              .setContentType(ContentType.OCTET_STREAM)
              .setContentEncoding(contentEncoding)
              .build();
          outputStream.write(response.getBytes());
        }
      } else {
        outputStream.write(notFoundResponse.getBytes());
      }
    } catch (IOException e) {
      outputStream.write(notFoundResponse.getBytes());
    }
    outputStream.close();
    bufferedReader.close();
  }

  private static String readBody(BufferedReader bufferedReader, int contentLength)
      throws IOException {
    var body = new char[contentLength];
    bufferedReader.read(body, 0, contentLength);
    return new String(body);
  }

  private static String readFile(Path absPath) throws IOException {
    return Files.readString(absPath);
  }

  private static Map<String, String> headers(String request) {
    var headers = request.split("\r\n");
    var headersMap = new HashMap<String, String>();
    for (var header : headers) {
      if (!header.contains(": ")) {
        continue;
      }
      var headerParts = header.split(": ");
      headersMap.put(headerParts[0], headerParts[1]);
    }
    return headersMap;
  }

  private static String getLine(String request, int index) {
    return request.split("\n")[index];
  }

  private static String getPath(String request) {
    return request.split(" ")[1];
  }

  private static String getVerb(String request) {
    return request.split(" ")[0];
  }

  public static byte[] compressString(String str) throws IOException {
    byte[] input = str.getBytes(StandardCharsets.UTF_8);

    var baos = new ByteArrayOutputStream();
    var gos = new GZIPOutputStream(baos);
    gos.write(input);
    gos.close();

    return baos.toByteArray();
  }

  private enum Header {
    CONTENT_TYPE("Content-Type"),
    ACCEPT_ENCODING("Accept-Encoding"),
    CONTENT_ENCODING("Content-Encoding"),
    CONTENT_LENGTH("Content-Length"),
    USER_AGENT("User-Agent");

    private final String value;

    Header(String value) {
      this.value = value;
    }
  }

  private enum ContentType {
    TEXT("text/plain"),
    OCTET_STREAM("application/octet-stream");

    private final String value;

    ContentType(String value) {
      this.value = value;
    }
  }

  private enum ContentEncoding {
    GZIP("gzip"),
    DEFLATE("deflate"),
    GZIP_DEFLATE("gzip, deflate");

    private final String value;

    ContentEncoding(String value) {
      this.value = value;
    }

    public static ContentEncoding fromStrings(String[] values) {
      for (var enc : values) {
        var encoding = fromString(enc.trim());
        if (encoding != null) {
          return encoding;
        }
      }
      return null;
    }

    public static ContentEncoding fromString(String value) {
      return Arrays.stream(ContentEncoding.values())
          .filter(v -> v.value.equalsIgnoreCase(value))
          .findFirst()
          .orElse(null);
    }
  }

  private static class ResponseBuilder {

    private String responseLine;
    private String body;
    private Integer contentLength;
    private ContentType contentType;
    private ContentEncoding contentEncoding;

    public ResponseBuilder() {
    }

    public static ResponseBuilder builder() {
      return new ResponseBuilder();
    }

    public String build() {
      var builder = new StringBuilder(responseLine);

      if (contentType != null) {
        builder.append(Header.CONTENT_TYPE.value).append(COLUMN_WITH_WHITESPACE)
            .append(contentType.value).append(CRLF);
      }
      if (contentEncoding != null) {
        builder.append(Header.CONTENT_ENCODING.value).append(COLUMN_WITH_WHITESPACE)
            .append(contentEncoding.value).append(CRLF);
      }
      if (contentLength != null) {
        builder.append(Header.CONTENT_LENGTH.value).append(COLUMN_WITH_WHITESPACE)
            .append(contentLength).append(CRLF);
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

    public ResponseBuilder setContentType(ContentType contentType) {
      this.contentType = contentType;
      return this;
    }

    public ResponseBuilder setContentEncoding(ContentEncoding contentEncoding) {
      this.contentEncoding = contentEncoding;
      return this;
    }
  }
}
