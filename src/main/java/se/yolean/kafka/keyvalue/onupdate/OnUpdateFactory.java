package se.yolean.kafka.keyvalue.onupdate;

import java.util.regex.Pattern;

import se.yolean.kafka.keyvalue.OnUpdate;

public class OnUpdateFactory {

  public static final Pattern URL_VALIDATION = Pattern.compile("^https?://[^/]+/.*");

  private OnUpdateFactory() {
  }

  private static OnUpdateFactory instance = null;

  public static OnUpdateFactory getInstance() {
    if (instance == null) {
      instance = new OnUpdateFactory();
    }
    return instance;
  }

  public OnUpdate fromUrl(String url) {
    if (!URL_VALIDATION.matcher(url).matches()) {
      throw new IllegalArgumentException("Invalid onupdate URL: " + url);
    }
    return new OnUpdateHttpIgnoreResult(url);
  }

}
