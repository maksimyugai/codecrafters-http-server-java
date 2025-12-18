import static util.HttpUtil.CRLF;
import static util.HttpUtil.getCreatedString;
import static util.HttpUtil.getLine;
import static util.HttpUtil.getNotFoundString;
import static util.HttpUtil.getPath;
import static util.HttpUtil.getSuccessString;
import static util.HttpUtil.getVerb;
import static util.HttpUtil.readBody;
import static util.HttpUtil.readFile;
import static util.HttpUtil.headers;
import static util.HttpUtil.compressString;

import builder.ResponseBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import model.ContentEncodings;
import model.ContentTypes;
import model.Headers;
import model.HttpMethods;

public class Main {

  private static final ExecutorService executorService =
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

  public static void main(String[] args) {
    try (var serverSocket = new ServerSocket(4221)) {
      serverSocket.setReuseAddress(true);

      while (true) {
        var clientSocket = serverSocket.accept();
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

  private static void handleRequest(Socket clientSocket, String filepath)
      throws IOException {
    var bufferedReader = new BufferedReader(
        new InputStreamReader(clientSocket.getInputStream())
    );

    var httpRequest = new StringBuilder();

    String line;
    while ((line = bufferedReader.readLine()) != null && !line.isEmpty()) {
      httpRequest.append(line).append(CRLF);
      System.out.println("Request line: " + line);
    }

    var headers = headers(httpRequest.toString());

    if (headers.containsKey(Headers.CONNECTION.getValue())
        && headers.get(Headers.CONNECTION.getValue()).equalsIgnoreCase("close")) {
      bufferedReader.close();
      clientSocket.close();
    }

    var outputStream = clientSocket.getOutputStream();
    try {
      var requestLine = getLine(httpRequest.toString(), 0);
      var verb = getVerb(requestLine);
      var path = getPath(requestLine);
      var acceptEncoding = headers.get(Headers.ACCEPT_ENCODING.getValue());
      var contentEncoding = acceptEncoding != null
          ? ContentEncodings.fromStrings(acceptEncoding.split(","))
          : null;

      if (path.isEmpty() || path.equals("/")) {
        outputStream.write((getSuccessString() + CRLF).getBytes());
      } else if (path.contains("/echo")) {
        var secondArgument = path.split("/")[2];
        String response;
        if (contentEncoding != null) {
          var compressedBody = compressString(secondArgument);
          response = ResponseBuilder.builder()
              .setResponseLine(getSuccessString())
              .setContentLength(compressedBody.length)
              .setContentType(ContentTypes.TEXT)
              .setContentEncoding(contentEncoding)
              .build();
          outputStream.write(response.getBytes());
          outputStream.write(compressedBody);
        } else {
          response = ResponseBuilder.builder()
              .setResponseLine(getSuccessString())
              .setContentType(ContentTypes.TEXT)
              .setBody(secondArgument)
              .setContentLength(secondArgument.length())
              .build();
          outputStream.write(response.getBytes());
        }
      } else if (path.contains("/user-agent")) {
        var userAgent = headers.get(Headers.USER_AGENT.getValue());
        var response = ResponseBuilder.builder(getSuccessString(), userAgent, userAgent.length(), ContentTypes.TEXT, contentEncoding).build();
        outputStream.write(response.getBytes());
      } else if (path.contains("/files")) {
        var absolutePath = Path.of(
            filepath,
            path.split("/")[2]
        ).toAbsolutePath();
        if (verb.equalsIgnoreCase(HttpMethods.POST.name())) {
          var contentLength = Integer.parseInt(
              headers.get(Headers.CONTENT_LENGTH.getValue())
          );
          var body = readBody(bufferedReader, contentLength);
          Files.writeString(absolutePath, body);
          outputStream.write(getCreatedString().getBytes());
        } else {
          var content = readFile(absolutePath);
          var response = ResponseBuilder.builder(getSuccessString(), content, content.length(), ContentTypes.OCTET_STREAM, contentEncoding).build();
          outputStream.write(response.getBytes());
        }
      } else {
        outputStream.write(getNotFoundString().getBytes());
      }
    } catch (IOException e) {
      outputStream.write(getNotFoundString().getBytes());
    }

    outputStream.close();
//    bufferedReader.close();
//    clientSocket.close();
  }
}
