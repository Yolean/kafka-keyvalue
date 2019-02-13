package se.yolean.kafka.keyvalue;

import org.apache.kafka.streams.Topology;

public interface KeyvalueUpdate extends KafkaCache {

  Topology getTopology();

}
