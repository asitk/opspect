package com.infrared.engine;

/**
 * Created by asitk on 8/6/16.
 */
public class clustersummary {
    public String cluster;
    public String label;
    public String type;
    public int nodecount;
    public char spof;
    public thermal thermal;

    public clustersummary() {}

    public clustersummary(String cluster, String label, String type, int nodecount, char spof, com.infrared.engine.thermal thermal) {
        this.cluster = cluster;
        this.label = label;
        this.type = type;
        this.nodecount = nodecount;
        this.spof = spof;
        this.thermal = thermal;
    }

    public String getCluster() {
        return cluster;
    }

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }

    public int getNodecount() {
        return nodecount;
    }

    public char getSpof() {
        return spof;
    }

    public com.infrared.engine.thermal getThermal() {
        return thermal;
    }


    @Override
    public String toString() {
        return "clustersummary{" +
                "cluster='" + cluster + '\'' +
                ", label='" + label + '\'' +
                ", type='" + type + '\'' +
                ", nodecount=" + nodecount +
                ", spof=" + spof +
                ", thermal=" + thermal +
                '}';
    }
}
