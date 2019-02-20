package se.yolean.kafka.keyvalue.onupdate;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

  public OnUpdate fromManyUrls(List<String> onupdate) {
    if (onupdate.size() < 2) throw new IllegalArgumentException("Use fromUrl for a single onupdate");
    List<OnUpdate> many = onupdate.stream()
        .map(url -> fromUrl(url))
        .collect(Collectors.toUnmodifiableList());
    return new OnUpdateMany(many);
  }

}
