import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

  private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

  public static void main(String[] args) {
     try(var serverSocket = new ServerSocket(4221)) {
       // Since the tester restarts your program quite often, setting SO_REUSEADDR
       // ensures that we don't run into 'Address already in use' errors
       serverSocket.setReuseAddress(true);

       while(true) {
         var clientSocket = serverSocket.accept(); // Wait for connection from client.
         executorService.submit(() -> {
           try {
             handleRequest(clientSocket);
           } catch (IOException e) {
             System.out.println("IOException: " + e.getMessage());
           }
         });
       }
     } catch (IOException e) {
       System.out.println("IOException: " + e.getMessage());
     }
  }

  private static void handleRequest(Socket clientSocket) throws IOException {
    var successResponse = "HTTP/1.1 200 OK\r\n";
    var notFoundResponse = "HTTP/1.1 404 Not Found\r\n\r\n";

    var line = "";
    var httpRequest = new StringBuilder();

    var buffReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    while((line = buffReader.readLine()) != null && !line.isEmpty()) {
      httpRequest.append(line).append("\n");
    }

    var firstLine = getLine(httpRequest.toString(), 0);
    var path = getPath(firstLine);
    var headers = headers(httpRequest.toString());

    if (path.isEmpty() || path.equals("/")) {
      clientSocket.getOutputStream().write((successResponse + "\r\n").getBytes());
    } else if (path.contains("/echo")) {
      var secondArgument = path.split("/")[2];
      clientSocket.getOutputStream()
          .write(prepareResponse(successResponse, secondArgument).getBytes());
    } else if (path.contains("/user-agent")) {
      var userAgent = headers.get("User-Agent");
      clientSocket.getOutputStream().write(prepareResponse(successResponse, userAgent).getBytes());
    } else {
      clientSocket.getOutputStream().write(notFoundResponse.getBytes());
    }
  }

  private static String prepareResponse(String start, String body) {
    String responseEnd = "Content-Type: text/plain\r\n"
                         + "Content-Length: " + body.length() + "\r\n"
                         + "\r\n"
                         + body + "\r\n";
    return start + responseEnd;
  }

  private static Map<String, String> headers(String request) {
    var headers = request.split("\n");
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
