package com.opspect.engine;

// import org.json.JSONObject;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

@Path("/service")
public class service {
  @POST
  @Path("queryabsolute")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public String queryabsolute(String q) {
    System.out.println("queryabsolute: " + q);

    try {
      TimeseriesClient ts = new TimeseriesClient("http://localhost:8181");

      // Todo: Validate query

      // parse
      // build kairos query
      // send results back
      return ts.QueryAbsolute(q);
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
    return null;
  }

  @POST
  @Path("getdeploymentmarkers")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public String getdeploymentmarkers(String s) {
    System.out.println(s);

    try {
      // TimeseriesClient ts = new TimeseriesClient("http://localhost:8181");
      // ts.GetMetricNames();
      // ts.GetTagNames();
      // ts.QueryRelative("system.load15", TimeUnit.HOURS);

      // {"deployment_id":"ec2-dc-01", "customer_id":"1234abcd", "sts":"1463052810",
      // "ets":"1463052810"}
      // JSONObject jsonObj = new JSONObject(s);

      JSONParser parser = new JSONParser();
      Object obj = parser.parse(s);
      JSONObject jsonObj = (JSONObject) obj;

      String deployment_id = jsonObj.get("deployment_id").toString();
      String customer_id = jsonObj.get("customer_id").toString();
      String sts = jsonObj.get("sts").toString();
      String ets = jsonObj.get("ets").toString();

      // if (Objects.equals(deployment_id, "ec2-dc-01") && Objects.equals(customer_id, "1234abcd"))
      // {

      /*
           2016-07-31 14:30  1469975400000 1469955600000
                             1469979000000 1469959200000

           2016-07-31 22:30  1469984400000 1469984400000
                             1469988000000 1469988000000
      */

      String jsonStr =
          "{\n"
              + "    \"method\": \"get_deployment_timeline_markers\",\n"
              + "    \"customer_id\": \"1234abcd\",\n"
              + "    \"deployment_id\": \"ec2-dc-01\",\n"
              + "    \"name\": \"EC2 DC-01\",\n"
              + "    \"description\": \"DataCenter-01 on EC2\",\n"
              + "    \"active\": true,\n"
              + "    \"markers\": [\n"
              + "        {\n"
              + "            \"start\": 1469955600000,\n"
              + "            \"end\": 1469959200000,\n"
              + "            \"thermal\": 100\n"
              + "        },\n"
              + "        {\n"
              + "            \"start\": 1469984400000,\n"
              + "            \"end\": 1469988000000,\n"
              + "            \"thermal\": 1\n"
              + "        }\n"
              + "    ],\n"
              + "    \"time_range\": {\n"
              + "        \"start\": 1469955600000,\n"
              + "        \"end\": 1469988000000\n"
              + "    }\n"
              + "}";

      return jsonStr;
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
    return null;
  }

  @POST
  @Path("getdeploymentdetail")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public String getdeploymentdetail(String s) {
    System.out.println(s);

    try {
      JSONParser parser = new JSONParser();
      JSONObject jsonObj = (JSONObject) parser.parse(s);

      String deployment_id = jsonObj.get("deployment_id").toString();
      String customer_id = jsonObj.get("customer_id").toString();
      String sts = jsonObj.get("sts").toString();
      String ets = jsonObj.get("ets").toString();

      // if (Objects.equals(deployment_id, "ec2-dc-01") && Objects.equals(customer_id, "1234abcd"))
      // {

      /*
        public String cluster;            cluster_id
        public String label;              name
        public String type;               cluster_id
        public int nodecount;             node_count
        public char spof;                 replicate
        public thermal thermal;
      */

      String jsonStr =
          "{\n"
              + "    \"method\": \"get_deployment_detail\",\n"
              + "    \"customer_id\": \"1234abcd\",\n"
              + "    \"deployment_id\": \"ec2-dc-01\",\n"
              + "    \"name\": \"EC2 DC-01\",\n"
              + "    \"description\": \"DataCenter-01 on EC2\",\n"
              + "    \"active\": true,\n"
              + "    \"thermal\": 100,\n"
              + "    \"thermal_count\": {\n"
              + "        \"1\": 85,\n"
              + "        \"20\": 12,\n"
              + "        \"30\": 2,\n"
              + "        \"100\": 0\n"
              + "    },\n"
              + "    \"clusterDetailsList\": [\n"
              + "        {\n"
              + "            \"cluster_id\": \"myappcluster\",\n"
              + "            \"name\": \"MY-APP-CLUSTER\",\n"
              + "            \"description\": \"Apache web cluster\",\n"
              + "            \"active\": true,\n"
              + "            \"thermal\": 100,\n"
              + "            \"thermal_count\": {\n"
              + "                \"1\": 89,\n"
              + "                \"20\": 5,\n"
              + "                \"30\": 4,\n"
              + "                \"100\": 0\n"
              + "            },\n"
              + "            \"node_count\": 1\n"
              + "        }\n"
              + "    ],\n"
              + "    \"time_range\": {\n"
              + "        \"start\": 1469955600000,\n"
              + "        \"end\": 1469988000000\n"
              + "    }\n"
              + "}";
      return jsonStr;
      // }
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }

    return null;
  }

  @POST
  @Path("getdeploymentsnapshot")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public String getdeploymentsnapshot(String s) {
    System.out.println(s);

    try {
      JSONParser parser = new JSONParser();
      JSONObject jsonObj = (JSONObject) parser.parse(s);

      String deployment_id = jsonObj.get("deployment_id").toString();
      String customer_id = jsonObj.get("customer_id").toString();
      String sts = jsonObj.get("sts").toString();
      String ets = jsonObj.get("ets").toString();

      String jsonStr =
          "{\n"
              + "    \"method\": \"getdeploymentsnapshot\",\n"
              + "    \"deployment_id\": \"ec2-dc-01\",\n"
              + "    \"customer_id\": \"1234abcd\",\n"
              + "    \"name\": \"EC2 DC-01\",\n"
              + "    \"description\": \"DataCenter-01 on EC2\",\n"
              + "    \"active\": true,\n"
              + "    \"thermal\": 100,\n"
              + "    \"thermal_count\": {\n"
              + "        \"1\": 79,\n"
              + "        \"20\": 0,\n"
              + "        \"30\": 0,\n"
              + "        \"100\": 0\n"
              + "    },\n"
              + "    \"stats\": [\n"
              + "        {\n"
              + "            \"custID\": \"1234abcd\",\n"
              + "            \"deploymentID\": \"ec2-dc-01\",\n"
              + "            \"clusterID\": \"webserver\",\n"
              + "            \"hostIP\": \"192.168.6.21\",\n"
              + "            \"hostname\": \"ak-ubuntu\",\n"
              + "            \"plugin\": \"cpu\",\n"
              + "            \"target\": \"redis-server:3:0:0:1802:*\",\n"
              + "            \"classification\": \"usage_system\",\n"
              + "            \"sev\": 1,\n"
              + "            \"anomalyClass\": \"Anomaly\",\n"
              + "            \"anomalyType\": \"Peer Detection\",\n"
              + "            \"sevReason\": {\n"
              + "                \"severity\": 1,\n"
              + "                \"total_severity_duration\": 7,\n"
              + "                \"single_max_severity_duration\": 4,\n"
              + "                \"total_duration\": 60\n"
              + "            },\n"
              + "            \"ws\": {\n"
              + "                \"1\": [\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1469957220000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1469957340000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.8,\n"
              + "                        \"startTime\": 1469957700000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    }\n"
              + "                ],\n"
              + "                \"20\": [\n"
              + "                    {\n"
              + "                        \"score\": 0.4,\n"
              + "                        \"startTime\": 1469956680000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 20\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.5,\n"
              + "                        \"startTime\": 1469958360000,\n"
              + "                        \"duration\": 2,\n"
              + "                        \"scoreType\": 20\n"
              + "                    }\n"
              + "                ]\n"
              + "            }\n"
              + "        }, \n"
              + "        {\n"
              + "            \"custID\": \"1234abcd\",\n"
              + "            \"deploymentID\": \"ec2-dc-01\",\n"
              + "            \"clusterID\": \"webserver\",\n"
              + "            \"hostIP\": \"192.168.6.21\",\n"
              + "            \"hostname\": \"ak-ubuntu\",\n"
              + "            \"plugin\": \"cpu\",\n"
              + "            \"target\": \"redis-server:3:0:0:1802:*\",\n"
              + "            \"classification\": \"usage_user\",\n"
              + "            \"sev\": 1,\n"
              + "            \"anomalyClass\": \"Anomaly\",\n"
              + "            \"anomalyType\": \"Peer Detection\",\n"
              + "            \"sevReason\": {\n"
              + "                \"severity\": 1,\n"
              + "                \"total_severity_duration\": 7,\n"
              + "                \"single_max_severity_duration\": 4,\n"
              + "                \"total_duration\": 60\n"
              + "            },\n"
              + "            \"ws\": {\n"
              + "                \"1\": [\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1468503000000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1468503060000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.8,\n"
              + "                        \"startTime\": 1468503420000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.5333333333333333,\n"
              + "                        \"startTime\": 1468503300000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.6666666666666666,\n"
              + "                        \"startTime\": 1468503360000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.6666666666666666,\n"
              + "                        \"startTime\": 1468503180000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1468503120000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    }\n"
              + "                ],\n"
              + "                \"20\": [\n"
              + "                    {\n"
              + "                        \"score\": 0.4,\n"
              + "                        \"startTime\": 1468503240000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 20\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.5,\n"
              + "                        \"startTime\": 1468502820000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 20\n"
              + "                    }\n"
              + "                ]\n"
              + "            }\n"
              + "        },\n"
              + "        {\n"
              + "            \"custID\": \"1234abcd\",\n"
              + "            \"deploymentID\": \"ec2-dc-01\",\n"
              + "            \"clusterID\": \"appserver\",\n"
              + "            \"hostIP\": \"192.168.6.21\",\n"
              + "            \"hostname\": \"ak-ubuntu\",\n"
              + "            \"plugin\": \"cpu\",\n"
              + "            \"target\": \"redis-server:3:0:0:1802:*\",\n"
              + "            \"classification\": \"usage_system\",\n"
              + "            \"sev\": 20,\n"
              + "            \"anomalyClass\": \"Anomaly\",\n"
              + "            \"anomalyType\": \"Peer Detection\",\n"
              + "            \"sevReason\": {\n"
              + "                \"severity\": 1,\n"
              + "                \"total_severity_duration\": 7,\n"
              + "                \"single_max_severity_duration\": 4,\n"
              + "                \"total_duration\": 60\n"
              + "            },\n"
              + "            \"ws\": {\n"
              + "                \"1\": [\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1468503000000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1468503060000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.8,\n"
              + "                        \"startTime\": 1468503420000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.5333333333333333,\n"
              + "                        \"startTime\": 1468503300000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.6666666666666666,\n"
              + "                        \"startTime\": 1468503360000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.6666666666666666,\n"
              + "                        \"startTime\": 1468503180000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1468503120000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    }\n"
              + "                ],\n"
              + "                \"100\": [\n"
              + "                    {\n"
              + "                        \"score\": 0.4,\n"
              + "                        \"startTime\": 1468503240000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 20\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.5,\n"
              + "                        \"startTime\": 1468502820000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 20\n"
              + "                    }\n"
              + "                ]\n"
              + "            }\n"
              + "        },\n"
              + "        {\n"
              + "            \"custID\": \"1234abcd\",\n"
              + "            \"deploymentID\": \"ec2-dc-01\",\n"
              + "            \"clusterID\": \"webserver\",\n"
              + "            \"hostIP\": \"192.168.6.21\",\n"
              + "            \"hostname\": \"ak-ubuntu\",\n"
              + "            \"plugin\": \"cpu\",\n"
              + "            \"target\": \"redis-server:3:0:0:1802:*\",\n"
              + "            \"classification\": \"usage_iowait\",\n"
              + "            \"sev\": 1,\n"
              + "            \"anomalyClass\": \"Anomaly\",\n"
              + "            \"anomalyType\": \"Peer Detection\",\n"
              + "            \"sevReason\": {\n"
              + "                \"severity\": 1,\n"
              + "                \"total_severity_duration\": 7,\n"
              + "                \"single_max_severity_duration\": 4,\n"
              + "                \"total_duration\": 60\n"
              + "            },\n"
              + "            \"ws\": {\n"
              + "                \"1\": [\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1468503000000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1468503060000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.8,\n"
              + "                        \"startTime\": 1468503420000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.5333333333333333,\n"
              + "                        \"startTime\": 1468503300000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.6666666666666666,\n"
              + "                        \"startTime\": 1468503360000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.6666666666666666,\n"
              + "                        \"startTime\": 1468503180000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 1,\n"
              + "                        \"startTime\": 1468503120000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    }\n"
              + "                ],\n"
              + "                \"20\": [\n"
              + "                    {\n"
              + "                        \"score\": 0.4,\n"
              + "                        \"startTime\": 1468503240000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 20\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.5,\n"
              + "                        \"startTime\": 1468502820000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 20\n"
              + "                    }\n"
              + "                ]\n"
              + "            }\n"
              + "        }\n"
              + "        \n"
              + "       \n"
              + "    ],\n"
              + "    \"connections\": [\n"
              + "        {\n"
              + "            \"to\": \"ec2-dc-01\",\n"
              + "            \"avglatency\": 1000\n"
              + "        }\n"
              + "    ]\n"
              + "}";

      return jsonStr;

    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
    return null;
  }

  @POST
  @Path("getclusterdetail")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public String getclusterdetail(String s) {
    System.out.println(s);

    try {
      JSONParser parser = new JSONParser();
      JSONObject jsonObj = (JSONObject) parser.parse(s);

      String deployment_id = jsonObj.get("deployment_id").toString();
      String customer_id = jsonObj.get("customer_id").toString();
      String cluster = jsonObj.get("cluster_id").toString();
      String sts = jsonObj.get("sts").toString();
      String ets = jsonObj.get("ets").toString();

      /*
      public String node;     = host_name
      public String ip;         = host_ip
      public String scope;      = scope
      public thermal thermal;   = thermal
      */

      String jsonStr =
          "{\n"
              + "    \"method\": \"get_cluster_detail\",\n"
              + "    \"customer_id\": \"1234abcd\",\n"
              + "    \"deployment_id\": \"ec2-dc-01\",\n"
              + "    \"cluster_id\": \"myappcluster\",\n"
              + "    \"name\": \"MY-APP-CLUSTER\",\n"
              + "    \"description\": \"Apache web cluster\",\n"
              + "    \"active\": true,\n"
              + "    \"thermal\": 100,\n"
              + "    \"thermal_count\": {\n"
              + "        \"1\": 93,\n"
              + "        \"20\": 9,\n"
              + "        \"30\": 3,\n"
              + "        \"100\": 0\n"
              + "    },\n"
              + "    \"nodeDetailsList\": [\n"
              + "        {\n"
              + "            \"host_name\": \"ak-ubuntu\",\n"
              + "            \"host_ip\": \"192.168.6.21\",\n"
              + "            \"scope\": \"stateless\",\n"
              + "            \"active\": true,\n"
              + "            \"thermal\": 100,\n"
              + "            \"thermal_count\": {\n"
              + "                \"1\": 92,\n"
              + "                \"20\": 5,\n"
              + "                \"30\": 1,\n"
              + "                \"100\": 0\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"time_range\": {\n"
              + "        \"start\": 1469955600000,\n"
              + "        \"end\": 1469959200000\n"
              + "    }\n"
              + "}";
      return jsonStr;
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }

    return null;
  }

  @POST
  @Path("getclustersnapshot")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public String getclustersnapshot(String s) {
    System.out.println(s);

    try {
      JSONParser parser = new JSONParser();
      JSONObject jsonObj = (JSONObject) parser.parse(s);

      String deployment_id = jsonObj.get("deployment_id").toString();
      String customer_id = jsonObj.get("customer_id").toString();
      String cluster = jsonObj.get("cluster_id").toString();
      String sts = jsonObj.get("sts").toString();
      String ets = jsonObj.get("ets").toString();

      String jsonStr =
          "{\n"
              + "    \"method\": \"get_cluster_snapshot\",\n"
              + "    \"customer_id\": \"1234abcd\",\n"
              + "    \"deployment_id\": \"ec2-dc-01\",\n"
              + "    \"cluster_id\": \"myappcluster\",\n"
              + "    \"name\": \"MY-APP-CLUSTER\",\n"
              + "    \"description\": \"Apache web cluster\",\n"
              + "    \"active\": true,\n"
              + "    \"thermal\": 100,\n"
              + "    \"thermal_count\": {\n"
              + "        \"1\": 93,\n"
              + "        \"20\": 9,\n"
              + "        \"30\": 3,\n"
              + "        \"100\": 0\n"
              + "    },\n"
              + "    \"stats\": [\n"
              + "        {\n"
              + "            \"customer_id\": \"1234abcd\",\n"
              + "            \"deployment_id\": \"ec2-dc-01\",\n"
              + "            \"cluster_id\": \"myappcluster\",\n"
              + "            \"host_ip\": \"192.168.6.21\",\n"
              + "            \"host_name\": \"ak-ubuntu\",\n"
              + "            \"plugin\": \"procstat\",\n"
              + "            \"target\": \"mysqld\",\n"
              + "            \"classification\": \"write_bytes\",\n"
              + "            \"thermal\": 30,\n"
              + "            \"anomaly_class\": \"Anomaly\",\n"
              + "            \"anomaly_type\": \"Threshold Detection\",\n"
              + "            \"thermal_reason\": {\n"
              + "                \"severity\": 30,\n"
              + "                \"total_severity_duration\": 4,\n"
              + "                \"single_max_severity_duration\": 4,\n"
              + "                \"total_duration\": 60\n"
              + "            },\n"
              + "            \"ws\": {\n"
              + "                \"1\": [\n"
              + "                    {\n"
              + "                        \"score\": 0.6666666666666666,\n"
              + "                        \"startTime\": 1469180460000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 1\n"
              + "                    }\n"
              + "                ],\n"
              + "                \"20\": [\n"
              + "                    {\n"
              + "                        \"score\": 0.5,\n"
              + "                        \"startTime\": 1469180340000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 20\n"
              + "                    }\n"
              + "                ],\n"
              + "                \"30\": [\n"
              + "                    {\n"
              + "                        \"score\": 0.2,\n"
              + "                        \"startTime\": 1469180640000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 30\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.13333333333333333,\n"
              + "                        \"startTime\": 1469180700000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 30\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.13333333333333333,\n"
              + "                        \"startTime\": 1469180760000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 30\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.13333333333333333,\n"
              + "                        \"startTime\": 1469180820000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 30\n"
              + "                    }\n"
              + "                ],\n"
              + "                \"100\": [\n"
              + "                    {\n"
              + "                        \"score\": 0.06666666666666667,\n"
              + "                        \"startTime\": 1469180880000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 100\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0,\n"
              + "                        \"startTime\": 1469180520000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 100\n"
              + "                    },\n"
              + "                    {\n"
              + "                        \"score\": 0.1,\n"
              + "                        \"startTime\": 1469180580000,\n"
              + "                        \"duration\": 1,\n"
              + "                        \"scoreType\": 100\n"
              + "                    }\n"
              + "                ]\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"connections\": [\n"
              + "        {\n"
              + "            \"to\": {\n"
              + "                \"customer_id\": \"1234abcd\",\n"
              + "                \"deployment_id\": \"ec2-dc-01\",\n"
              + "                \"cluster_id\": \"myappcluster\",\n"
              + "                \"host_ip\": \"*\",\n"
              + "                \"host_name\": \"*\",\n"
              + "                \"scope\": \"stateless\",\n"
              + "                \"active\": true,\n"
              + "                \"last_modified\": 1462613400000\n"
              + "            },\n"
              + "            \"from\": {\n"
              + "                \"customer_id\": \"Unknown\",\n"
              + "                \"deployment_id\": \"Unknown\",\n"
              + "                \"cluster_id\": \"Unknown\",\n"
              + "                \"host_ip\": \"*\",\n"
              + "                \"host_name\": \"*\",\n"
              + "                \"scope\": \"Unknown\",\n"
              + "                \"active\": true,\n"
              + "                \"last_modified\": 0\n"
              + "            },\n"
              + "            \"svc_info\": {\n"
              + "                \"cluster_id\": \"myappcluster\",\n"
              + "                \"name\": \"apache2\",\n"
              + "                \"svc\": 0,\n"
              + "                \"ipver\": 1,\n"
              + "                \"proto\": 0,\n"
              + "                \"port\": 80,\n"
              + "                \"interface\": \"*\"\n"
              + "            },\n"
              + "            \"src_ip\": \"*\",\n"
              + "            \"dst_ip\": \"*\",\n"
              + "            \"dst_port\": \"80\",\n"
              + "            \"avg_rsp_size\": 0,\n"
              + "            \"avg_ttfb\": 0,\n"
              + "            \"avg_ttlb\": 0,\n"
              + "            \"max_ttfb\": 1188,\n"
              + "            \"max_ttlb\": 1188,\n"
              + "            \"error_count\": 0,\n"
              + "            \"request_count\": 59,\n"
              + "            \"sent_bytes\": 65875,\n"
              + "            \"recv_bytes\": 183137,\n"
              + "            \"topmost_error_stats\": [],\n"
              + "            \"costliest_request_stats\": [\n"
              + "                {\n"
              + "                    \"req\": \"GET / HTTP/1.1Host: 192.168.6.21Connection:"
              + " keep-alivePragma: no-cacheCache-Control: no-cacheAccept:"
              + " text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8Upgrade-Insecure-Requests:"
              + " 1User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_5) AppleWebKit/537.36"
              + " (KHTML, like Gecko) Chrome/49.0.2623.112 Safari/537.36Accept-Encoding: gzip,"
              + " deflate, sdchAccept-Language: en-US,en;q=0.8\",\n"
              + "                    \"count\": 1,\n"
              + "                    \"ttfb\": 1188,\n"
              + "                    \"ttlb\": 1188\n"
              + "                },\n"
              + "                {\n"
              + "                    \"req\": \"GET /icons/ubuntu-logo.png HTTP/1.1Host:"
              + " 192.168.6.21Connection: keep-alivePragma: no-cacheCache-Control: no-cacheAccept:"
              + " image/webp,image/*,*/*;q=0.8User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X"
              + " 10_8_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/49.0.2623.112"
              + " Safari/537.36Referer: http://192.168.6.21/Accept-Encoding: gzip, deflate,"
              + " sdchAccept-Language: en-US,en;q=0.8\",\n"
              + "                    \"count\": 2,\n"
              + "                    \"ttfb\": 23,\n"
              + "                    \"ttlb\": 24\n"
              + "                }\n"
              + "            ],\n"
              + "            \"ts\": 0,\n"
              + "            \"duration\": 239975\n"
              + "        }\n"
              + "    ],\n"
              + "    \"time_range\": {\n"
              + "        \"start\": 1466331323000,\n"
              + "        \"end\": 1469613259000\n"
              + "    }\n"
              + "}";
      return jsonStr;
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }

    return null;
  }

  @GET
  @Path("testget")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public String testget() {
    return "{\"hello\":\"test\"}";

    /*
    Map<String, String> testMap = new HashMap<>();
    testMap.put("Key1", "value1");
    testMap.put("key2", "value2");
    JSONObject obj = new JSONObject(testMap);
    return obj;
    */
  }
}
