package se.yolean.kafka.keyvalue;

import java.util.List;
import java.util.Map;

import org.apache.kafka.common.PartitionInfo;

public class NoMatchingTopicsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NoMatchingTopicsException(List<String> wasLookingFor, Map<String, List<PartitionInfo>> butFound) {
    super(butFound.size() == 0 ?
      "Looks like there are no topics at all on the broker side" :
      "Broker returned " + butFound.size() + " topic names but that didn't include all of " + wasLookingFor);
  }

}
