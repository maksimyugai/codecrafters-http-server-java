package util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import model.HttpMethods;

public final class HttpUtil {

  private HttpUtil() {}

  public static String readBody(
      BufferedReader bufferedReader,
      int contentLength
  ) throws IOException {
    var body = new char[contentLength];
    bufferedReader.read(body, 0, contentLength);
    return new String(body);
  }

  public static String readFile(Path absPath) throws IOException {
    return Files.readString(absPath);
  }

  public static Map<String, String> headers(String request) {
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

  public static String getLine(String request, int index) {
    return request.split("\n")[index];
  }

  public static String getPath(String request) {
    return request.split(" ")[1];
  }

  public static String getVerb(String request) {
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

  public boolean isNewRequest(String line) {
    return Arrays.stream(HttpMethods.values()).anyMatch(verb -> verb.name().equals(line));
  }
}
