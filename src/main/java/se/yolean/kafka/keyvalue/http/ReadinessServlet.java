package se.yolean.kafka.keyvalue.http;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import se.yolean.kafka.keyvalue.KafkaCache;

public class ReadinessServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private KafkaCache cache;

  public ReadinessServlet(KafkaCache cache) {
    this.cache = cache;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doHead(req, resp);
  }

  @Override
  protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    if (cache == null) {
      resp.setStatus(503);
      return;
    }
    resp.setStatus(410); // TODO implement
  }

}
