package com.opspect.engine;

/** Created by asitk on 10/6/16. */
public class marker {
  public long sts;

  public marker() {}

  public marker(long sts) {
    this.sts = sts;
  }

  public long getSts() {
    return sts;
  }

  @Override
  public String toString() {
    return "marker{" + "sts=" + sts + '}';
  }
}
