package se.yolean.kafka.keyvalue.benchmark;

//import javax.inject.Inject;
//import javax.ws.rs.GET;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain(name = "prepare")
public class Main {
  public static void main(String... args) {
      Quarkus.run(PrepareApp.class, args);
  }

  public static class PrepareApp implements QuarkusApplication {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    //@Inject
    //KafkaSetup kafkaSetup;



    @Override
    public int run(String... args) throws Exception {
      System.out.println("Do startup logic here");
      Quarkus.waitForExit();
      logger.warn("TODO prepare benchmark setup prior to kkv start");
      return 0;
    }
  }
}
