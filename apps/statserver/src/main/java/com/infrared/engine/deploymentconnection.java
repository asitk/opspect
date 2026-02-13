package com.infrared.engine;

/**
 * Created by asitk on 9/6/16.
 */
public class deploymentconnection {
    public String to;
    public int avglatency;

    public deploymentconnection() {}
    public deploymentconnection(String to, int avglatency) {
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
        return "deploymentconnection{" +
                "to='" + to + '\'' +
                ", avglatency=" + avglatency +
                '}';
    }
}