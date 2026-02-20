package com.opspect.engine;

/** Created by asitk on 8/6/16. */
public class deploymentstats {
  public String sev;
  public String cl;
  public String plg;
  public String cls;
  public String msg;

  public deploymentstats() {}

  public deploymentstats(String sev, String cl, String plg, String cls, String msg) {
    this.sev = sev;
    this.cl = cl;
    this.plg = plg;
    this.cls = cls;
    this.msg = msg;
  }

  public String getSev() {
    return sev;
  }

  public String getCl() {
    return cl;
  }

  public String getPlg() {
    return plg;
  }

  public String getCls() {
    return cls;
  }

  public String getMsg() {
    return msg;
  }

  @Override
  public String toString() {
    return "deploymentstats{"
        + "sev='"
        + sev
        + '\''
        + ", host='"
        + cl
        + '\''
        + ", plg='"
        + plg
        + '\''
        + ", cls='"
        + cls
        + '\''
        + ", msg='"
        + msg
        + '\''
        + '}';
  }
}
