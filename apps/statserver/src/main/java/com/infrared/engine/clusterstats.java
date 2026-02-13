package com.infrared.engine;

/**
 * Created by asitk on 10/6/16.
 */
public class clusterstats {
    public String sev;
    public String host;
    public String ip;
    public String plg;
    public String cls;
    public String msg;

    public clusterstats() {}
    public clusterstats(String sev, String host, String ip, String plg, String cls, String msg) {
        this.sev = sev;
        this.host = host;
        this.ip = ip;
        this.plg = plg;
        this.cls = cls;
        this.msg = msg;
    }

    public String getSev() {
        return sev;
    }

    public String getHost() {
        return host;
    }

    public String getIp() {
        return ip;
    }

    public String getPlg() {
        return plg;
    }

    public String getCls() {
        return cls;
    }

    public String getMsg() {
        return msg;
    }
}
