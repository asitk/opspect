package com.infrared.engine;

import java.util.ArrayList;

/**
 * Created by asitk on 10/6/16.
 */
public class clusterattributes {
    public String method;
    public String deployment_id;
    public String customer_id;
    public String cluster;
    public long sts;
    public long ets;

    // Todo: Add label and type?

    public ArrayList<clusterstats> stats = null;
    public thermal thermal;
    public ArrayList<clusterconnection> connections = null;

    public clusterattributes() {}
    public clusterattributes(String method, String deployment_id, String customer_id, String cluster, long sts, long ets, ArrayList<clusterstats> stats, com.infrared.engine.thermal thermal, ArrayList<clusterconnection> connections) {
        this.method = method;
        this.deployment_id = deployment_id;
        this.customer_id = customer_id;
        this.cluster = cluster;
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

    public String getCluster() {
        return cluster;
    }

    public long getSts() {
        return sts;
    }

    public long getEts() {
        return ets;
    }

    public ArrayList<clusterstats> getStats() {
        return stats;
    }

    public com.infrared.engine.thermal getThermal() {
        return thermal;
    }

    public ArrayList<clusterconnection> getConnections() {
        return connections;
    }

    @Override
    public String toString() {
        return "clusterattributes{" +
                "method='" + method + '\'' +
                ", deployment_id='" + deployment_id + '\'' +
                ", customer_id='" + customer_id + '\'' +
                ", cluster='" + cluster + '\'' +
                ", sts=" + sts +
                ", ets=" + ets +
                ", stats=" + stats +
                ", thermal=" + thermal +
                ", connections=" + connections +
                '}';
    }
}
