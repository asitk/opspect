/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.infrared.engine;

import com.infrared.kairosdb.client.builder.TimeUnit;

/**
 *
 * @author asitk
 */
public class QRAggregatorSampling {
  int value;
  TimeUnit unit;

  public QRAggregatorSampling() {}
  
  public QRAggregatorSampling(int value, TimeUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  public int getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

  public TimeUnit getUnit() {
    return unit;
  }

  public void setUnit(TimeUnit unit) {
    this.unit = unit;
  }

  @Override
  public String toString() {
    return "QRAggregatorSampling{" + "value=" + value + ", unit=" + unit + '}';
  }
}
