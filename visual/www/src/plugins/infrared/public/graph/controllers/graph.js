'use strict';

//import _ from 'lodash';
import Promise from 'bluebird';
import $ from 'jquery';
import 'angular';

import moment from 'moment';
import 'moment-timezone';

import Highcharts from 'highcharts/highcharts.js';
import 'highcharts/highcharts-more.js';
import 'highcharts/modules/heatmap.js';
import 'highcharts/modules/exporting.js';

import 'datatables.net-buttons';
import 'datatables.net-buttons-dt/css/buttons.dataTables.css';

import 'datatables.net';
import 'datatables.net-dt/css/jquery.dataTables.css'

import 'bootstrap';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/css/bootstrap-theme.min.css';

import 'jqwidgets-framework/jqwidgets/styles/jqx.base.css';
import jqwidgets from 'jqwidgets-framework/jqwidgets/jqx-all.js';

import VisProvider from 'ui/vis';
import vis from 'vis/dist/vis.min.js';
import 'vis/dist/vis.min.css';

import 'jstree/dist/themes/default/style.min.css'
import 'jstree/dist/jstree.min.js'
import 'plugins/infrared/graph/controllers/_graph';
import uiRoutes from 'ui/routes';
import uiModules from 'ui/modules';
import chrome from 'ui/chrome';

import 'plugins/infrared/graph/styles/graph.css';  // load after vis

import graph_template from 'plugins/infrared/graph/index.html';

const app = uiModules.get('apps/graph', []);

uiRoutes
  .when('/graph', {
    template: graph_template
  });

