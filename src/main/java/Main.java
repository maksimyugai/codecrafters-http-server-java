import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

  private static final ExecutorService executorService = Executors.newFixedThreadPool(5);

  public static void main(String[] args) {
     try(var serverSocket = new ServerSocket(4221)) {
       // Since the tester restarts your program quite often, setting SO_REUSEADDR
       // ensures that we don't run into 'Address already in use' errors
       serverSocket.setReuseAddress(true);

       System.out.println("args: " + Arrays.toString(args));

       while(true) {
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

    System.out.println("input: \n" + httpRequest);

    var requestLine = getLine(httpRequest.toString(), 0);
    var verb = getVerb(requestLine);
    var path = getPath(requestLine);
    var headers = headers(httpRequest.toString());

    var outputStream = clientSocket.getOutputStream();
    if (path.isEmpty() || path.equals("/")) {
      outputStream.write((successResponse + "\r\n").getBytes());
    } else if (path.contains("/echo")) {
      var secondArgument = path.split("/")[2];
      outputStream.write(prepareResponse(successResponse, secondArgument, "text/plain").getBytes());
    } else if (path.contains("/user-agent")) {
      var userAgent = headers.get("User-Agent");
      outputStream.write(prepareResponse(successResponse, userAgent, "text/plain").getBytes());
    } else if (path.contains("/files") && verb.equals("POST")) {
      var absolutePath = Path.of(filepath, path.split("/")[2]).toAbsolutePath();
      try {
        var contentLength = Integer.parseInt(headers.get("Content-Length"));
        var body = readBody(bufferedReader, contentLength);
        Files.writeString(absolutePath, body);
        outputStream.write(createdResponse.getBytes());
      } catch(IOException e) {
        outputStream.write(notFoundResponse.getBytes());
      }
    } else if (path.contains("/files")) {
      var absolutePath = Path.of(filepath, path.split("/")[2]).toAbsolutePath();
      try {
        var content = readFile(absolutePath);
        outputStream.write(prepareResponse(successResponse, content, "application/octet-stream").getBytes());
      } catch(IOException e) {
        outputStream.write(notFoundResponse.getBytes());
      }
    } else {
      outputStream.write(notFoundResponse.getBytes());
    }
    bufferedReader.close();
  }

  private static String readBody(BufferedReader bufferedReader, int contentLength) throws IOException {
    var body = new char[contentLength];
    bufferedReader.read(body, 0, contentLength);
    return new String(body);
  }

  private static String readFile(Path absPath) throws IOException {
    return Files.readString(absPath);
  }

  private static String prepareResponse(String start, String body, String contentType) {
    String responseEnd = "Content-Type: " + contentType + "\r\n"
                         + "Content-Length: " + body.getBytes().length + "\r\n"
                         + "\r\n"
                         + body + "\r\n";
    return start + responseEnd;
  }

  private static Map<String, String> headers(String request) {
    var headers = request.split("\r\n");
    var headersMap = new HashMap<String, String>();
    for (var header : headers) {
      if (!header.contains(": ")) continue;
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
}
