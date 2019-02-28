package se.yolean.kafka.keyvalue.onupdate;

public class UnrecognizedOnupdateResult extends Exception {

  private static final long serialVersionUID = 1L;

  public UnrecognizedOnupdateResult(Throwable error, HttpTargetRequestInvoker invoker) {
    super(error.getMessage() + " for " + invoker, error);
  }

}
