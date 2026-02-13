package com.infrared.entry;

/**
 * Created by asitk on 4/8/16.
 */

import java.io.FileReader;

import com.infrared.entry.infra.*;
import com.infrared.util.CassandraDB;
import com.infrared.util.JacksonWrapper;


import org.apache.commons.codec.binary.Base64;
import java.util.Arrays;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import scala.collection.immutable.List;

import javax.ws.rs.*;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Objects;

@Path("/service")
public class service {
    @GET
    @Path("queryabsolute")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response queryabsolute(@QueryParam("rawq") String rawq) {
        System.out.println("encoded: " + rawq);

        byte[] byteArray = Base64.decodeBase64(rawq.getBytes());
        String q = new String(byteArray);

        System.out.println("decoded: " + q);

        // todo: validate params

        try {
            TimeseriesClient ts = new TimeseriesClient("http://localhost:8181");

            // Todo: Validate query

            // parse
            // build kairosdb query
            // send results back
            String strResult = ts.QueryAbsolute(q);

            CacheControl cc = new CacheControl();
            cc.setMaxAge(3600);
            cc.setPrivate(false);

            Response.ResponseBuilder builder = Response.ok(strResult.toString(), MediaType.APPLICATION_JSON);
            builder.cacheControl(cc);
            return builder.build();
        }
        catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return null;
    }

    /////////////////////////////////////////////////////////////////////
    // Deployment

    @GET
    @Path("getdeploymentmarkers")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getdeploymentmarkers(@QueryParam("deployment_id") String deployment_id,
                                       @QueryParam("customer_id") String customer_id,
                                       @QueryParam("sts") Long sts,
                                       @QueryParam("ets") Long ets) {

        System.out.println("getdeploymentmarkers: " + deployment_id);
        System.out.println("getdeploymentmarkers: " + customer_id);
        System.out.println("getdeploymentmarkers: " + sts);
        System.out.println("getdeploymentmarkers: " + ets);

        // todo: validate params

        try
        {
            System.out.println("getdeploymentmarkers Calling cassandra");
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            Deployment.DeploymentTimelineMarker TimeLineMarkers = Deployment.getTimelineMarkers(customer_id, deployment_id, tr);

            System.out.println(TimeLineMarkers.toString());

            CacheControl cc = new CacheControl();
            cc.setMaxAge(3600);
            cc.setPrivate(false);

            Response.ResponseBuilder builder = Response.ok(TimeLineMarkers.toString(), MediaType.APPLICATION_JSON);
            builder.cacheControl(cc);
            return builder.build();
        }
        catch (Exception ex) {
            System.out.println("Exception!");
            System.out.println(ex.getMessage());
        }

        Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
        return builder.build();
    }

