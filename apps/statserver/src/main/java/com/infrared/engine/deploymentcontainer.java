package com.infrared.engine;

import java.util.ArrayList;

public class deploymentcontainer {
    public String method;
    public String deployment_id;
    public String customer_id;
    public long sts;
    public long ets;
    public ArrayList<clustersummary> clusters = null;

    public deploymentcontainer() {
    }
    
    public deploymentcontainer(String method, String deployment_id, String customer_id, long sts, long ets, 
          ArrayList<clustersummary> clusters) {
      this.method = method;
      this.deployment_id = deployment_id;
      this.customer_id = customer_id;
      this.sts = sts;
      this.ets = ets;
      this.clusters = clusters;
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

    public ArrayList<clustersummary> getClusters() {
      return clusters;
    }

    @Override
    public String toString() {
      return "deploymentcontainer{" + "method=" + method + ", deployment_id=" + deployment_id + ", customer_id=" + customer_id + ", sts=" + sts + ", ets=" + ets + ", clusters=" + clusters + '}';
    }  
}
