package se.yolean.kafka.keyvalue.http;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import se.yolean.kafka.keyvalue.Readiness;

/**
 * Based on the example in https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/#define-a-liveness-http-request
 *
 * Path: {@value #ENDPOINT_PATH}
 */
public class ReadinessServlet extends HttpServlet {

  public static final String ENDPOINT_PATH = "/healthz";

  public static final String READINESS_CONTENT_TYPE = "application/json";
  public static final byte[] READY_JSON = "{\"ready\":true}".getBytes();
  public static final byte[] UNREADY_JSON = "{\"ready\":false}".getBytes();
  public static final byte[] NULL_JSON = "{\"ready\":undefined}".getBytes();

  private static final long serialVersionUID = 1L;

  private Readiness readiness;

  public ReadinessServlet() {
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    if (readiness == null) {
      respond(resp, 503, NULL_JSON);
    } else if (readiness.isAppReady()) {
      respond(resp, 200, READY_JSON);
    } else {
      respond(resp, 500, UNREADY_JSON);
    }
  }

  @Override
  protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    if (readiness == null) {
      resp.setStatus(503);
    } else if (readiness.isAppReady()) {
      resp.setStatus(200);
    } else {
      resp.setStatus(500);
    }
  }

  private void respond(HttpServletResponse resp, int status, byte[] body) throws IOException {
    resp.setStatus(status);
    resp.setContentType(READINESS_CONTENT_TYPE);
    resp.setContentLength(body.length);
    ServletOutputStream out = resp.getOutputStream();
    out.write(body);
    out.close();
  }

  public HttpServlet setReadiness(Readiness readiness) {
    this.readiness = readiness;
    return this;
  }

}
