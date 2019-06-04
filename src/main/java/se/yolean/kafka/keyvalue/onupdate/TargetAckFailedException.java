package se.yolean.kafka.keyvalue.onupdate;

public class TargetAckFailedException extends Exception {

  private static final long serialVersionUID = 1L;

  public TargetAckFailedException(int status) {
    super("Update target returned status " + status);
  }

  public TargetAckFailedException(Exception e) {
    super(e);
  }

}
