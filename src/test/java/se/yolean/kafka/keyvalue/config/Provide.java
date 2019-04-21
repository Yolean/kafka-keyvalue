package se.yolean.kafka.keyvalue.config;

import javax.inject.Provider;

public class Provide<T> implements Provider<T> {

  public final T instance;

  private int count = 0;

  public Provide(T provideInstance) {
    this.instance = provideInstance;
  }

  @Override
  public T get() {
    count++;
    return instance;
  }

  public int getCount() {
    return count;
  }

}
