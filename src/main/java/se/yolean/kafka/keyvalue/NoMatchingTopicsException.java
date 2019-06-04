package se.yolean.kafka.keyvalue;

import java.util.List;
import java.util.Map;

import org.apache.kafka.common.PartitionInfo;

public class NoMatchingTopicsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NoMatchingTopicsException(List<String> wasLookingFor, Map<String, List<PartitionInfo>> butFound) {
    super("Broker returned " + (butFound.size() == 0 ?
      "an empty topic list" :
      butFound.size() + " topic names but the list didn't include all of " + wasLookingFor));
  }

}
