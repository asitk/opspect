/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.opscog.engine;

/**
 * @author asitk
 */
public class QRAggregator {
  String name;
  QRAggregatorSampling sampling;

  public QRAggregator() {}

  public QRAggregator(String name, QRAggregatorSampling sampling) {
    this.name = name;
    this.sampling = sampling;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public QRAggregatorSampling getSampling() {
    return sampling;
  }

  public void setSampling(QRAggregatorSampling sampling) {
    this.sampling = sampling;
  }

  @Override
  public String toString() {
    return "QRAggregator{" + "name=" + name + ", sampling=" + sampling + '}';
  }
}
