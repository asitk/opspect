/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.opscog.engine;

import java.util.ArrayList;

/**
 * @author asitk
 */
public class QRMetric {
  String name;
  long limit;
  public ArrayList<QRTag> tags;
  public ArrayList<QRAggregator> aggregators;

  public QRMetric() {}
  ;

  public QRMetric(
      String name, long limit, ArrayList<QRTag> tags, ArrayList<QRAggregator> aggregators) {
    this.name = name;
    this.limit = limit;
    this.tags = tags;
    this.aggregators = aggregators;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getLimit() {
    return limit;
  }

  public void setLimit(long limit) {
    this.limit = limit;
  }

  public ArrayList<QRTag> getTags() {
    return tags;
  }

  public void setTags(ArrayList<QRTag> tags) {
    this.tags = tags;
  }

  public ArrayList<QRAggregator> getAggregators() {
    return aggregators;
  }

  public void setAggregators(ArrayList<QRAggregator> aggregators) {
    this.aggregators = aggregators;
  }

  @Override
  public String toString() {
    return "QRMetric{"
        + "name="
        + name
        + ", limit="
        + limit
        + ", tags="
        + tags
        + ", aggregators="
        + aggregators
        + '}';
  }
}
