package com.opspect.engine;

/** Created by asitk on 8/6/16. */
public class thermal {
  public String color;
  public int sat;

  public thermal() {}

  public thermal(String color, int sat) {
    this.color = color;
    this.sat = sat;
  }

  public String getColor() {
    return color;
  }

  public int getSat() {
    return sat;
  }

  @Override
  public String toString() {
    return "thermal{" + "color='" + color + '\'' + ", sat=" + sat + '}';
  }
}
