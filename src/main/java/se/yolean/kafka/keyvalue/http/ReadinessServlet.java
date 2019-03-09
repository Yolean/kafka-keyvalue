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
 */
public class ReadinessServlet extends HttpServlet {

  public static final String READINESS_CONTENT_TYPE = "application/json";
  public static final byte[] READY_JSON = "{\"ready\":true}".getBytes();
  public static final byte[] UNREADY_JSON = "{\"ready\":false}".getBytes();

  private static final long serialVersionUID = 1L;

  private Readiness readiness;

  public ReadinessServlet(Readiness readiness) {
    this.readiness = readiness;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doHead(req, resp);
  }

  @Override
  protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    if (readiness == null) {
      resp.sendError(503);
      return;
    }
    if (readiness.isAppReady()) {
      resp.setStatus(200);
      resp.setContentLength(READY_JSON.length);
      resp.setContentType(READINESS_CONTENT_TYPE);
      ServletOutputStream body = resp.getOutputStream();
      body.write(READY_JSON);
      body.close();
    } else {
      resp.setStatus(500);
      resp.setContentLength(UNREADY_JSON.length);
      resp.setContentType(READINESS_CONTENT_TYPE);
      ServletOutputStream body = resp.getOutputStream();
      body.write(UNREADY_JSON);
      body.close();
    }
  }

}
