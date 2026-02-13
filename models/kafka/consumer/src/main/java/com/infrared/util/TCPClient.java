package com.infrared.util;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;

public class TCPClient implements Serializable {

    // make sure you include \n at the end
    public void Send(String data, String url, Integer nPort) {
        try {
            Socket sock = new Socket(url, nPort);
            sock.getOutputStream().write(data.getBytes());
            sock.close();
        }
        catch (IOException e) {
            Log.getLogger().fatal(e.getMessage());
            e.printStackTrace();
            System.exit(0);
        }

    }
}
