package se.yolean.kafka.keyvalue;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Provider;

public class CacheInMemoryProvider implements Provider<Map<String, byte[]>> {

  private int initialSize = 0;

  @Override
  public Map<String, byte[]> get() {
    return new HashMap<String, byte[]>(initialSize);
  }

}