app.service('graphservice', ['$http', '$window', function ($http, $window) {
  var graphservice = this;

  this.setHeader = function () {
    var graphservice = this;

    var sts = graphservice.Model.get_curr_marker().sts;
    var ets = graphservice.Model.get_curr_marker().ets;

    var startDt      = new Date(sts);
    var endDt        = new Date(ets);
    var startTimeStr = moment(startDt).format('MMMM Do YYYY, h:mm:ss a');
    var endTimeStr   = moment(endDt).format('h:mm:ss a');

    var scope = graphservice.Model.get_scope();

    // Todo: Update with current scope

    $("#graphfixedheadertext").html("Current Selection [From: "
                                    + startTimeStr + " -"
                                    + " To: "
                                    + endTimeStr + "]"
                                    + " Scope: " + scope);
  };

  ////////////////////////////////////////
  // REST Access Layer                  //
  ///////////////////////////////////////

  this.queryabsolute = function (jsonobj) {
    return $http.post('http://www.nuvidata.com:5601/service/queryabsolute', jsonobj);
  };

  /////////////////////////////////////////////////////////////////////
  // Deployment

  this.getdeploymentmarkers = function (jsonobj) {

    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getdeploymentmarkers', config);
  };

  this.getdeploymentdetail = function (jsonobj) {
    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getdeploymentdetail', config);
  };

  this.getdeploymentsnapshot = function (jsonobj) {
    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getdeploymentsnapshot', config);
  };

  this.getdeploymentconnection = function (jsonobj) {

    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getdeploymentconnection', config);
  };

  this.getdeploymentservice = function (jsonobj) {

    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getdeploymentservice', config);
  };

  /////////////////////////////////////////////////////////////////////
  // Cluster

  this.getclustermarkers = function (jsonobj) {
    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getclustermarkers', config);
  };

  this.getclusterdetail = function (jsonobj) {
    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getclusterdetail', config);
  };

  this.getclustersnapshot = function (jsonobj) {
    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getclustersnapshot', config);
  };

  this.getclusterconnection = function (jsonobj) {

    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getclusterconnection', config);
  };

  this.getclusterservice = function (jsonobj) {

    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getclusterservice', config);
  };

  /////////////////////////////////////////////////////////////////////
  // Cluster

  this.getnodemarkers = function (jsonobj) {
    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getnodemarkers', config);
  };

  this.getnodedetail = function (jsonobj) {
    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getnodedetail', config);
  };

  this.getnodesnapshot = function (jsonobj) {
    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getnodesnapshot', config);
  };

  this.getnodeconnection = function (jsonobj) {

    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getnodeconnection', config);
  };

  this.getnodeservice = function (jsonobj) {

    var config = {
      params: jsonobj,
      headers: {
        "Content-Type": "application/json"
      }
    };
    return $http.get('http://www.nuvidata.com:5601/service/getnodeservice', config);
  };

  ////////////////////////////////////////
  // REST Access Layer Ends             //
  ///////////////////////////////////////

  // returns markers corresponding to scope
  this.get_markers = function (commonfactory, deployment_id, customer_id, cluster_id, node_id, sts, ets) {
    var gs = this;

    var scope = gs.Model.get_scope();

    /*
    if (scope === 'node') {
      return new Promise(function (resolve, reject) {
        gs.getnodemarkers({
                            "deployment_id": deployment_id,
                            "customer_id": customer_id,
                            "cluster_id": cluster_id,
                            "node_id": node_id,
                            "sts": sts,
                            "ets": ets
                          }).success(function (response) {
          resolve(response);
        }).error(function (e) {
          console.log("get_markers err: " + e);
          reject(e);
        });
      })
    }

    if (scope === 'cluster') {
      return new Promise(function (resolve, reject) {
        gs.getclustermarkers({
                               "deployment_id": deployment_id,
                               "customer_id": customer_id,
                               "cluster_id": cluster_id,
                               "sts": sts,
                               "ets": ets
                             }).success(function (response) {
          resolve(response);
        }).error(function (e) {
          console.log("get_markers err: " + e);
          reject(e);
        });
      })
    }
    */

    if (scope === 'deployment') {
      return new Promise(function (resolve, reject) {
        gs.getdeploymentmarkers({
                                  "deployment_id": deployment_id,
                                  "customer_id": customer_id,
                                  "sts": sts,
                                  "ets": ets
                                }).success(function (response) {
          resolve(response);
        }).error(function (e) {
          console.log("get_markers err: " + e);
          reject(e);
        });
      })
    }
  };


  this.display_stats_create_rows = function () {
    var gs = this;
    var dr = [];
    var brow_found = 0;

    var ne = gs.Model.get_network_composite();

    // add any missing cluster connections
    for (var edx = 0; edx < ne.edges.length; edx ++) {
      var clusterid = ne.edges[edx].to;

      if (typeof ne.edges[edx].props !== 'undefined') {

        // display prop around corresponding cluster
        for (var ndx = 0; ndx < ne.nodes.length; ndx++) {
          if (ne.nodes[ndx].id === clusterid) {
            var cluster_name = ne.nodes[ndx].label;

            // create data mapping
            // var data =
            // commonfactory.create_stats_mapping(ne.edges[edx].props);
            for (var cdx = 0; cdx < ne.edges[edx].props.costliest_request_stats.length; cdx ++) {
              var req = ne.edges[edx].props.costliest_request_stats[cdx].req;
              var count = ne.edges[edx].props.costliest_request_stats[cdx].count;
              var ttfb = ne.edges[edx].props.costliest_request_stats[cdx].ttfb;
              var ttlb = ne.edges[edx].props.costliest_request_stats[cdx].ttlb;

              var row = [];
              row.push(cluster_name);
              row.push(req);
              row.push(count);
              row.push(ttfb);
              row.push(ttlb);
              dr.push(row);

              brow_found = 1;
            }
          }
        }
      }
    }

    return dr;
  };

  this.display_stats_datatable = function (commonfactory) {
    var graphservice = this;

    var d = [];
    var table;
    var rows_selected = []; // Array holding selected row IDs

    var calc_width = function () {
      return $('#costly_requests_grid').parent().width() - 10;
    };

    var calc_height = function () {
      return $('#costly_requests_grid').parent().height() - 10;
    };

    d = graphservice.display_stats_create_rows();
    // console.log("display_stats_datatable create_rows: " +
    // JSON.stringify(d));

    var oldt = graphservice.Model.get_datatable_obj();
    if (typeof oldt !== 'undefined' && oldt !== null) {
      console.log("display_stats_datatable found existing instance");
      table = oldt;
      table.clear();
      if (d.length > 0) {
        table.rows.add(d);
        table.draw();
      }
    }
    else {
      console.log("display_stats_datatable: creating new table");
      table = $('#costly_requests_grid').DataTable({
                                     pageLength: 5,
                                     "lengthMenu": [[5, 10, 25, 50, -1], [5, 10, 25, 50, "All"]],
                                     data: d,
                                     columns: [
                                       { title: "Cluster" },
                                       { title: "Request" },
                                       { title: "Count" },
                                       { title: "TTFB" },
                                       { title: "TTLB" },
                                      ],
                                     columnDefs: [
                                       { width: '20%', targets: 0 },
                                       { width: '20%', targets: 1 },
                                       { width: '20%', targets: 2 },
                                       { width: '20%', targets: 3 },
                                       { width: '20%', targets: 4 },
                                     ],
                                   }); // DataTable init

      // set instance into model
      graphservice.Model.set_datatable_obj(table);
    }
  };

  /////////////////////////////////////////
  // Network processing                  //
  ////////////////////////////////////////

  this.processdeploymentsnapshot = function (deployment_id, customer_id, sts, ets, displaydeployment) {
    var gs = this;

    return new Promise(function (resolve, reject) {
      var getdeploymentsnapshotfn = gs.getdeploymentsnapshot;

      if (typeof getdeploymentsnapshotfn === "undefined") {
        console.log("err: processdeploymentsnapshot:" +
                    " getdeploymentsnapshotfn is undefined");
      }

      getdeploymentsnapshotfn({
                                "deployment_id": deployment_id,
                                "customer_id": customer_id,
                                "sts": sts,
                                "ets": ets
                              }).success(function (response) {
        var deploymentsnapshot = response;
        var composite          = {};

        if ($.isEmptyObject(deploymentsnapshot) == false) {
          // console.log("processdeploymentsnapshot " +
          // JSON.stringify(deploymentsnapshot));
          composite.deploymentsnapshot = deploymentsnapshot;
          resolve(composite);
        }
        else {
          console.log("err: processdeploymentsnapshot" +
                      " deploymentsnapshot is empty for " + JSON.stringify(deployment_id));
          composite.deploymentsnapshot = deploymentsnapshot;
          reject(composite); // todo: Check this
        }
      }).error(function (e) {
        console.log("processdeploymentsnapshot err: " + e);
        reject(e);
      });
    })
  };

  this.processclusterconnections = function (deployment_id, customer_id, cluster_id, sts, ets) {
    var gs = this;

    return new Promise(function (resolve, reject) {
      var getclusterconnectionsfn = gs.getclusterconnection;

      getclusterconnectionsfn({
                                "deployment_id": deployment_id,
                                "customer_id": customer_id,
                                "cluster_id": cluster_id,
                                "sts": sts,
                                "ets": ets
                              }).success(function (response) {
        var clusterconnections = response;
        var edges              = [];
        var nodes              = [];
        var composite          = {};

        if ($.isEmptyObject(clusterconnections) == false) {
          // console.log("processclusterconnections: " +
          // JSON.stringify(clusterconnections));

          if (clusterconnections.connections.length > 0) {

            var bOnce = 0;
            for (var cindex = 0; cindex < clusterconnections.connections.length; cindex++) {

              if (clusterconnections.connections[cindex].from.cluster_id === "*") {
                console.log("processclusterconnections: Found *");

                // push a '*' node
                // create a node for the service
                if (!bOnce) {
                  nodes.push({
                               "id": clusterconnections.connections[cindex].from.cluster_id,
                               "label": "*",
                               "group": "external_user",
                               "elemtype": "external"
                             });
                  bOnce=1;
                }
              }

              // todo: add bucketization function
              var width = Math.round(clusterconnections.connections[cindex].request_count / 400);
              if (width > 10) {
                width = 10;
              }
              if (width < 4) {
                width = 4;
              }

              // create props object and add to edge
              var props                     = {};
              props.svc_info                = clusterconnections.connections[cindex].svc_info;
              props.src_ip                  = clusterconnections.connections[cindex].from.host_ip;
              props.dst_ip                  = clusterconnections.connections[cindex].to.host_ip;
              props.dst_port                = clusterconnections.connections[cindex].svc_info.port;
              props.avg_rsp_size            = clusterconnections.connections[cindex].avg_rsp_size;
              props.avg_ttfb                = clusterconnections.connections[cindex].avg_ttfb;
              props.avg_ttlb                = clusterconnections.connections[cindex].avg_ttlb;
              props.max_ttfb                = clusterconnections.connections[cindex].max_ttfb;
              props.max_ttlb                = clusterconnections.connections[cindex].max_ttlb;
              props.error_count             = clusterconnections.connections[cindex].error_count;
              props.sent_bytes              = clusterconnections.connections[cindex].sent_bytes;
              props.recv_bytes              = clusterconnections.connections[cindex].recv_bytes;
              props.topmost_error_stats     = clusterconnections.connections[cindex].topmost_error_stats;
              props.costliest_request_stats = clusterconnections.connections[cindex].topmost_error_stats;

              if (clusterconnections.connections[cindex].from.cluster_id !== clusterconnections.connections[cindex].to.cluster_id) {
                edges.push({
                             id: clusterconnections.connections[cindex].from.cluster_id + "_"+clusterconnections.connections[cindex].to.cluster_id,
                             from: clusterconnections.connections[cindex].from.cluster_id,
                             to: clusterconnections.connections[cindex].to.cluster_id,

                             label: 'avg ttfb: ' + props.avg_ttlb,
                             font: {
                               strokeWidth: 0,
                               color: '#ffffff',
                               align: 'horizontal'
                             },
                             labelHighlightBold: false,
                             shadow: false,
                             smooth: true,

                             arrows: "to",
                             width: width,
                             props: props,
                           });
              }
            }

            // console.log("processclustersnapshot edges: " +
            // JSON.stringify(edges));
            composite.nodes = nodes;
            composite.edges = edges;
            resolve(composite);
          }
          else {
            // console.log("processclustersnapshot: clustersdetail" +
            // " length is 0" + " empty for " + cluster_id);
            composite.edges = edges;
            resolve(composite);
          }
        }
        else {
          // console.log("processclustersnapshot: clustersdetail is" +
          // " empty for " + deployment_id);
          composite.edges = edges;
          reject(composite);
        }
      }).error(function (e) {
        console.log("processclusterconnections err: " + e);
        reject(e);
      });
    })
  };

  this.processdeploymentservice = function (deployment_id, customer_id, sts, ets) {
    var gs = this;
    return new Promise(function (resolve, reject) {
      var getdeploymentservicefn = gs.getdeploymentservice;

      getdeploymentservicefn({
                            "deployment_id": deployment_id,
                            "customer_id": customer_id,
                            "sts": sts,
                            "ets": ets
                          }).success(function (response) {

        var deploymentservices = response;
        // console.log("processdeploymentservice: " +
        // JSON.stringify(response));

        var composite     = {};
        composite.deploymentservice = response;

        resolve(composite);
      }).error(function (e) {
        console.log("processdeploymentservice err: " + e);
        reject(e);
      });

    });
  };

  this.processclusterservice = function (deployment_id, customer_id, cluster_id, sts, ets) {
    var gs = this;

    return new Promise(function (resolve, reject) {
      var getclusterservicefn = gs.getclusterservice;

      getclusterservicefn({
                            "deployment_id": deployment_id,
                            "customer_id": customer_id,
                            "cluster_id": cluster_id,
                            "sts": sts,
                            "ets": ets
                          }).success(function (response) {

        var clusterservices = response;

        var composite         = {};
        var edges             = [];
        var nodes             = [];
        var services          = [];
        var observations_map  = {};

        if (typeof clusterservices.services === 'undefined') {
          console.log("processclusterservice: services are undefined");
          return;
        }

        for (var sindex = 0; sindex < clusterservices.services.length; sindex++) {

          // update observations map
          var obj = clusterservices.services[sindex].observations;
          for (var key in obj) { var k=key; }
          var value = parseInt(clusterservices.services[sindex].observations[k]);

          //console.log("processclusterservice observations: " +
          // JSON.stringify(obj));
          //console.log("processclusterservice value: " +
          // JSON.stringify(value));

          if (typeof observations_map[k] === 'undefined') {
            observations_map[k] = value;
          }
          else {
            observations_map[k] += value;
          }

          services.push(clusterservices.services[sindex].svc_info["name"]);

          // create a node for the service

          /* uncomment to see service connections */
          nodes.push({
                       "id": cluster_id + "_" + clusterservices.services[sindex].svc_info["name"],
                       "label": clusterservices.services[sindex].svc_info["name"],
                       "group": "sm" + "process",
                       "elemtype": "service"
                     });

          // Simulate connection from each node to its cluster
          edges.push({
                       from: cluster_id + "_" + clusterservices.services[sindex].svc_info["name"],
                       to: clusterservices.services[sindex].svc_info["cluster_id"],
                       //arrows: "to",
                       width: 2,
           });

        }

        composite.edges = edges;
        composite.nodes = nodes;

        composite["services_observations"] = {};
        composite["services_observations"]["cluster_id"] = cluster_id;
        composite["services_observations"]["services"] = services;
        composite["services_observations"]["observations_map"] = observations_map;

        console.log("processclusterservice composite: " + JSON.stringify(composite));

        resolve(composite);
      }).error(function (e) {
        console.log("processclusterservice err: " + e);
        reject(e);
      });

    });
  };

  this.get_nodes_and_edges = function (commonfactory, deployment_id, customer_id, sts, ets, displaydeployment) {
    var gs = this;

    return new Promise(function (resolve, reject) {

      // console.log("get_nodes_and_edges" +
      //            " deployment_id: " + JSON.stringify(deployment_id)
      // + " customer_id: " + JSON.stringify(customer_id)
      //            + " sts: " + JSON.stringify(sts)
      //            + " ets: " + JSON.stringify(ets));

      var nodes              = [];
      var edges              = [];
      var deploymentsnapshot = {};
      var composite          = {};

      var promises = [];

      gs.getdeploymentdetail({
                               "deployment_id": deployment_id,
                               "customer_id": customer_id, "sts": sts,
                               "ets": ets
                             }).success(function (response) {
        var deploymentdetail = response;

        if ($.isEmptyObject(deploymentdetail) === 'true') {
          console.log("Err: get_nodes_and_edges getdeploymentdetail is" +
                      " empty!");
          return;
        }
        if (typeof deploymentdetail.clusterDetailsList === 'undefined') {
          console.log("Err: get_nodes_and_edges clusterDetailsList is" +
                      " undefined!");
          return;
        }

        // console.log("get_nodes_and_edges:" +
        // " deploymentdetail" + JSON.stringify(deploymentdetail));
        // console.log("get_nodes_and_edges:" + "
        // cluster length " + deploymentdetail.clusterDetailsList.length);

        var deployment_id = deploymentdetail.deployment_id;
        var customer_id   = deploymentdetail.customer_id;
        var cluster_id    = deploymentdetail.clusterDetailsList[0].cluster_id;

        var sts = gs.Model.get_curr_marker().sts;
        var ets = gs.Model.get_curr_marker().ets;

        if (displaydeployment) {
          nodes.push({
                       "id": deployment_id,
                       "label": deployment_id,
                       "group": "deployment",
                       "thermal": deploymentdetail.thermal,
                       "elemtype": "deployment",
                     });

          // connect the deployment id to the last cluster
          edges.push({
                       "id": deployment_id+"_"+deploymentdetail.clusterDetailsList[deploymentdetail.clusterDetailsList.length - 1].cluster_id,
                       "from": deployment_id,
                       "to": deploymentdetail.clusterDetailsList[deploymentdetail.clusterDetailsList.length - 1].cluster_id,
                       "arrows": "to",
                       "width": 6,
                     });
        }

        // get deployment snapshot (One time call hence this does not
        // need to be in the for loop)
        // promises.push(gs.processdeploymentsnapshot(deployment_id,
        // customer_id, sts, ets, displaydeployment));

        // get observations for each cluster in order to create icon map
        // promises.push(gs.processdeploymentservice(deployment_id,
        // customer_id, sts, ets));

        // walk clusters
        for (var dindex = 0; dindex < deploymentdetail.clusterDetailsList.length; dindex++) {

          // push each cluster to nodes
          // console.log("get_nodes_and_edges role:" +
          // " ",
          // JSON.stringify(deploymentdetail.clusterDetailsList[dindex].role));

          nodes.push({
                       "id": deploymentdetail.clusterDetailsList[dindex].cluster_id,
                       "label": deploymentdetail.clusterDetailsList[dindex].name,
                       "group": deploymentdetail.clusterDetailsList[dindex].role,

                       "thermal": deploymentdetail.clusterDetailsList[dindex].thermal,
                       "thermal_count": deploymentdetail.clusterDetailsList[dindex].thermal_count,

                       // push replicated flag (for flag icon)
                       "replicated": deploymentdetail.clusterDetailsList[dindex].replicated,

                       "elemtype": "cluster",
                     });

          deployment_id = deploymentdetail.deployment_id;
          customer_id   = deploymentdetail.customer_id;
          cluster_id    = deploymentdetail.clusterDetailsList[dindex].cluster_id;
          sts           = gs.Model.get_curr_marker().sts;
          ets           = gs.Model.get_curr_marker().ets;

          // add connections for each cluster into edges
          promises.push(gs.processclusterconnections(deployment_id, customer_id, cluster_id, sts, ets));

          // add services for each cluster into edges in order to
          // create incoming connections into the cluster
          promises.push(gs.processclusterservice(deployment_id, customer_id, cluster_id, sts, ets));

          // get nodes for each cluster
          // promises.push(gs.processclusterdetail(deployment_id,customer_id, cluster_id, sts, ets));

        } // for

        // update nodes from all clusters also fill edges array with
        // simulated connections
        Promise.all(promises).then(function (result) {
          // console.log("get_nodes_and_edges promise.all result: " +
          // JSON.stringify(result));

          var services_observations_objlist = [];

          for (var i = 0; i < result.length; i++) {
            if (typeof result[i].nodes !== 'undefined') {
              for (var j = 0; j < result[i].nodes.length; j++) {
                nodes.push(result[i].nodes[j]);
              }
            }

            if (typeof result[i].edges !== 'undefined') {
              for (var j = 0; j < result[i].edges.length; j++) {
                edges.push(result[i].edges[j]);
              }
            }

            if (typeof result[i].services_observations !== 'undefined') {
              services_observations_objlist.push(result[i].services_observations);
            }
          }

          composite = {
            nodes: nodes,
            edges: edges,
            services_observations_objlist: services_observations_objlist,
          };

          gs.Model.set_network_composite(composite);

          // console.log("get_nodes_and_edges final composite: " +
          // JSON.stringify(gs.Model.get_network_composite()));

          resolve(composite);

        }).catch(function (e) {
          console.log("err get_nodes_and_edges: some promise" +
                      " failed: " + e);
        });
      }).error(function (e) {
        console.log("err get_nodes_and_edges deploymentdetail: " + e);
        reject(e);
      });
    }).catch(function (e) {
      console.log("err get_nodes_and_edges: " + e);
      reject(e);
    });
  };

  /* Display property box */
  this.display_topology_proc_before_drawing = function (commonfactory, composite, network, ctx) {
    var gs = this;

    var ne = gs.Model.get_network_composite();

    if (typeof ne === 'undefined')
      return;
    if (typeof ne.edges === 'undefined')
      return;

    // add property boxes
    for (var edx = 0; edx < ne.edges.length; edx ++) {
      var clusterid = ne.edges[edx].to;
      if (typeof ne.edges[edx].props !== 'undefined') {

        // console.log("display_topology_proc_before_drawing" +
        //             " ne.edges[edx].id: " +
        // JSON.stringify(ne.edges[edx].id));

          // create data mapping
          var data = commonfactory.create_stats_mapping(ne.edges[edx].props);

          // locate the service node for this clusterid
          var servicenodeid = clusterid + "_" + ne.edges[edx].props.svc_info.name;
          for (var ndx = 0; ndx < ne.edges.length; ndx ++) {
            if (ne.nodes[ndx].id === servicenodeid) {

              // console.log("display_topology_proc_before_drawing data:
              // " + JSON.stringify(data));

              // get the placement of the cluster id
              var box = network.getBoundingBox(ne.nodes[ndx].id);
              var pos = network.getPositions(ne.nodes[ndx].id);

              // mid:
              // get the placement of the cluster id
              // var box = network.getBoundingBox(ne.nodes[ndx].id);
              // var pos = network.getPositions(ne.edges[edx].to);
              // var frompos = network.getPositions(ne.edges[edx].from);
              // var topos = network.getPositions(ne.edges[edx].to);
              // var x = Math.round((frompos[ne.edges[edx].from].x +
              // topos[ne.edges[edx].to].x) /2);
              // var y = Math.round((frompos[ne.edges[edx].from].y +
              // topos[ne.edges[edx].to].y) /2);

              // if y is -ve draw box on the bottom
              if (pos[ne.nodes[ndx].id].y < 0) {

                // To change the color on the rectangle, just manipulate the context
                ctx.strokeStyle = "#00ff00";
                ctx.lineWidth   = 1.5;
                ctx.fillStyle   = "#000000";
                //commonfactory.roundRect(ctx, x-100, y+50, 200, 200,
                // 10, true, true, data); // mid

                commonfactory.roundRect(ctx, box.left - 10, box.top + 75, 200, 200, 10, true, true, data);
              }
              else {

                // To change the color on the rectangle, just manipulate the context
                ctx.strokeStyle = "#00ff00";
                ctx.lineWidth   = 1.5;
                ctx.fillStyle   = "#000000";
                //commonfactory.roundRect(ctx, x-100, y-250, 200, 200,
                // 10, true, true, data);

                commonfactory.roundRect(ctx, box.left - 10, box.top - 225, 200, 200, 10, true, true, data);
              }
            }
          } // for
      }
    }

    // Draw using default border radius,
    // stroke it but no fill (function's default values)
    // graphservice.roundRect(ctx, 5, 5, 50, 50);

    // Manipulate it again
    // ctx.strokeStyle = "#0f0";
    // ctx.fillStyle = "#ddd";

    // Different radii for each corner, others default to 0
    // graphservice.roundRect(ctx, 300, 5, 200, 100, {
    //  tl: 50,
    //  br: 25
    //}, true, true);

  };

  this.display_topology_proc_after_drawing = function (commonfactory, composite, network, ctx) {
    var gs = this;

    // var nodeId    = 'myappcluster';
    // var nodePosition = network.getPositions([nodeId]);

    var topright = network.DOMtoCanvas({x: 25, y: 50});
    commonfactory.display_keys(ctx, topright);

    // Display icons over nodes
    var ne = gs.Model.get_network_composite();

    if (typeof ne === 'undefined')
      return;
    if (typeof ne.nodes === 'undefined')
      return;

    for (var ndx = 0; ndx < ne.nodes.length; ndx ++) {
      if (ne.nodes[ndx].elemtype == "cluster") {
        //console.log(JSON.stringify(ne.nodes[ndx]));
        var nodeId = ne.nodes[ndx].id;
        var nodePosition = network.getPositions([nodeId]);

        // fa-flag-checkered f11e -- non replicated
        if (ne.nodes[ndx].replicated == false) {
          ctx.font = '20px FontAwesome';
          ctx.fillText('\uf11e', nodePosition[nodeId].x - 75, nodePosition[nodeId].y);
        }

        // fa-bomb f1e2 -- critical
        if (ne.nodes[ndx].thermal == 1 &&
          ne.nodes[ndx].thermal_count["1"] > 0) {
          ctx.font = '20px FontAwesome';
          ctx.fillText('\uf1e2', nodePosition[nodeId].x - 50, nodePosition[nodeId].y - 50);

          // critical counts above icon
          ctx.font      = "8pt Arial";
          ctx.fillStyle = "#ffffff";
          ctx.fillText(ne.nodes[ndx].thermal_count["1"], nodePosition[nodeId].x - 50, nodePosition[nodeId].y - 70);
        }

        // see if we have observations for this cluster
        for (var oidx=0; oidx < ne.services_observations_objlist.length; oidx++) {
          var cluster_id = ne.services_observations_objlist[oidx].cluster_id;

          if (cluster_id === ne.nodes[ndx].id) {

            // draw observations
            var observations_map = ne.services_observations_objlist[oidx].observations_map;

            if (typeof observations_map["Hot Spotting"] !== 'undefined') {
              // fa-fire  Unicode: f06d  -- hotspotting
              ctx.font = '20px FontAwesome';
              ctx.fillText('\uf06d', nodePosition[nodeId].x - 20, nodePosition[nodeId].y - 50);

              // hotspotting counts above icon
              ctx.font      = "8pt Arial";
              ctx.fillStyle = "#ffffff";
              ctx.fillText(observations_map["Hot Spotting"], nodePosition[nodeId].x - 20, nodePosition[nodeId].y - 70);
            }

            if (typeof observations_map["Slow Performance"] !== 'undefined') {
              // fa-spinner  Unicode: f110  -- slow/bottleneck
              ctx.font = '20px FontAwesome';
              ctx.fillText('\uf110', nodePosition[nodeId].x + 10, nodePosition[nodeId].y - 50);

              // fa-spinner counts above icon
              ctx.font      = "8pt Arial";
              ctx.fillStyle = "#ffffff";
              ctx.fillText(observations_map["Slow Performance"], nodePosition[nodeId].x + 10, nodePosition[nodeId].y - 70);
            }

            if (typeof observations_map["Non Participation"] !== 'undefined') {
              ctx.font = '20px FontAwesome';
              ctx.fillText('\uf05c', nodePosition[nodeId].x + 10, nodePosition[nodeId].y - 50);

              // fa-spinner counts above icon
              ctx.font      = "8pt Arial";
              ctx.fillStyle = "#ffffff";
              ctx.fillText(observations_map["Non Participation"], nodePosition[nodeId].x + 10, nodePosition[nodeId].y - 70);
            }

          }
        }
      }
    }
  };

  this.display_topology_proc = function (commonfactory, composite) {
    var graphservice = this;

    var network_nodes     = [];
    var edgesarray        = [];
    var network_edges     = {};
    var network_container = {};
    var network_data      = {};
    var network_options   = {};

    var nodesarray = composite.nodes;
    var edgearray  = composite.edges;

    // console.log("nodes " + JSON.stringify(nodesarray));
    // console.log("edges " + JSON.stringify(edgearray));

    network_nodes = new vis.DataSet(nodesarray);
    network_edges = new vis.DataSet(edgearray);

    var parent_width = $('#network').parent().width();
    var parent_height = $('#network').parent().height();

    $('#network').width(parent_width);
    $('#network').height(parent_height);

    // console.log("display_topology_proc width: " +
    // JSON.stringify(parent_width));
    // console.log("display_topology_proc height: " +
    // JSON.stringify(parent_height));

    // create a network
    network_container = $("#network")[0];

    network_data = {
      nodes: network_nodes,
      edges: network_edges
    };

    network_options = commonfactory.get_network_options(composite.nodes);
    var network     = new vis.Network(network_container, network_data, network_options);

    network.setSize(parent_width, parent_height);
    network.fit();
    $("#network").show();
    graphservice.Model.set_network(network);

    network.on("beforeDrawing", function (ctx) {
      graphservice.display_topology_proc_before_drawing(commonfactory, composite, network, ctx);
    });

    network.on("afterDrawing", function (ctx) {
      graphservice.display_topology_proc_after_drawing(commonfactory, composite, network, ctx);
    });

    network.on("click", function (params) {
      console.log("display_topology_proc network clicked");
    });
  };

  this.display_topology = function (commonfactory) {
    var graphservice = this;

    var deployment_id = commonfactory.get_defaults().deployment_id;
    var customer_id   = commonfactory.get_defaults().customer_id;
    var sts           = graphservice.Model.get_curr_marker().sts;
    var ets           = graphservice.Model.get_curr_marker().ets;

    graphservice.get_nodes_and_edges(commonfactory, deployment_id, customer_id, sts, ets, false).then(function (response) {
      var composite = response;

      graphservice.display_topology_proc(commonfactory, composite);

      graphservice.display_stats_datatable(commonfactory);

    }).catch(function (e) {
      console.log("err: get_nodes_and_edges: " + JSON.stringify(e));
    });

  };

  /////////////////////////////////////////
  // Network processing ends             //
  ////////////////////////////////////////

  // create an array of markers
  this.display_markers_proc_create_array = function (commonfactory, m) {
    var minms        = 60000;
    var hourms       = minms * 60;
    var marker_array = [];

    for (var i = 0; i < m.markers.length; i++) {
      // date to the epoch
      var markerdt = new Date(0); // The 0 there is the key, which sets the
      markerdt.setUTCMilliseconds(m.markers[i].start);

      //console.log("display_markers_proc m.markers[i].start " +
      // JSON.stringify(m.markers[i].start));

      //console.log("display_markers_proc m.markers[i].thermal " +
      // JSON.stringify(m.markers[i].thermal));

      var thermalclass = commonfactory.get_vis_thermal_class(m.markers[i].thermal);
      var diff         = m.markers[i].end - m.markers[i].start;

      if (graphservice.Model.get_realtime_mode() === true) {
        // realtime mode

        if (diff <= minms) {
          //console.log("display_markers_proc_create_array: realtime Mode," +
          //             " adding min marker");
          marker_array.push({
                              id: i,
                              'start': markerdt,
                              //'content': '',
                              'className': thermalclass,
                              'sts': m.markers[i].start,
                              'ets': m.markers[i].end,
                              'thermal': m.markers[i].thermal
                            });
        }
      }
      else {
        if (diff >= hourms) {
          //console.log("display_markers_proc_create_array: Manual Mode" +
          //             " adding hour marker");
          marker_array.push({
                              id: i,
                              'start': markerdt,
                              //'content': '',
                              'className': thermalclass,
                              'sts': m.markers[i].start,
                              'ets': m.markers[i].end,
                              'thermal': m.markers[i].thermal
                            });
        }
      }
    }

    var new_scope_markers = {
      marker_array: marker_array,
    };
    //console.log("display_markers_proc_create_array new scope_markers: " +
    //            JSON.stringify(new_scope_markers));

    // update the model with the scope_markers
    graphservice.Model.set_scope_markers(new_scope_markers);

    return marker_array;
  };

  // create options object for timeline
  this.display_markers_proc_create_options = function (commonfactory, m) {
    var hourms = 3600000;
    var minms  = 60000;
    var dayms  = 86400 * 1000;

    var zmin, zmax;
    var mindt = new Date(0);
    var maxdt = new Date(0);

    var gs = this;

    if (gs.Model.get_realtime_mode() === true) {
      console.log("display_marker scope_markers: setting min zoom");
      console.log("display_marker scope_markers: m.start:" + JSON.stringify(m.time_range.start));
      console.log("display_marker scope_markers: Date.now: " + JSON.stringify(Date.now()));

      zmin = (minms) * 15;        // 30  mins
      zmax = (minms) * 30;        // 60  mins

      //mindt.setUTCMilliseconds(marker_array[0].sts - (minms * 10));
      //mindt.setUTCMilliseconds(m.time_range.start - (minms * 2));
      //maxdt.setUTCMilliseconds(maxdt.getMilliseconds()); // time now
      //maxdt = Date.now();

      mindt.setUTCMilliseconds(m.time_range.start - (minms * 2));
      maxdt = Date.now();

      console.log("display_marker scope_markers: mindt: " + mindt.toString());
      console.log("display_marker scope_markers: maxdt: " + maxdt.toString());
    }
    else {
      console.log("display_marker scope_markers: setting hrly zoom");
      zmin = hourms * 12;                // 12hrs in ms
      zmax = ((dayms) * 14 * 1);           // about 1 weeks in ms

      // mindt.setUTCMilliseconds(m.markers[0].start - (dayms * 14));
      // maxdt.setUTCMilliseconds(m.markers[m.markers.length - 1].start
      // + hourms);

      mindt.setUTCMilliseconds(m.time_range.start - dayms);
      maxdt.setUTCMilliseconds(m.time_range.end + (hourms * 4));

      //console.log("display_marker scope_markers: mindt: " +
      // mindt.toString());
      //console.log("display_marker scope_markers: maxdt: " +
      // maxdt.toString());
    }

    var maxht = commonfactory.timeline_height_px() + "px";

    var timeline_options = {
      maxHeight: maxht,
      min: mindt,          // lower limit of visible range
      max: maxdt,          // upper limit of visible range
      zoomMin: zmin,
      zoomMax: zmax
    };

    return timeline_options;
  };

  /**
   * Move the timeline a given percentage to left or right
   * @param {Number} percentage   For example 0.1 (left) or -0.1 (right)
   */
  this.display_markers_proc_move_timeline = function (timeline, percentage) {
    var range    = timeline.getWindow();
    var interval = range.end - range.start;

    timeline.setWindow({
                         start: range.start.valueOf() - interval * percentage,
                         end: range.end.valueOf() - interval * percentage
                       });
  };

  // onselecthandler: update marker and display tree
  this.display_markers_processor_onselect = function (commonfactory) {
    var graphservice = this;

    var timeline = graphservice.Model.get_timeline_obj();

    timeline.on('select', function (properties) {
      var cache         = [];
      var state         = graphservice.Model.get_state();
      var scope_markers = state.scope_markers;
      var marker_array  = scope_markers.marker_array;

      // console.log("display_markers_proc properties: " +
      // commonfactory.stringifyOnce(properties));
      // console.log("display_markers_proc marker_array: " +
      // commonfactory.stringifyOnce(marker_array));

      if (properties === null || typeof properties === 'undefined') return;
      if (properties.items === null || typeof properties.items === 'undefined') return;
      if (properties.items[0] === null || typeof properties.items[0] === 'undefined') return;
      if (properties.items.length <= 0) return;

      if (typeof marker_array[properties.items[0]] === 'undefined') return;
      if (typeof marker_array[properties.items[0]].id === 'undefined') return;
      if (marker_array[properties.items[0]].id === null) return;

      // check if the marker has changed
      var clicked_id = marker_array[properties.items[0]].id;
      var curr_id    = graphservice.Model.get_curr_marker().id;

      if (clicked_id === curr_id) {
        console.log("display_markers_proc clicked id === current id ")
      }
      else {
        graphservice.Model.set_curr_marker(marker_array[properties.items[0]]);
        graphservice.setHeader();
        timeline.focus(timeline.getSelection());

        console.log("display_markers_proc onselect curr marker: " + JSON.stringify(graphservice.Model.get_curr_marker()));
        // console.log("display_markers_proc onselect prev marker: " +
        // JSON.stringify(graphservice.Model.get_prev_marker()));

        graphservice.display_topology(commonfactory);
      }
    });
  };

  // display_markers_proc:
  // get markers
  // create timeline and display tree
  // in realtime mode update timeline
  // init and create new timeline on every mode change

  this.display_markers_proc = function (commonfactory, m) {
    var graphservice = this;

    var timeline_items     = [];
    var timeline_options   = {};
    var timeline_container = {};
    var marker_array       = [];
    var timeline;
    var prev_timeline

    try {
      // console.log("display_markers_proc count: " +
      // JSON.stringify(m.markers.length));
      if (m.markers.length < 0) {
        throw "err: display_markers_proc: no markers found!";
        return;
      }

      // get new or updated marker array
      marker_array = graphservice.display_markers_proc_create_array(commonfactory, m);
      if (marker_array.length <= 0) {
        throw "err: display_markers_proc empty marker_array!";
        return;
      }

      // if toggle is set destroy prev timeline if one exists
      if (graphservice.Model.get_init_timeline_flag()) {
        graphservice.Model.set_init_timeline_flag(false);

        prev_timeline = graphservice.Model.get_timeline_obj();
        if (prev_timeline !== null && typeof prev_timeline !== 'undefined') {
          // destroy prev timeline if existing from older run
          console.log("display_markers_proc destroying prev timeline" +
                      " due since toggle is set");
          prev_timeline.destroy();
          graphservice.Model.set_timeline_obj(null, null);
        }
      }

      // if prev timeline not found create new one else update
      if (typeof graphservice.Model.get_timeline_obj() !== 'undefined' &&
        graphservice.Model.get_timeline_obj() !== null) {

        // update data, timeline should already be created
        timeline       = graphservice.Model.get_timeline_obj();
        timeline_items = graphservice.Model.get_timeline_items_obj();

        if (timeline === null || typeof timeline === 'undefined') {
          console.log("err: display_markers_proc timeline not found **");
          return;
        }
        if (timeline_items === null || typeof timeline_items === 'undefined') {
          console.log("err: display_markers_proc timeline items not found **");
          return;
        }

        timeline_items.clear();

        if (marker_array.length > 0)
          timeline_items.update(marker_array);
        else {
          console.log("err: display_markers_proc no markers found for update_timeline");
        }

        timeline_options = graphservice.display_markers_proc_create_options(commonfactory, m);
        timeline.setOptions(timeline_options);
        //graphservice.display_markers_proc_move_timeline(timeline, -1);

        timeline.setSelection(graphservice.Model.get_curr_marker().id);
        timeline.focus(timeline.getSelection());
        graphservice.setHeader();
        timeline.redraw();

        graphservice.display_topology(commonfactory);
      }
      else { // create new instance

        prev_timeline = graphservice.Model.get_timeline_obj();

        if (prev_timeline !== null && typeof prev_timeline !== 'undefined') {
          // destroy prev timeline if existing from older run
          console.log("display_markers_proc destroying prev timeline ?!");
          prev_timeline.destroy();
        }

        console.log("display_markers_proc creating new timeline");

        // create timeline
        if (marker_array.length > 0)
          timeline_items = new vis.DataSet(marker_array);

        timeline_options   = graphservice.display_markers_proc_create_options(commonfactory, m);
        timeline_container = $('#timeline')[0];

        timeline = new vis.Timeline(timeline_container);
        timeline.setOptions(timeline_options);

        if (marker_array.length > 0)
          timeline.setItems(timeline_items);
        else
          console.log("display_markers_proc: created empty timeline");

        // set obj into model
        graphservice.Model.set_timeline_obj(timeline, timeline_items);

        // Set select event
        graphservice.display_markers_processor_onselect(commonfactory);

        // select the last marker
        if (marker_array.length > 0) {

          // todo: select prev marker if found
          // else
          // select the latest marker and move timeline
          timeline.setSelection(marker_array[marker_array.length - 1].id);
          timeline.focus(marker_array[marker_array.length - 1].id);
          //graphservice.display_markers_proc_move_timeline(timeline, -1);
          timeline.focus(timeline.getSelection());

          // set model to the latest marker
          graphservice.Model.set_curr_marker(marker_array[marker_array.length - 1]);
          graphservice.setHeader();

          graphservice.display_topology(commonfactory);

          // update tree for the current scope
          // graphservice.display_tree(commonfactory, true);

          //console.log("display_markers_proc selectinitial curr marker: "
          // + JSON.stringify(graphservice.Model.get_curr_marker()));
          //console.log("display_markers_proc selectinitial prev marker: "
          // + JSON.stringify(graphservice.Model.get_prev_marker()));
        }
        else {
          // clear current marker
          graphservice.Model.clear_curr_marker();
        }
      } // if (update_timeline === false)
    }
    catch (e) {
      console.log("display_markers_proc err: " + e);
    }
  };

  // get markers based on scope.
  // called from onselect handler of display_tree
  this.display_markers = function (commonfactory) {
    var graphservice = this;

    var scope         = graphservice.Model.get_scope();
    var deployment_id = commonfactory.get_defaults().deployment_id;
    var customer_id   = commonfactory.get_defaults().customer_id;
    var tr            = graphservice.get_timerange_for_mode();

    if (scope === 'deployment') {
      graphservice.getdeploymentmarkers({
                                          "deployment_id": deployment_id,
                                          "customer_id": customer_id,
                                          "sts": tr.sts,
                                          "ets": tr.ets
                                        }).success(function (response) {

        var m = response;

        if (typeof m.markers === 'undefined') {
          console.log("display_markers: markers are undefined!");
          return;
        }

        if (m.markers.length > 0) {     // if there are markers
          graphservice.display_markers_proc(commonfactory, m);
        }
        else {
          console.log("display_markers: deployment no markers found");
          return;
        }
      });
    }
  };

  // show the explorer layout
  this.display_layout = function (commonfactory) {
    var s      = commonfactory.calc_explorer_layout();
    var layout = commonfactory.create_layout(s.ht);

    $('#jqxLayout').jqxLayout({
                                resizable: true,
                                width: '100%',
                                height: s.ht,
                                layout: layout,
                              });
  };

  this.Model = {

    state: {
      curr_marker: "",
      prev_marker: "",            // todo: remove
      curr_graph_node: "",        // todo: remove

      realtimemode: false,
      timelineon: false,

      selectedcharts: [],
      active_windows: {},

      scope_markers: {},
      scope: "",

      init_timeline: false,

      obj_timeline: {
        timeline: {},
        timeline_items: {}
      },

      obj_datatable: {},
      obj_network: {},

      network_composite: {},
    },
  };

  this.Model.init = function () {
    this.state.curr_marker        = "";
    this.state.prev_marker        = "";
    this.state.prev_explorer_node = "";

    if (typeof this.state.selectedcharts !== 'undefined') {
      delete this.state.selectedcharts;
    }
    if (typeof this.state.active_windows !== 'undefined') {
      delete this.state.active_windows;
    }
    if (typeof this.state.obj_datatable !== 'undefined' && this.state.obj_datatable !== null) {
      console.log("Model init deleted datatable");
      delete this.state.obj_datatable;
    }
    if (typeof this.state.obj_timeline !== 'undefined' && this.state.obj_timeline !== null) {
      console.log("Model init deleted timeline");
      delete this.state.obj_timeline.timeline;
      delete this.state.obj_timeline.timeline_items;
      delete this.state.obj_timeline;
    }
  },

  this.Model.set_scope = function (scope) {
    this.state.scope = scope;
  },

  this.Model.get_scope = function () {
    return this.state.scope;
  },

  this.Model.get_network = function () {
    return this.state.obj_network;
  },

  this.Model.set_network = function (obj) {
    if (typeof this.state.obj_network === 'undefined') {
      this.state.obj_network = {};
    }
    this.state.obj_network = obj;
  },

  this.Model.set_network_composite = function (obj) {
    if (typeof this.state.network_composite === 'undefined') {
      this.state.network_composite = {};
    }
    this.state.network_composite = obj;
  },
  this.Model.get_network_composite = function () {
    return this.state.network_composite;
  },

  // timeline object ///////////////////////////

  this.Model.set_timeline_obj = function (timeline, timeline_items) {
    if (typeof this.state.obj_timeline === 'undefined') {
      this.state.obj_timeline                = {};
      this.state.obj_timeline.timeline       = {};
      this.state.obj_timeline.timeline_items = {};
    }
    this.state.obj_timeline.timeline       = timeline;
    this.state.obj_timeline.timeline_items = timeline_items;
  };

  this.Model.get_timeline_obj = function () {
    if (typeof this.state.obj_timeline === 'undefined' ||
      typeof this.state.obj_timeline === null) {
      return null;
    }
    else return this.state.obj_timeline.timeline;
  };

  this.Model.get_timeline_items_obj = function () {
    if (typeof this.state.obj_timeline === 'undefined' ||
      typeof this.state.obj_timeline === null) {
      return null;
    }
    else return this.state.obj_timeline.timeline_items;
  };

  // datatable object ///////////////////////////

  this.Model.set_datatable_obj = function (obj) {
    if (typeof this.state.obj_datatable === 'undefined') {
      this.state.obj_datatable = {};
    }
    this.state.obj_datatable = obj;
  };

  this.Model.get_datatable_obj = function (obj) {
    return this.state.obj_datatable;
  };

  // flags ///////////////////////////

  this.Model.set_init_timeline_flag = function (b) {
    this.init_timeline = b;
  };

  this.Model.get_init_timeline_flag = function () {
    return this.init_timeline;
  };


  // realtime mode  ///////////////////////////

  this.Model.enable_realtime_mode = function () {
    this.realtimemode = true;
    sessionStorage.realtimemode = this.realtimemode;
  };

  this.Model.disable_realtime_mode = function () {
    this.realtimemode = false;
    sessionStorage.realtimemode = this.realtimemode;
  };

  this.Model.get_realtime_mode = function () {
    return this.realtimemode;
  };

  // timeline  ///////////////////////////

  this.Model.show_timeline = function () {
    this.timelineon = true;
    sessionStorage.timelineon = this.timelineon;
  };

  this.Model.hide_timeline = function () {
    this.timelineon = false;
    sessionStorage.timelineon = this.timelineon;
  };

  this.Model.is_timeline_on = function () {
    return this.timelineon;
  };

  // chartviewer window handling  ///////////////////////////

  this.Model.add_activewindow = function (k, v) {
    this.active_windows[k] = v;
  };

  this.Model.del_activewindow = function (k) {
    console.log("deleting: " + k);
    delete this.active_windows[k];
  };

  this.Model.is_activewindow = function (k) {
    if (typeof this.active_windows === 'undefined') {
      this.active_windows = {};
      return false;
    }

    if (k in this.active_windows)
      return true;
    else
      return false;
  };

  this.Model.set_activewindow_focus = function (k) {
    var win = this.active_windows[k];
    win.focus();
  };

  this.Model.add_selectedcharts = function (strindex, data) {
    var obj      = {};
    obj.rowindex = strindex;
    obj.data     = data;

    this.state.selectedcharts.push(obj);
  };

  this.Model.delete_selectedcharts = function (strindex) {

    for (var i = 0; i < this.state.selectedcharts.length; i++) {
      if (this.state.selectedcharts[i].rowindex === strindex) {
        this.state.selectedcharts.splice(i, 1);
      }
    }
    // delete this.state.selectedcharts[strindex];
  };

  this.Model.delete_allselectedcharts = function () {
    this.state.selectedcharts        = [];
    this.state.selectedcharts.length = 0;
  };

  // chartviewer window handling ends ///////////////////////////

  this.Model.set_scope_markers = function (obj) {
    this.state.scope_markers = obj;
  };
  this.Model.get_scope_markers = function () {
    return this.state.scope_markers;
  };

  // markers  ///////////////////////////

  this.Model.set_curr_marker = function (obj) {
    if (typeof this.state.curr_marker === 'undefined') {
      this.state.prev_marker = obj;
      this.state.curr_marker = obj;
    }
    else {
      this.state.prev_marker = this.state.curr_marker;
      this.state.curr_marker = obj;
    }
  };
  this.Model.get_curr_marker = function () {
    return this.state.curr_marker;
  };
  this.Model.get_prev_marker = function () {
    return this.state.prev_marker;
  };

  this.Model.clear_curr_marker = function () {
    if (typeof this.state.curr_marker !== 'undefined') {
      this.state.prev_marker = "";
      this.state.curr_marker = "";
    }
  };

  this.Model.set_curr_graph_node = function (obj) {
    if (typeof this.state.curr_graph_node === 'undefined') {
      this.state.curr_graph_node = obj;
    }
    else {
      this.state.curr_graph_node = obj;
    }
  };
  this.Model.get_curr_graph_node = function () {
    return this.state.curr_graph_node;
  };

  // state
  this.Model.get_state = function () {
    return this.state;
  };


  this.get_timerange_for_mode = function () {
    var graphservice = this;

    var momentutcets;
    var momentutcsts;
    var sts;
    var ets;

    if (graphservice.Model.get_realtime_mode() === true) {
      // get the current hour's markers (2 mins behind)
      var momentutc_sts = moment.utc().startOf('minute').subtract(63, "minutes");
      var momentutc_ets = moment(momentutc_sts).add(60, "minutes");
      var sts           = momentutc_sts.valueOf();
      var ets           = momentutc_ets.valueOf();
    }
    else {
      // hourly - set the sts and ets to 2 weeks back

      // get the start of hour and substract 1 hr
      momentutcets = moment.utc().startOf('hour').subtract(1, "hours");
      momentutcsts = moment(momentutcets).subtract(14, "days");

      // add min to make it inclusive of the hour
      momentutcets = moment(momentutcets).add(1, "minutes");

      sts          = momentutcsts.valueOf();
      ets          = momentutcets.valueOf();

      // console.log("get_timerange_for_mode sts: " + sts);
      // console.log("get_timerange_for_mode ets: " + ets);
    }

    return {
      sts: sts,
      ets: ets
    };
  };

  // realtime processing, display markers on each interval
  // display_markers will update the tree thermals and counts
  // and reselect the prev node
  this.process_rt = function (commonfactory, b_refresh) {
    graphservice.display_markers(commonfactory);
  };
  
  // unchecked_handler = on
  this.realtimebutton_unchecked_handler = function (commonfactory) {
    var graphservice = this;
    var timerId;
    var minms = 60000;

    graphservice.Model.set_init_timeline_flag(true);
    graphservice.Model.enable_realtime_mode();

    // enable clock
    var b_refresh = true;
    timerId       = setInterval(function () { graphservice.process_rt(commonfactory, b_refresh) }, minms);
    return timerId;
  };

  // checked_handler = off
  this.realtimebutton_checked_handler = function (commonfactory, timerId) {
    var graphservice = this;
    graphservice.Model.disable_realtime_mode();

    clearInterval(timerId);
    console.log("Enabled Manual Mode");

    graphservice.Model.set_init_timeline_flag(true);
    graphservice.display_markers(commonfactory);
  };

  this.timelinebutton_checked_handler = function (commonfactory) {
    var graphservice = this;
    var layout = commonfactory.calc_explorer_layout();
    var timeline_ht = commonfactory.timeline_height_px();
    var ht = layout.ht + timeline_ht;

    // console.log("checked_handler: errorgrid_ht + timeline_ht: " +
    // JSON.stringify(ht));
    graphservice.Model.hide_timeline();

    $("#jqxLayout").animate({height: ht+"px"});
    $("#timeline").hide();

    var network = graphservice.Model.get_network();
    if (typeof network !== 'undefined' && network !== null) {
      var parent_width = $('#graphpanelinner').width();
      var parent_height = ht;

      //console.log("timelinebutton_checked_handler resize width: " +
      // JSON.stringify(parent_width));
      //console.log("timelinebutton_checked_handler resize height: " +
      // JSON.stringify(parent_height));

      if (typeof network.setSize !== 'undefined') {
        network.setSize(parent_width-3, parent_height-31);
        $("#network").animate({height: ht-31 + "px"});
        network.redraw();
        network.fit();
      }
      else
        console.log("timelinebutton_checked_handler: network.setSize is" +
                    " undefined !");
    }

    $(window).trigger('resize');
  };

  // unchecked_handler = on
  this.timelinebutton_unchecked_handler = function (commonfactory) {
    var graphservice = this;
    var layout = commonfactory.calc_explorer_layout();

    // console.log("unchecked_handler: layout_ht:  " +
    // JSON.stringify(layout.ht));
    graphservice.Model.show_timeline();

    $("#jqxLayout").animate({height: layout.ht+"px"});
    $("#timeline").show();

    var network = graphservice.Model.get_network();
    if (typeof network !== 'undefined' && network !== null) {
      var parent_width = $('#graphpanelinner').width();
      var parent_height = layout.ht;

      // console.log("timelinebutton_checked_handler resize width: " +
      // JSON.stringify(parent_width));
      // console.log("timelinebutton_checked_handler resize height: " +
      // JSON.stringify(parent_height));

      if (typeof network.setSize !== 'undefined') {
        network.setSize(parent_width-3, parent_height-31);
        $("#network").animate({height: layout.ht-31 + "px"});
        network.redraw();
        network.fit();
      }
      else
        console.log("timelinebutton_unchecked_handler: network.setSize" +
                    " is undefined !");
    }

    $(window).trigger('resize');
  };

  this.button_event_handlers = function (commonfactory) {
    var graphservice = this;
    var timerId = 0;

    $('.jqx-switchbutton').on('unchecked', function (event) {
      if (event.target.id === 'realtimebutton')
        timerId = graphservice.realtimebutton_unchecked_handler(commonfactory);
      if (event.target.id === 'timelinebutton')
        timerId = graphservice.timelinebutton_unchecked_handler(commonfactory);
    });

    $('.jqx-switchbutton').on('checked', function (event) {
      if (event.target.id === 'realtimebutton')
        return graphservice.realtimebutton_checked_handler(commonfactory, timerId);
      if (event.target.id === 'timelinebutton')
        timerId = graphservice.timelinebutton_checked_handler(commonfactory);
    });
  };

  this.initstorage = function(commonfactory) {
    graphservice = this;

    var height = 20;
    var width = 60;

    if (typeof(Storage) !== "undefined") {

      if (typeof sessionStorage.timelineon === 'undefined') {
        // show timeline
        graphservice.Model.show_timeline();
        $('#timelinebutton').jqxSwitchButton({
                                               height: height,
                                               width: width,
                                               checked: true
                                             });
        console.log("graph_controller init: timeline initialized")
      }
      else {
        // timelineon
        if (sessionStorage.timelineon === 'true') {
          // show timeline
          graphservice.Model.show_timeline();
          $('#timelinebutton').jqxSwitchButton({
                                                 height: height,
                                                 width: width,
                                                 checked: true
                                               });
          graphservice.timelinebutton_unchecked_handler(commonfactory);
          console.log("graph_controller init: timeline enabled");
        }
        else {
          graphservice.Model.hide_timeline();
          $('#timelinebutton').jqxSwitchButton({
                                                 height: height,
                                                 width: width,
                                                 checked: false
                                               });
          graphservice.timelinebutton_checked_handler(commonfactory);
          console.log("graph_controller init: timeline disabled")
        }
      }

      if (typeof sessionStorage.realtimemode === 'undefined') {

        // create button with realtime set to on
        graphservice.Model.enable_realtime_mode();
        $('#realtimebutton').jqxSwitchButton({
                                               height: height,
                                               width: width,
                                               checked: true
                                             });
        console.log("graph_controller init: realtime mode initialized")

      }
      else {

        if (sessionStorage.realtimemode === 'true') {
          graphservice.Model.enable_realtime_mode();
          $('#realtimebutton').jqxSwitchButton({
                                                 height: height,
                                                 width: width,
                                                 checked: true
                                               });
          console.log("graph_controller init: realtime mode enabled")
        }
        else {
          graphservice.Model.disable_realtime_mode();
          $('#realtimebutton').jqxSwitchButton({
                                                 height: height,
                                                 width: width,
                                                 checked: false
                                               });
          console.log("graph_controller init: realtime mode disabled")
        }
      }
    }
    else {
      console.log("Sessionstorage support is unavailable")
    }
  };

}]); // App.Service


app.controller('graph_controller', ['$scope', 'commonfactory', 'graphservice', '$window', function ($scope, commonfactory, graphservice, $window) {

  $(window).resize(function () {
  });

  $(document).ready(function () {

    // init model
    graphservice.Model.init();

    // init realtime and timeline buttons
    graphservice.initstorage(commonfactory);

    // Load fonts (one time)
    /*
    Highcharts.createElement('link', {
      href: 'https://fonts.googleapis.com/css?family=Unica+One',
      rel: 'stylesheet',
      type: 'text/css'
    }, null, document.getElementsByTagName('head')[0]);
   */

    graphservice.button_event_handlers(commonfactory);

    // starting scope
    graphservice.Model.set_scope('deployment');

    graphservice.display_markers(commonfactory);

    graphservice.display_layout(commonfactory);

    $('#myCarousel').carousel({interval: 5000});

    // hook up buttons
    $('#myCarousel').click(function () {
      $('#homeCarousel').carousel('cycle');
    });
    $('#myCarousel').click(function () {
      $('#homeCarousel').carousel('pause');
    });
  }).error(function (error) {
    console.log("err: onReady: " + error);
  });
}]);

