package se.yolean.kafka.keyvalue;

import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Map;

@ApplicationScoped
@Identifier("kkv.rebalancer")
public class RebalanceListener implements KafkaConsumerRebalanceListener {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RebalanceListener.class);

  private Map<TopicPartition, Long> endOffsets = null;

  private final long startOffset = 0;

  public long getEndOffset(TopicPartition topicPartition) {
    if (this.endOffsets == null) {
      throw new IllegalStateException("Waiting for partition assignment");
    }
    if (!endOffsets.containsKey(topicPartition)) {
      throw new IllegalStateException("Topic-partition " + topicPartition + " not found in " + endOffsets.keySet());
    }
    return endOffsets.get(topicPartition);
  }

  /**
   * Provides offset information to kkv logic.
   *
   * @param consumer   underlying consumer
   * @param partitions set of assigned topic partitions
   */
  @Override
  public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
    if (this.endOffsets != null) {
      logger.warn("Partition reassignment ignored, with no check for differences in the set of partitions");
      return;
    }
    this.endOffsets = consumer.endOffsets(partitions);
    for (TopicPartition partition : partitions) {
      logger.info("Seeking position {} for {}", startOffset, partition);
      consumer.seek(partition, startOffset);
    }
  }

}