    @GET
    @Path("getdeploymentdetail")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getdeploymentdetail(@QueryParam("deployment_id") String deployment_id,
                                      @QueryParam("customer_id") String customer_id,
                                      @QueryParam("sts") Long sts,
                                      @QueryParam("ets") Long ets) {
        System.out.println("getdeploymentdetail: " + deployment_id);
        System.out.println("getdeploymentdetail: " + customer_id);
        System.out.println("getdeploymentdetail: " + sts);
        System.out.println("getdeploymentdetail: " + ets);

        // todo: validate params

        try {
            JSONParser parser = new JSONParser();
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Deployment.DeploymentDetail> DeploymentDetail = Deployment.getDetails(customer_id, deployment_id, tr);

            // Take the first element of the list
            // Todo: Check size
            String strDeploymentDetail = JacksonWrapper.serialize(DeploymentDetail);
            JSONArray jsonDeploymentDetail = (JSONArray) parser.parse(strDeploymentDetail);

            CacheControl cc = new CacheControl();
            cc.setMaxAge(3600);
            cc.setPrivate(false);

            if (jsonDeploymentDetail.size() > 0) {
                JSONObject InnerDetail = (JSONObject) jsonDeploymentDetail.get(0);
                System.out.println("The deployment detail is" + InnerDetail.toJSONString());
                //return InnerDetail.toJSONString();
                Response.ResponseBuilder builder = Response.ok(InnerDetail.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
            else {
                Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
                return builder.build();
            }
        }
        catch (Exception ex) {
            System.out.println(ex.getMessage());
        }

        return null;
    }

    @GET
    @Path("getdeploymentsnapshot")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getdeploymentsnapshot(@QueryParam("deployment_id") String deployment_id,
                                        @QueryParam("customer_id") String customer_id,
                                        @QueryParam("sts") Long sts,
                                        @QueryParam("ets") Long ets) {
        System.out.println("getdeploymentdetail: " + deployment_id);
        System.out.println("getdeploymentdetail: " + customer_id);
        System.out.println("getdeploymentdetail: " + sts);
        System.out.println("getdeploymentdetail: " + ets);

        // todo: validate params

        try {
            JSONParser parser = new JSONParser();

            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Deployment.DeploymentSnapshot> DeploymentSnapshot = Deployment.getSnapshot(customer_id, deployment_id, tr);

            // Take the first element of the list
            // Todo: Check size
            String strDeploymentSnapshot = JacksonWrapper.serialize(DeploymentSnapshot);
            JSONArray jsonDeploymentSnapshot = (JSONArray) parser.parse(strDeploymentSnapshot);

            if (jsonDeploymentSnapshot.size() > 0) {
                JSONObject InnerSnapshot = (JSONObject) jsonDeploymentSnapshot.get(0);
                System.out.println("The deployment snapshot is" + InnerSnapshot.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerSnapshot.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
            else {
                Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
                return builder.build();
            }
        }
        catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return null;
    }

    @GET
    @Path("getdeploymentconnection")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getdeploymentconnection(@QueryParam("deployment_id") String deployment_id,
                                            @QueryParam("customer_id") String customer_id,
                                            @QueryParam("sts") Long sts,
                                            @QueryParam("ets") Long ets) {

        System.out.println("getdeploymentconnection: " + deployment_id);
        System.out.println("getdeploymentconnection: " + customer_id);
        System.out.println("getdeploymentconnection: " + sts);
        System.out.println("getdeploymentconnection: " + ets);

        // todo: validate params

        try
        {
            JSONParser parser = new JSONParser();
            System.out.println("getdeploymentconnection Calling cassandra");
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Deployment.DeploymentConnection> DeploymentConnection = Deployment.getConnection(customer_id, deployment_id, tr);

            String strDeploymentConnection = JacksonWrapper.serialize(DeploymentConnection);
            JSONArray jsonDeploymentConnection = (JSONArray) parser.parse(strDeploymentConnection);

            if (jsonDeploymentConnection.size() > 0) {
                JSONObject InnerDetail = (JSONObject) jsonDeploymentConnection.get(0);
                System.out.println("The deployment connection is" + InnerDetail.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerDetail.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
        }
        catch (Exception ex) {
            System.out.println("Exception!");
            System.out.println(ex.getMessage());
        }

        Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
        return builder.build();
    }


    @GET
    @Path("getdeploymentservice")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getdeploymentservice(@QueryParam("deployment_id") String deployment_id,
                                         @QueryParam("customer_id") String customer_id,
                                         @QueryParam("sts") Long sts,
                                         @QueryParam("ets") Long ets) {

        System.out.println("getdeploymentservice: " + deployment_id);
        System.out.println("getdeploymentservice: " + customer_id);
        System.out.println("getdeploymentservice: " + sts);
        System.out.println("getdeploymentservice: " + ets);

        // todo: validate params

        try
        {
            JSONParser parser = new JSONParser();
            System.out.println("getdeploymentservice calling cassandra");
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Deployment.DeploymentService> DeploymentService = Deployment.getService(customer_id, deployment_id, tr);

            String strDeploymentService = JacksonWrapper.serialize(DeploymentService);
            JSONArray jsonDeploymentService = (JSONArray) parser.parse(strDeploymentService);

            if (jsonDeploymentService.size() > 0) {
                JSONObject InnerDetail = (JSONObject) jsonDeploymentService.get(0);
                System.out.println("The deployment service is" + InnerDetail.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerDetail.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
        }
        catch (Exception ex) {
            System.out.println("Exception!");
            System.out.println(ex.getMessage());
        }

        Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
        return builder.build();
    }

    /////////////////////////////////////////////////////////////////////
    // Cluster

    @GET
    @Path("getclustermarkers")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getdeploymentmarkers(@QueryParam("deployment_id") String deployment_id,
                                         @QueryParam("customer_id") String customer_id,
                                         @QueryParam("cluster_id") String cluster_id,
                                         @QueryParam("sts") Long sts,
                                         @QueryParam("ets") Long ets) {

        System.out.println("getclustermarkers: " + deployment_id);
        System.out.println("getclustermarkers: " + customer_id);
        System.out.println("getclustermarkers: " + cluster_id);
        System.out.println("getclustermarkers: " + sts);
        System.out.println("getclustermarkers: " + ets);

        // todo: validate params

        try
        {
            System.out.println("getclustermarkers Calling cassandra");
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            Cluster.ClusterTimelineMarker TimeLineMarkers = Cluster.getTimelineMarkers(customer_id, deployment_id, cluster_id, tr);

            System.out.println(TimeLineMarkers.toString());

            CacheControl cc = new CacheControl();
            cc.setMaxAge(3600);
            cc.setPrivate(false);

            Response.ResponseBuilder builder = Response.ok(TimeLineMarkers.toString(), MediaType.APPLICATION_JSON);
            builder.cacheControl(cc);
            return builder.build();
        }
        catch (Exception ex) {
            System.out.println("Exception!");
            System.out.println(ex.getMessage());
        }

        Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
        return builder.build();
    }

    @GET
    @Path("getclusterdetail")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getclusterdetail(@QueryParam("deployment_id") String deployment_id,
                                   @QueryParam("customer_id") String customer_id,
                                   @QueryParam("cluster_id") String cluster_id,
                                   @QueryParam("sts") Long sts,
                                   @QueryParam("ets") Long ets) {
        System.out.println("getclusterdetail: " + deployment_id);
        System.out.println("getclusterdetail: " + customer_id);
        System.out.println("getclusterdetail: " + cluster_id);
        System.out.println("getclusterdetail: " + sts);
        System.out.println("getclusterdetail: " + ets);

        // todo: validate params

        try {
            JSONParser parser = new JSONParser();

            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Cluster.ClusterDetail> ClusterDetail = Cluster.getDetails(customer_id, deployment_id, cluster_id, tr);

            // Take the first element of the list
            // Todo: Check size
            String strClusterDetail = JacksonWrapper.serialize(ClusterDetail);
            JSONArray jsonClusterDetail = (JSONArray) parser.parse(strClusterDetail);


            if (jsonClusterDetail.size() > 0) {
                JSONObject InnerDetail = (JSONObject) jsonClusterDetail.get(0);
                System.out.println("The cluster detail is" + InnerDetail.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerDetail.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
            else {
                Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
                return builder.build();
            }
        }
        catch(Exception ex) {
            System.out.println(ex.getMessage());
        }

        return null;
    }

    @GET
    @Path("getclustersnapshot")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getclustersnapshot(@QueryParam("deployment_id") String deployment_id,
                                     @QueryParam("customer_id") String customer_id,
                                     @QueryParam("cluster_id") String cluster_id,
                                     @QueryParam("sts") Long sts,
                                     @QueryParam("ets") Long ets) {
        System.out.println("getclustersnapshot: " + deployment_id);
        System.out.println("getclustersnapshot: " + customer_id);
        System.out.println("getclustersnapshot: " + cluster_id);
        System.out.println("getclustersnapshot: " + sts);
        System.out.println("getclustersnapshot: " + ets);

        // todo: validate params

        try {
            JSONParser parser = new JSONParser();
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Cluster.ClusterSnapshot> ClusterSnapshot = Cluster.getSnapshot(customer_id, deployment_id, cluster_id, tr);

            // Todo: Check size
            String strClusterSnapshot = JacksonWrapper.serialize(ClusterSnapshot);
            JSONArray jsonClusterSnapshot = (JSONArray) parser.parse(strClusterSnapshot);

            if (jsonClusterSnapshot.size() > 0) {
                JSONObject InnerSnapshot = (JSONObject) jsonClusterSnapshot.get(0);
                System.out.println("The cluster snapshot is" + InnerSnapshot.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerSnapshot.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
            else {
                Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
                return builder.build();
            }
        }
        catch(Exception ex) {
            System.out.println(ex.getMessage());
        }

        return null;
    }

    @GET
    @Path("getclusterconnection")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getclusterconnection(@QueryParam("deployment_id") String deployment_id,
                                         @QueryParam("customer_id") String customer_id,
                                         @QueryParam("cluster_id") String cluster_id,
                                         @QueryParam("sts") Long sts,
                                         @QueryParam("ets") Long ets) {

        System.out.println("getclusterconnection: " + deployment_id);
        System.out.println("getclusterconnection: " + customer_id);
        System.out.println("getclusterconnection: " + cluster_id);
        System.out.println("getclusterconnection: " + sts);
        System.out.println("getclusterconnection: " + ets);

        // todo: validate params

        try
        {
            JSONParser parser = new JSONParser();
            System.out.println("getclusterconnection Calling cassandra");
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Cluster.ClusterConnection> ClusterConnection = Cluster.getConnection(customer_id, deployment_id, cluster_id, tr);

            String strClusterConnection = JacksonWrapper.serialize(ClusterConnection);
            JSONArray jsonClusterConnection = (JSONArray) parser.parse(strClusterConnection);

            if (jsonClusterConnection.size() > 0) {
                JSONObject InnerDetail = (JSONObject) jsonClusterConnection.get(0);
                System.out.println("The cluster connection is" + InnerDetail.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerDetail.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
        }
        catch (Exception ex) {
            System.out.println("Exception!");
            System.out.println(ex.getMessage());
        }

        Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
        return builder.build();
    }


    @GET
    @Path("getclusterservice")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getclusterservice(@QueryParam("deployment_id") String deployment_id,
                                         @QueryParam("customer_id") String customer_id,
                                         @QueryParam("cluster_id") String cluster_id,
                                         @QueryParam("sts") Long sts,
                                         @QueryParam("ets") Long ets) {

        System.out.println("getclusterservice: " + deployment_id);
        System.out.println("getclusterservice: " + customer_id);
        System.out.println("getclusterservice: " + cluster_id);
        System.out.println("getclusterservice: " + sts);
        System.out.println("getclusterservice: " + ets);

        // todo: validate params

        try
        {
            JSONParser parser = new JSONParser();
            System.out.println("getclusterservice calling cassandra");
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Cluster.ClusterService> ClusterService = Cluster.getService(customer_id, deployment_id, cluster_id, tr);

            String strClusterService = JacksonWrapper.serialize(ClusterService);
            JSONArray jsonClusterService = (JSONArray) parser.parse(strClusterService);

            if (jsonClusterService.size() > 0) {
                JSONObject InnerDetail = (JSONObject) jsonClusterService.get(0);
                System.out.println("The cluster service is" + InnerDetail.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerDetail.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
        }
        catch (Exception ex) {
            System.out.println("Exception!");
            System.out.println(ex.getMessage());
        }

        Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
        return builder.build();
    }

    /////////////////////////////////////////////////////////////////////
    // Node

    @GET
    @Path("getnodemarkers")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getnodemarkers(@QueryParam("deployment_id") String deployment_id,
                                   @QueryParam("customer_id") String customer_id,
                                   @QueryParam("cluster_id") String cluster_id,
                                   @QueryParam("node_id") String node_id,
                                   @QueryParam("sts") Long sts,
                                   @QueryParam("ets") Long ets) {

        System.out.println("getnodemarkers: " + deployment_id);
        System.out.println("getnodemarkers: " + customer_id);
        System.out.println("getnodemarkers: " + cluster_id);
        System.out.println("getnodemarkers: " + node_id);
        System.out.println("getnodemarkers: " + sts);
        System.out.println("getnodemarkers: " + ets);

        // todo: validate params

        try
        {
            System.out.println("getnodemarkers Calling cassandra");
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            Node.NodeTimelineMarker TimeLineMarkers = Node.getTimelineMarkers(customer_id, deployment_id, cluster_id, node_id, tr);

            System.out.println(TimeLineMarkers.toString());

            CacheControl cc = new CacheControl();
            cc.setMaxAge(3600);
            cc.setPrivate(false);

            Response.ResponseBuilder builder = Response.ok(TimeLineMarkers.toString(), MediaType.APPLICATION_JSON);
            builder.cacheControl(cc);
            return builder.build();
        }
        catch (Exception ex) {
            System.out.println("Exception!");
            System.out.println(ex.getMessage());
        }

        Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
        return builder.build();
    }

    @GET
    @Path("getnodedetail")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getnodedetail(@QueryParam("deployment_id") String deployment_id,
                                  @QueryParam("customer_id") String customer_id,
                                  @QueryParam("cluster_id") String cluster_id,
                                  @QueryParam("node_id") String node_id,
                                  @QueryParam("sts") Long sts,
                                  @QueryParam("ets") Long ets) {
        System.out.println("getnodedetail: " + deployment_id);
        System.out.println("getnodedetail: " + customer_id);
        System.out.println("getnodedetail: " + cluster_id);
        System.out.println("getnodedetail: " + node_id);
        System.out.println("getnodedetail: " + sts);
        System.out.println("getnodedetail: " + ets);

        // todo: validate params

        try {
            JSONParser parser = new JSONParser();

            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Node.NodeDetail> NodeDetail = Node.getDetails(customer_id, deployment_id, cluster_id, node_id, tr);

            // Take the first element of the list
            // Todo: Check size
            String strNodeDetail = JacksonWrapper.serialize(NodeDetail);
            JSONArray jsonNodeDetail = (JSONArray) parser.parse(strNodeDetail);

            if (jsonNodeDetail.size() > 0) {
                JSONObject InnerDetail = (JSONObject) jsonNodeDetail.get(0);
                System.out.println("The node detail is" + InnerDetail.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerDetail.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
            else {
                Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
                return builder.build();
            }
        }
        catch(Exception ex) {
            System.out.println(ex.getMessage());
        }

        return null;
    }

    @GET
    @Path("getnodesnapshot")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getnodesnapshot(@QueryParam("deployment_id") String deployment_id,
                                    @QueryParam("customer_id") String customer_id,
                                    @QueryParam("cluster_id") String cluster_id,
                                    @QueryParam("node_id") String node_id,
                                    @QueryParam("sts") Long sts,
                                    @QueryParam("ets") Long ets) {
        System.out.println("getnodesnapshot: " + deployment_id);
        System.out.println("getnodesnapshot: " + customer_id);
        System.out.println("getnodesnapshot: " + cluster_id);
        System.out.println("getnodesnapshot: " + node_id);
        System.out.println("getnodesnapshot: " + sts);
        System.out.println("getnodesnapshot: " + ets);

        // todo: validate params

        try {
            JSONParser parser = new JSONParser();
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Node.NodeSnapshot> NodeSnapshot = Node.getSnapshot(customer_id, deployment_id, cluster_id, node_id, tr);

            // Todo: Check size
            String strNodeSnapshot = JacksonWrapper.serialize(NodeSnapshot);
            JSONArray jsonNodeSnapshot = (JSONArray) parser.parse(strNodeSnapshot);

            if (jsonNodeSnapshot.size() > 0) {
                JSONObject InnerSnapshot = (JSONObject) jsonNodeSnapshot.get(0);
                System.out.println("The node snapshot is" + InnerSnapshot.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerSnapshot.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
            else {
                Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
                return builder.build();
            }
        }
        catch(Exception ex) {
            System.out.println(ex.getMessage());
        }

        return null;
    }

    @GET
    @Path("getnodeconnection")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getnodeconnection(@QueryParam("deployment_id") String deployment_id,
                                      @QueryParam("customer_id") String customer_id,
                                      @QueryParam("cluster_id") String cluster_id,
                                      @QueryParam("node_id") String node_id,
                                      @QueryParam("sts") Long sts,
                                      @QueryParam("ets") Long ets) {

        System.out.println("getnodeconnection: " + deployment_id);
        System.out.println("getnodeconnection: " + customer_id);
        System.out.println("getnodeconnection: " + cluster_id);
        System.out.println("getnodeconnection: " + node_id);
        System.out.println("getnodeconnection: " + sts);
        System.out.println("getnodeconnection: " + ets);

        // todo: validate params

        try
        {
            JSONParser parser = new JSONParser();
            System.out.println("getnodeconnection Calling cassandra");
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Node.NodeConnection> NodeConnection = Node.getConnection(customer_id, deployment_id, cluster_id, node_id, tr);

            String strNodeConnection = JacksonWrapper.serialize(NodeConnection);
            JSONArray jsonNodeConnection = (JSONArray) parser.parse(strNodeConnection);

            if (jsonNodeConnection.size() > 0) {
                JSONObject InnerDetail = (JSONObject) jsonNodeConnection.get(0);
                System.out.println("The node connection is" + InnerDetail.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerDetail.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
        }
        catch (Exception ex) {
            System.out.println("Exception!");
            System.out.println(ex.getMessage());
        }

        Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
        return builder.build();
    }

    @GET
    @Path("getnodeservice")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getnodeservice(@QueryParam("deployment_id") String deployment_id,
                                   @QueryParam("customer_id") String customer_id,
                                   @QueryParam("cluster_id") String cluster_id,
                                   @QueryParam("node_id") String node_id,
                                   @QueryParam("sts") Long sts,
                                   @QueryParam("ets") Long ets) {

        System.out.println("getnodeservice: " + deployment_id);
        System.out.println("getnodeservice: " + customer_id);
        System.out.println("getnodeservice: " + cluster_id);
        System.out.println("getnodeservice: " + node_id);
        System.out.println("getnodeservice: " + sts);
        System.out.println("getnodeservice: " + ets);

        // todo: validate params

        try
        {
            JSONParser parser = new JSONParser();
            System.out.println("getnodeservice calling cassandra");
            CassandraDB.TimeRange tr = new CassandraDB.TimeRange(sts, ets);
            List<Node.NodeService> NodeService = Node.getService(customer_id, deployment_id, cluster_id, node_id, tr);

            String strNodeService = JacksonWrapper.serialize(NodeService);
            JSONArray jsonNodeService = (JSONArray) parser.parse(strNodeService);

            if (jsonNodeService.size() > 0) {
                JSONObject InnerDetail = (JSONObject) jsonNodeService.get(0);
                System.out.println("The node service is" + InnerDetail.toJSONString());

                CacheControl cc = new CacheControl();
                cc.setMaxAge(3600);
                cc.setPrivate(false);

                Response.ResponseBuilder builder = Response.ok(InnerDetail.toJSONString(), MediaType.APPLICATION_JSON);
                builder.cacheControl(cc);
                return builder.build();
            }
        }
        catch (Exception ex) {
            System.out.println("Exception!");
            System.out.println(ex.getMessage());
        }

        Response.ResponseBuilder builder = Response.ok("{}", MediaType.APPLICATION_JSON);
        return builder.build();
    }
}
