package com.infrared.engine;

/**
 * Created by asitk on 10/6/16.
 */
public class clusterconnection {
    public String to;
    public int avglatency;

    public clusterconnection() {}
    public clusterconnection(String to, int avglatency) {
        this.to = to;
        this.avglatency = avglatency;
    }

    public String getTo() {
        return to;
    }

    public int getAvglatency() {
        return avglatency;
    }

    @Override
    public String toString() {
        return "clusterconnection{" +
                "to='" + to + '\'' +
                ", avglatency=" + avglatency +
                '}';
    }
}
