package se.yolean.kafka.keyvalue.onupdate.hc;

public class ResponseResult {

  private int status;

  ResponseResult(int status) {
    this.status = status;
  }

  boolean isAck() {
    if (status == 200) return true;
    if (status == 204) return true;
    return false;
  }

  int getStatus() {
    return status;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[status=" + getStatus() + "]";
  }

}
