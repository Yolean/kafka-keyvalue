package se.yolean.kafka.keyvalue.cli;

import se.yolean.kafka.keyvalue.App;
import se.yolean.kafka.keyvalue.CacheServiceOptions;
import se.yolean.kafka.keyvalue.onupdate.OnUpdateFactory;

public class Main {

  public static void main(String[] args) {
    OnUpdateFactory onUpdateFactory = OnUpdateFactory.getInstance();

    CacheServiceOptions options = new ArgsToOptions()
        .setOnUpdateFactory(onUpdateFactory)
        .fromCommandLineArguments(args);

    new App(options);
  }

}
