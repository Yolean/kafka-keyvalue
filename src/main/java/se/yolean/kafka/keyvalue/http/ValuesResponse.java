package se.yolean.kafka.keyvalue.http;

import java.util.Iterator;
import java.util.List;

import se.yolean.kafka.keyvalue.TopicPartitionOffset;

public final class ValuesResponse {

  final Iterator<byte[]> values;
  final List<TopicPartitionOffset> currentOffsets;

  public ValuesResponse(Iterator<byte[]> values, List<TopicPartitionOffset> currentOffsets) {
    this.values = values;
    this.currentOffsets = currentOffsets;
  }

}
