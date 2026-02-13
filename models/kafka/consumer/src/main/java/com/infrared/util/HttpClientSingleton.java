package com.infrared.util;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.*;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;

public class HttpClientSingleton implements Serializable {

    // Singleton
    private static HttpClientSingleton instance= null;
    private static Object mutex= new Object();
    private HttpClientSingleton() {
    }

    public static HttpClientSingleton getInstance(){
        if(instance==null){
            synchronized (mutex){
                if(instance==null) instance= new HttpClientSingleton();
            }
        }
        return instance;
    }


    public int Post(String url, String data) throws IOException {
        int responseCode = -1;

        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            HttpPost request = new HttpPost(url);

            StringEntity params = new StringEntity(data, "UTF-8");
            params.setContentType("application/json");
            request.setEntity(params);
            //System.out.println("Request:" + request.toString());
            CloseableHttpResponse response = httpClient.execute(request);

            try {
                responseCode = response.getStatusLine().getStatusCode();
                if ((response.getStatusLine().getStatusCode() != 200) &&
                        (response.getStatusLine().getStatusCode() != 201)) {
                    throw new RuntimeException("Failed : HTTP error code : "
                            + response.getStatusLine().getStatusCode());
                }
            } finally {
                response.close();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }finally {
            httpClient.close();
        }

        return responseCode;
    }

    public int Put(String url, String data) throws IOException {
        int responseCode = -1;

        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            HttpPut request = new HttpPut(url);

            StringEntity params = new StringEntity(data, "UTF-8");
            params.setContentType("application/json");
            request.setEntity(params);
            System.out.println("Request:" + request.toString());
            CloseableHttpResponse response = httpClient.execute(request);

            try {
                responseCode = response.getStatusLine().getStatusCode();
                if (response.getStatusLine().getStatusCode() == 200 || response.getStatusLine().getStatusCode() == 204) {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader((response.getEntity().getContent())));
                    String output;
                    System.out.println("Output from Server ...." + response.getStatusLine().getStatusCode() + "\n");
                    while ((output = br.readLine()) != null) {
                        System.out.println(output);
                    }
                } else {
                    //logger.error(response.getStatusLine().getStatusCode());
                    throw new RuntimeException("Failed : HTTP error code : "
                            + response.getStatusLine().getStatusCode());
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                System.exit(0);
            } finally {
                response.close();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }finally {
            httpClient.close();
        }

        return responseCode;
    }
}