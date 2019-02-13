package se.yolean.kafka.keyvalue;

import java.util.Properties;

import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.BeforeEach;

public interface KeyvalueUpdate {

	Topology getTopology();

}
