package se.yolean.kafka.keyvalue.onupdate.hc;

public class ResponseResult {

  private int status;

  static boolean isAck(int status) {
    if (status == 200) return true;
    if (status == 204) return true;
    return false;
  }

  ResponseResult(int status) {
    this.status = status;
  }

  public boolean isAck() {
    return isAck(status);
  }

  int getStatus() {
    return status;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[status=" + getStatus() + "]";
  }

}
