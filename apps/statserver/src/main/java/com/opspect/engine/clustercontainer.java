package com.opspect.engine;

import java.util.ArrayList;

/** Created by asitk on 8/6/16. */
public class clustercontainer {
  public String method;
  public String deployment_id;
  public String customer_id;
  public String cluster;
  public long sts;
  public long ets;
  public ArrayList<nodesummary> nodes = null;

  public clustercontainer() {}

  public clustercontainer(
      String method,
      String deployment_id,
      String customer_id,
      String cluster,
      long sts,
      long ets,
      ArrayList<nodesummary> nodes) {
    this.method = method;
    this.deployment_id = deployment_id;
    this.customer_id = customer_id;
    this.cluster = cluster;
    this.sts = sts;
    this.ets = ets;
    this.nodes = nodes;
  }

  public String getMethod() {
    return method;
  }

  public String getDeployment_id() {
    return deployment_id;
  }

  public String getCustomer() {
    return customer_id;
  }

  public String getCluster() {
    return cluster;
  }

  public long getSts() {
    return sts;
  }

  public long getEts() {
    return ets;
  }

  public ArrayList<nodesummary> getNodes() {
    return nodes;
  }

  @Override
  public String toString() {
    return "clustercontainer{"
        + "method='"
        + method
        + '\''
        + ", deployment_id='"
        + deployment_id
        + '\''
        + ", customer_id='"
        + customer_id
        + '\''
        + ", cluster='"
        + cluster
        + '\''
        + ", sts="
        + sts
        + ", ets="
        + ets
        + ", nodes="
        + nodes
        + '}';
  }
}
