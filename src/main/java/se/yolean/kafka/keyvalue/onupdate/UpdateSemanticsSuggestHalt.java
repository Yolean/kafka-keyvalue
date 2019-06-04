package se.yolean.kafka.keyvalue.onupdate;

public class UpdateSemanticsSuggestHalt extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UpdateSemanticsSuggestHalt(String string, TargetAckFailedException e) {
    super(string, e);
  }

}
