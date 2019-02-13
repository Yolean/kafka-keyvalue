package se.yolean.kafka.keyvalue.onupdate;

import se.yolean.kafka.keyvalue.OnUpdate;

public class OnUpdateFactory {

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
    return new OnUpdateHttpIgnoreResult(url);
  }

}
