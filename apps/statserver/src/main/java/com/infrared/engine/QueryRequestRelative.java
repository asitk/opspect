/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.infrared.engine;

import java.util.ArrayList;

/**
 *
 * @author asitk
 */
public class QueryRequestRelative {
  QRRelativeTime start_relative;
  QRRelativeTime end_relative;
  String time_zone;
  public ArrayList<QRMetric> metrics;

  public QueryRequestRelative() {};
  public QueryRequestRelative(QRRelativeTime start_relative, QRRelativeTime end_relative, String time_zone, ArrayList<QRMetric> metrics) {
    this.start_relative = start_relative;
    this.end_relative = end_relative;
    this.time_zone = time_zone;
    this.metrics = metrics;
  }

  public QRRelativeTime getStart_relative() {
    return start_relative;
  }

  public void setStart_relative(QRRelativeTime start_relative) {
    this.start_relative = start_relative;
  }

  public QRRelativeTime getEnd_relative() {
    return end_relative;
  }

  public void setEnd_relative(QRRelativeTime end_relative) {
    this.end_relative = end_relative;
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
    return "QueryRequestRel{" + "start_relative=" + start_relative + ", end_relative=" + end_relative + ", time_zone=" + time_zone + ", metrics=" + metrics + '}';
  }
}
