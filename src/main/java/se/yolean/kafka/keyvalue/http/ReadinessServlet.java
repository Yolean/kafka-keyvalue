package se.yolean.kafka.keyvalue.http;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import se.yolean.kafka.keyvalue.Readiness;

/**
 * Based on the example in https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/#define-a-liveness-http-request
 */
public class ReadinessServlet extends HttpServlet {

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
      resp.setContentLength(0);
      resp.getOutputStream().close();
    } else {
      resp.sendError(500);
    }
  }

}
