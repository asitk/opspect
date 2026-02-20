/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.opspect.engine;

import java.util.ArrayList;

/**
 * @author asitk
 */
public class QueryRequestAbsolute {
  long start_absolute;
  long end_absolute;
  long cache_time;

  String time_zone;
  public ArrayList<QRMetric> metrics;

  public QueryRequestAbsolute() {}
  ;

  public QueryRequestAbsolute(
      long start_absolute,
      long end_absolute,
      long cache_time,
      String time_zone,
      ArrayList<QRMetric> metrics) {
    this.start_absolute = start_absolute;
    this.end_absolute = end_absolute;
    this.cache_time = cache_time;
    this.time_zone = time_zone;
    this.metrics = metrics;
  }

  public long getStart_absolute() {
    return start_absolute;
  }

  public void setStart_absolute(long start_absolute) {
    this.start_absolute = start_absolute;
  }

  public long getEnd_absolute() {
    return end_absolute;
  }

  public void setEnd_absolute(long end_absolute) {
    this.end_absolute = end_absolute;
  }

  public long getCache_time() {
    return cache_time;
  }

  public void setCache_time(long cache_time) {
    this.cache_time = cache_time;
  }

  public String getTime_zone() {
    return time_zone;
  }

  public void setTime_zone(String time_zone) {
    this.time_zone = time_zone;
  }

  public ArrayList<QRMetric> getMetrics() {
    return metrics;
  }

  public void setMetrics(ArrayList<QRMetric> metrics) {
    this.metrics = metrics;
  }

  @Override
  public String toString() {
    return "QueryRequestAbsolute{"
        + "start_absolute="
        + start_absolute
        + ", end_absolute="
        + end_absolute
        + ", cache_time="
        + cache_time
        + ", time_zone="
        + time_zone
        + ", metrics="
        + metrics
        + '}';
  }
}
