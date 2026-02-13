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
public class QRTag {
  String tag;
  ArrayList<String> values;

  public QRTag() {}
  public QRTag(String tag, ArrayList<String> values) {
    this.tag = tag;
    this.values = values;
  }

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public ArrayList<String> getValues() {
    return values;
  }

  public void setValues(ArrayList<String> values) {
    this.values = values;
  }

  @Override
  public String toString() {
    return "QRTag{" + "tag=" + tag + ", values=" + values + '}';
  } 
}
