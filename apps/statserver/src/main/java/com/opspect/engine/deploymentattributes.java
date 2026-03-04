package com.opscog.engine;

import java.util.ArrayList;

public class deploymentattributes {
  public String method;
  public String deployment_id;
  public String customer_id;
  public long sts;
  public long ets;

  public ArrayList<deploymentstats> stats = null;
  public thermal thermal;
  public ArrayList<deploymentconnection> connections = null;

  public deploymentattributes() {}

  public deploymentattributes(
      String method,
      String deployment_id,
      String customer_id,
      long sts,
      long ets,
      ArrayList<deploymentstats> stats,
      com.opscog.engine.thermal thermal,
      ArrayList<deploymentconnection> connections) {
    this.method = method;
    this.deployment_id = deployment_id;
    this.customer_id = customer_id;
    this.sts = sts;
    this.ets = ets;
    this.stats = stats;
    this.thermal = thermal;
    this.connections = connections;
  }

  public String getMethod() {
    return method;
  }

  public String getDeployment_id() {
    return deployment_id;
  }

  public String getCustomer_id() {
    return customer_id;
  }

  public long getSts() {
    return sts;
  }

  public long getEts() {
    return ets;
  }

  public ArrayList<deploymentstats> getStats() {
    return stats;
  }

  public com.opscog.engine.thermal getThermal() {
    return thermal;
  }

  public ArrayList<deploymentconnection> getConnections() {
    return connections;
  }

  @Override
  public String toString() {
    return "deploymentattributes{"
        + "method='"
        + method
        + '\''
        + ", deployment_id='"
        + deployment_id
        + '\''
        + ", customer_id='"
        + customer_id
        + '\''
        + ", sts="
        + sts
        + ", ets="
        + ets
        + ", stats="
        + stats
        + ", thermal="
        + thermal
        + ", connections="
        + connections
        + '}';
  }
}
