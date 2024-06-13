package se.yolean.kafka.keyvalue.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain(name = "prepare")
public class Prepare implements QuarkusApplication {

  private static final Logger logger = LoggerFactory.getLogger(Prepare.class);

  @Override
  public int run(String... args) throws Exception {
    logger.warn("TODO prepare benchmark setup prior to kkv start");
    return 0;
  }

}
