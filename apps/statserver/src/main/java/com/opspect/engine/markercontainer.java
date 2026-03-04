package com.opscog.engine;

import java.util.ArrayList;

/** Created by asitk on 10/6/16. */
public class markercontainer {
  public ArrayList<marker> markers = null;

  public markercontainer() {}

  public markercontainer(ArrayList<marker> markers) {
    this.markers = markers;
  }

  public ArrayList<marker> getMarkers() {
    return markers;
  }

  @Override
  public String toString() {
    return "markercontainer{" + "markers=" + markers + '}';
  }
}
