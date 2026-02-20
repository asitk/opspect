package com.opspect.engine;

/** Created by asitk on 10/6/16. */
public class nodesummary {
  public String node;
  public String ip;
  public String scope;
  public thermal thermal;

  public nodesummary() {}

  public nodesummary(String node, String ip, String scope, com.opspect.engine.thermal thermal) {
    this.node = node;
    this.ip = ip;
    this.scope = scope;
    this.thermal = thermal;
  }

  public String getNode() {
    return node;
  }

  public String getIp() {
    return ip;
  }

  public String getScope() {
    return scope;
  }

  public com.opspect.engine.thermal getThermal() {
    return thermal;
  }

  @Override
  public String toString() {
    return "nodesummary{"
        + "node='"
        + node
        + '\''
        + ", ip='"
        + ip
        + '\''
        + ", scope='"
        + scope
        + '\''
        + ", thermal="
        + thermal
        + '}';
  }
}
