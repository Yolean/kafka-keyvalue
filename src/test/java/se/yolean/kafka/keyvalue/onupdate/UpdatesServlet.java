package se.yolean.kafka.keyvalue.onupdate;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UpdatesServlet extends HttpServlet {

  final List<HttpServletRequest> posts = new LinkedList<>();
  final List<String> payloads = new LinkedList<>();

  private static final long serialVersionUID = 1L;

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    posts.add(req);
    BufferedReader reader = req.getReader();
    payloads.add(reader.readLine());
    reader.close();
    resp.setStatus(200);
  }

}
