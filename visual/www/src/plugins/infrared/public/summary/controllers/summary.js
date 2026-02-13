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

//import 'datatables.net-bs/css/dataTables.bootstrap.css'
//import 'datatables.net-bs/js/dataTables.bootstrap.js';

//import 'datatables.net-buttons-bs/css/buttons.bootstrap.css'
//import 'datatables.net-buttons-bs/js/buttons.bootstrap.js';

//import 'datatables.net-select';
//import 'datatables.net-select-dt/css/select.dataTables.css';
//import 'datatables.net-select-bs/css/select.bootstrap.css';

import 'jqwidgets-framework/jqwidgets/styles/jqx.base.css';
import jqwidgets from 'jqwidgets-framework/jqwidgets/jqx-all.js';

import VisProvider from 'ui/vis';
import vis from 'vis/dist/vis.min.js';
import 'vis/dist/vis.min.css';

import 'jstree/dist/themes/default/style.min.css'
import 'jstree/dist/jstree.min.js'
import 'plugins/infrared/summary/controllers/_summary';
import uiRoutes from 'ui/routes';
import uiModules from 'ui/modules';
import chrome from 'ui/chrome';

import 'plugins/infrared/summary/styles/summary.css';  // load after vis

import summary_template from 'plugins/infrared/summary/index.html';

const app = uiModules.get('apps/summary', []);

uiRoutes
  .when('/summary', {
    template: summary_template
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
    //var scope        = graphservice.get_current_scope();

    // Todo: Update with current scope

    $("#explorefixedheadertext").html("Current Selection [From: "
                                      + startTimeStr + " -"
                                      + " To: "
                                      + endTimeStr + "]"
                                      + " Scope: " + scope);
  };

  this.hashCode = function (str) {
    var hash = 0, i, chr, len;
    if (str.length === 0) return hash;
    for (i = 0, len = str.length; i < len; i++) {
      chr  = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + chr;
      hash |= 0; // Convert to 32bit integer
    }
    return hash;
  };

  this.openWindow = function () {

    var selectedcharts = graphservice.Model.get_state().selectedcharts;

    var rowarray = [];
    for (var i = 0; i < selectedcharts.length; i++) {
      rowarray.push(selectedcharts[i].rowindex);
    }
    rowarray     = rowarray.sort();
    var hashstr  = rowarray.toString();
    var hashcode = this.hashCode(hashstr).toString();

    // check if this hash exists if so just do a win.focus

    if (graphservice.Model.is_activewindow(hashcode)) {
      console.log("found active window!");

      graphservice.Model.set_activewindow_focus(hashcode);
    }
    else {
      // add window
      console.log("Did not find active window!");

      var win = $window.open("http://www.nuvidata.com:5601/app/infrared#/graph/chart?id=1", hashcode, "width=200,height=100");
      // console.log("openWindow: " +
      // JSON.stringify(graphservice.Model.get_state().selectedcharts));

      win.selectedcharts     = graphservice.Model.get_state().selectedcharts;
      win.sts                = graphservice.Model.get_curr_marker().sts;
      win.ets                = graphservice.Model.get_curr_marker().ets;
      win.curr_explorer_node = graphservice.Model.get_curr_explorer_node();

      this.Model.add_activewindow(hashcode, win);
      console.log("name: " + win.name);

      win.onbeforeunload = function () {
        console.log("onunload: " + this.name);
        graphservice.Model.del_activewindow(this.name);
      };
    }
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

  ////////////////////////////////////////
  // REST Access Layer Ends             //
  ///////////////////////////////////////

  // returns markers corresponding to scope
  this.get_markers = function (commonfactory, deployment_id, customer_id, cluster_id, node_id, sts, ets) {
    var gs = this;

    var scope = gs.Model.get_scope();

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

  // returns composite obj with node snapshot and metrics for
  // errortable and summary charts
  this.get_snapshot_for_node = function (commonfactory, deployment_id, customer_id, cluster_id, node_id, sts, ets) {
    var gs = this;

    var scope = gs.Model.get_scope();
    if (scope === 'node') {

      return new Promise(function (resolve, reject) {
        var getnodesnapshotfn = gs.getnodesnapshot;

        if (typeof getnodesnapshotfn === "undefined") {
          console.log("err: get_snapshot_for_node:" +
                      " getnodesnapshotfn is undefined");
        }

        getnodesnapshotfn({
                            "deployment_id": deployment_id,
                            "customer_id": customer_id,
                            "cluster_id": cluster_id,
                            "node_id": node_id,
                            "sts": sts,
                            "ets": ets
                          }).success(function (response) {
          var nodesnapshot = response;
          var composite    = {};

          if ($.isEmptyObject(nodesnapshot) == false) {
            // console.log("get_snapshot_for_node " +
            // JSON.stringify(nodesnapshot));
            var node_metrics  = commonfactory.create_chart_metrics(nodesnapshot.stats);
            composite.errors  = nodesnapshot;
            composite.metrics = node_metrics;
            resolve(composite);
          }
          else {
            console.log("err: get_snapshot_for_node" +
                        " nodesnapshot is empty for " + JSON.stringify(cluster_id));
            reject(composite);
          }
        }).error(function (e) {
          console.log("get_snapshot_for_node err: " + e);
          reject(e);
        });
      })
    }
    else {
      console.log("err: get_snapshot_for_node invalid scope");
    }
  };

  // returns composite obj with cluster snapshot and metrics for
  // errortable and summary charts
  this.get_snapshot_for_cluster = function (commonfactory, deployment_id, customer_id, cluster_id, sts, ets) {
    var gs = this;

    var scope = gs.Model.get_scope();
    if (scope === 'cluster') {

      return new Promise(function (resolve, reject) {
        var getclustersnapshotfn = gs.getclustersnapshot;

        if (typeof getclustersnapshotfn === "undefined") {
          console.log("err: get_snapshot_for_cluster:" +
                      " getclustersnapshotfn is undefined");
        }

        getclustersnapshotfn({
                               "deployment_id": deployment_id,
                               "customer_id": customer_id,
                               "cluster_id": cluster_id,
                               "sts": sts,
                               "ets": ets
                             }).success(function (response) {
          var clustersnapshot = response;
          var composite       = {};

          if ($.isEmptyObject(clustersnapshot) == false) {
            // console.log("get_snapshot_for_cluster " +
            // JSON.stringify(clustersnapshot));

            var cluster_metrics = commonfactory.create_chart_metrics(clustersnapshot.stats);
            composite.errors    = clustersnapshot;
            composite.metrics   = cluster_metrics;
            resolve(composite);
          }
          else {
            console.log("err: get_snapshot_for_cluster" +
                        " clustersnapshot is empty for " + JSON.stringify(cluster_id));

            // add to composite
            reject(composite);
          }
        }).error(function (e) {
          console.log("get_snapshot_for_cluster err: " + e);
          reject(e);
        });
      })
    }
    else {
      console.log("err: get_snapshot_for_cluster invalid scope");
    }
  };

  // returns composite obj with deployment snapshot and metrics for
  // errortable and summary charts
  this.get_snapshot_for_deployment = function (commonfactory, deployment_id, customer_id, sts, ets) {
    var gs = this;

    var scope = gs.Model.get_scope();
    if (scope === 'deployment') {

      return new Promise(function (resolve, reject) {
        var getdeploymentsnapshotfn = gs.getdeploymentsnapshot;

        if (typeof getdeploymentsnapshotfn === "undefined") {
          console.log("err: get_snapshot_for_deployment:" +
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
            // console.log("get_snapshot_for_deployment " +
            // JSON.stringify(deploymentsnapshot));

            var deployment_metrics = commonfactory.create_chart_metrics(deploymentsnapshot.stats);

            composite.errors  = deploymentsnapshot;
            composite.metrics = deployment_metrics;

            resolve(composite);
          }
          else {
            console.log("err: get_snapshot_for_deployment" +
                        " deploymentsnapshot is empty for " + JSON.stringify(deployment_id));
            composite.deploymentsnapshot = deploymentsnapshot;
            reject(composite);
          }
        }).error(function (e) {
          console.log("get_snapshot_for_deployment err: " + e);
          reject(e);
        });
      })
    }
    else {
      console.log("err: get_snapshot_for_deployment invalid scope");
    }
  };

  // returns composite obj with sub-tree of nodes for cluster_id
  this.get_tree_for_cluster = function (commonfactory, deployment_id, customer_id, cluster_id, sts, ets) {
    var graphservice = this;

    var scope = graphservice.Model.get_scope();

    if (scope === 'cluster') {
      return new Promise(function (resolve, reject) {
        graphservice.getclusterdetail({
                                        "deployment_id": deployment_id,
                                        "customer_id": customer_id,
                                        "cluster_id": cluster_id,
                                        "sts": sts,
                                        "ets": ets
                                      }).success(function (response) {
          var clusterdetail = response;
          var tree          = [];
          var composite     = {};

          // console.log("get_tree_for_cluster: " +
          // JSON.stringify(clusterdetail));
          // console.log("get_tree_for_cluster: cluster length "
          // + clusterdetail.nodeDetailsList.length);

          if (typeof clusterdetail.nodeDetailsList === 'undefined'
            || clusterdetail.nodeDetailsList === null) {
            console.log("err processclusterdetail nodeDetailsList is null");
            return;
          }

          for (var cindex = 0; cindex < clusterdetail.nodeDetailsList.length; cindex++) {

            var icon_str        = commonfactory.get_icon_for_tree("desktop",
                                                                  clusterdetail.nodeDetailsList[cindex].thermal);
            var name_prefix_str = commonfactory.get_tree_name_prefix(clusterdetail.nodeDetailsList[cindex].thermal_count)
              + clusterdetail.nodeDetailsList[cindex].host_name;

            // Push the nodes
            tree.push({
                        //"id":
                        // clusterdetail.nodeDetailsList[cindex].host_ip,
                        "id": "192.168.6.21",       // todo: fix backend
                        "parent": cluster_id,
                        "text": name_prefix_str,
                        "type": "node",
                        "thermal": clusterdetail.thermal,
                        "icon": icon_str,
                      });
          }
          composite["tree"] = tree;
          resolve(composite);
        }).error(function (e) {
          console.log("err: get_tree_for_cluster: " + err);
          reject(e);
        });
      });
    }
    else {
      console.log("err: get_tree_for_cluster invalid scope");
    }
  };

  // returns composite obj with tree of nodes upto cluster level
  this.get_tree_for_deployment = function (commonfactory, deployment_id, customer_id, sts, ets) {
    var graphservice = this;

    var scope = graphservice.Model.get_scope();

    if (scope === 'deployment' || scope === 'cluster') {  // details upto cluster level

      return new Promise(function (resolve, reject) {
        console.log("get_tree_for_deployment"
                    + " deployment_id: " + JSON.stringify(deployment_id)
                    + " customer_id: " + JSON.stringify(customer_id)
                    + " sts: " + JSON.stringify(sts)
                    + " ets: " + JSON.stringify(ets));

        var tree      = [];
        var composite = {};

        graphservice.getdeploymentdetail({
                                           "deployment_id": deployment_id,
                                           "customer_id": customer_id,
                                           "sts": sts,
                                           "ets": ets
                                         }).success(function (response) {
          var deploymentdetail = response;

          //console.log("get_tree_for_deployment: deploymentdetail: " +
          // JSON.stringify(deploymentdetail));
          //console.log("get_tree_for_deployment: cluster length " +
          // deploymentdetail.clusterDetailsList.length);

          var deployment_id = deploymentdetail.deployment_id;
          var customer_id   = deploymentdetail.customer_id;
          var cluster_id    = deploymentdetail.clusterDetailsList[0].cluster_id;

          var icon_str        = commonfactory.get_icon_for_tree("deployment", deploymentdetail.thermal);
          var name_prefix_str = commonfactory.get_tree_name_prefix(deploymentdetail.thermal_count) + deploymentdetail.name;

          // Push the root for explorer
          tree.push({
                      "id": deployment_id,
                      "parent": "#",
                      "text": name_prefix_str,
                      "type": "deployment",
                      "icon": icon_str,
                    });

          // walk clusters
          for (var dindex = 0; dindex < deploymentdetail.clusterDetailsList.length; dindex++) {

            // push each cluster to nodes
            // console.log("get_tree_for_deployment role: ",
            // JSON.stringify(deploymentdetail.clusterDetailsList[dindex].role));

            // Push each cluster to explorer
            var icon_str        = commonfactory.get_icon_for_tree(deploymentdetail.clusterDetailsList[dindex].role,
                                                                  deploymentdetail.clusterDetailsList[dindex].thermal);
            var name_prefix_str = commonfactory.get_tree_name_prefix(deploymentdetail.clusterDetailsList[dindex].thermal_count)
              + deploymentdetail.clusterDetailsList[dindex].name;

            //console.log("cluster: " + JSON.stringify(icon_str) + " "
            // + JSON.stringify(name_prefix_str));

            tree.push({
                        "id": deploymentdetail.clusterDetailsList[dindex].cluster_id,
                        "parent": deployment_id,
                        "text": name_prefix_str,
                        "type": "cluster",
                        "thermal": deploymentdetail.clusterDetailsList[dindex].thermal,
                        "icon": icon_str,
                      });
          } // for

          composite["tree"] = tree;
          resolve(composite);
        }).catch(function (e) {
          console.log("err get_tree_for_deployment getdeploymentdetail: " + e);
          reject(e);
        });

      }).catch(function (e) {
        console.log("err get_tree_for_deployment: " + e);
        reject(e);
      });
    }
    else {
      console.log("err: get_tree_for_deployment invalid scope");
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

  this.processclustersnapshot = function (deployment_id, customer_id, cluster_id, sts, ets) {
    var gs = this;

    return new Promise(function (resolve, reject) {
      var getclustersnapshotfn = gs.getclustersnapshot;

      if (typeof getclustersnapshotfn === "undefined") {
        console.log("err: getclustersnapshotfn:" +
                    " getclustersnapshotfn is undefined");
      }

      getclustersnapshotfn({
                             "deployment_id": deployment_id,
                             "customer_id": customer_id,
                             "cluster_id": cluster_id,
                             "sts": sts,
                             "ets": ets
                           }).success(function (response) {
        var clustersnapshot = response;
        var edges           = [];
        var composite       = {};

        if ($.isEmptyObject(clustersnapshot) == false) {
          // console.log("processclustersnapshot: clustersdetail " +
          // JSON.stringify(clustersnapshot));

          if (clustersnapshot.connections.length > 0) {
            for (var cindex = 0; cindex < clustersnapshot.connections.length; cindex++) {
              edges.push({
                           from: cluster_id,
                           to: clustersnapshot.connections[cindex].to.cluster_id,
                           arrows: "to",
                           width: clustersnapshot.connections[cindex].avglatency / 40,
                           "elemtype": "cluster"
                         });
            }

            // console.log("processclustersnapshot edges: " +
            // JSON.stringify(edges));
            composite.edges = edges;
            resolve(composite);
          }
          else {
            // console.log("processclustersnapshot: clustersdetail" +
            // " length is 0" + " empty for " + cluster_id);
            composite.edges = edges;
            resolve(composite); // todo: Check this
          }
        }
        else {
          // console.log("processclustersnapshot: clustersdetail is" +
          // " empty for " + deployment_id);
          composite.edges = edges;
          reject(composite); // todo: Check this
        }
      }).error(function (e) {
        console.log("processclustersnapshot err: " + e);
        reject(e);
      });
    })
  };

  this.processclusterdetail = function (deployment_id, customer_id, cluster_id, sts, ets) {
    var gs = this;

    return new Promise(function (resolve, reject) {
      gs.getclusterdetail({
                            "deployment_id": deployment_id,
                            "customer_id": customer_id,
                            "cluster_id": cluster_id,
                            "sts": sts,
                            "ets": ets
                          }).success(function (response) {
        var clusterdetail = response;
        var nodes         = [];
        var edges         = [];
        var tree          = [];
        var composite     = {};

        // console.log("processclusterdetail: " +
        // JSON.stringify(clusterdetail));
        // console.log("processclusterdetail: cluster length "
        // + clusterdetail.nodeDetailsList.length);

        /*
         if (typeof clusterdetail === 'undefined' || clusterdetail === null)
         {
         console.log("err processclusterdetail clusterdetail is null"); return;
         }

         if (typeof clusterdetail.nodeDetailsList === 'undefined' || clusterdetail.nodeDetailsList === null)
         {
         console.log("err processclusterdetail nodeDetailsList is null"); return;
         }*/

        for (var cindex = 0; cindex < clusterdetail.nodeDetailsList.length; cindex++) {
          nodes.push({
                       "id": clusterdetail.nodeDetailsList[cindex].host_name,
                       "label": clusterdetail.nodeDetailsList[cindex].host_name,
                       "group": "sm" + clusterdetail.role
                     });

          // Push the nodes
          tree.push({
                      "id": clusterdetail.nodeDetailsList[cindex].host_name,
                      "parent": cluster_id,
                      "text": clusterdetail.nodeDetailsList[cindex].host_name,
                      //"value":""
                      "type": "node",
                      "thermal": clusterdetail.thermal,
                    });

          // Simulate connection from each node to its cluster
          edges.push({
                       from: clusterdetail.nodeDetailsList[cindex].host_name,
                       to: clusterdetail.cluster_id,
                       arrows: "from",
                       width: 1
                     });
        }
        composite.nodes = nodes;
        composite.edges = edges;
        composite.tree  = tree;

        resolve(composite);
      }).error(function (e) {
        console.log("err: getclusterdetailfn: " + err);
        reject(e);
      });
    });
  };

  this.update_model_for_deployment_full = function (commonfactory, deployment_id, customer_id, sts, ets, displaydeployment) {
    var gs = this;

    return new Promise(function (resolve, reject) {

      // console.log("update_model_for_deployment_full" +
      //            " deployment_id: " + JSON.stringify(deployment_id)
      // + " customer_id: " + JSON.stringify(customer_id)
      //            + " sts: " + JSON.stringify(sts)
      //            + " ets: " + JSON.stringify(ets));

      var nodes              = [];
      var edges              = [];
      var tree               = [];
      var deploymentsnapshot = {};
      var composite          = {};

      var promises = [];

      // Push the root for explorer
      tree.push({
                  "id": deployment_id,
                  "parent": "#",
                  "text": deployment_id,
                  //"value":deployment_id,
                  "type": "deployment"
                });

      gs.getdeploymentdetail({
                               "deployment_id": deployment_id,
                               "customer_id": customer_id, "sts": sts,
                               "ets": ets
                             }).success(function (response) {
        var deploymentdetail = response;

        // console.log("update_model_for_deployment_full:" +
        // " deploymentdetail" + JSON.stringify(deploymentdetail));
        // console.log("update_model_for_deployment_full:" + "
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
                       "elemtype": "deployment",
                       "thermal": deploymentdetail.thermal
                     });

          // connect the deployment id to the last cluster
          edges.push({
                       "from": deployment_id,
                       "to": deploymentdetail.clusterDetailsList[deploymentdetail.clusterDetailsList.length - 1].cluster_id,
                       "arrows": "to",
                       "width": 6,
                       "elemtype": "deployment"
                     });
        }

        // get deployment snapshot (One time call hence this does not
        // need to be in the for loop)
        promises.push(gs.processdeploymentsnapshot(deployment_id, customer_id, sts, ets, displaydeployment));

        // walk clusters
        for (var dindex = 0; dindex < deploymentdetail.clusterDetailsList.length; dindex++) {

          // push each cluster to nodes
          // console.log("update_model_for_deployment_full role:" +
          // " ",
          // JSON.stringify(deploymentdetail.clusterDetailsList[dindex].role));

          nodes.push({
                       "id": deploymentdetail.clusterDetailsList[dindex].cluster_id,
                       "label": deploymentdetail.clusterDetailsList[dindex].name,
                       "group": deploymentdetail.clusterDetailsList[dindex].role,
                       "elemtype": "cluster",
                       "thermal": deploymentdetail.clusterDetailsList[dindex].thermal,
                     });

          // Push each cluster to explorer
          tree.push({
                      "id": deploymentdetail.clusterDetailsList[dindex].cluster_id,
                      "parent": deployment_id,
                      "text": deploymentdetail.clusterDetailsList[dindex].name,
                      //"value":"",
                      "type": "cluster",
                      "thermal": deploymentdetail.thermal
                    });

          deployment_id = deploymentdetail.deployment_id;
          customer_id   = deploymentdetail.customer_id;
          cluster_id    = deploymentdetail.clusterDetailsList[dindex].cluster_id;
          sts           = gs.Model.get_curr_marker().sts;
          ets           = gs.Model.get_curr_marker().ets;

          // todo : Move to update_cluster
          // add connections for each cluster into edges
          promises.push(gs.processclustersnapshot(deployment_id, customer_id, cluster_id, sts, ets));

          // todo: Move to update node
          // get nodes for each cluster
          promises.push(gs.processclusterdetail(deployment_id, customer_id, cluster_id, sts, ets));
        } // for

        // update nodes from all clusters also fill edges array with
        // simulated connections
        Promise.all(promises).then(function (result) {
          //console.log("update_model_for_deployment_full promise.all
          // result" +
          //  " " + JSON.stringify(result));

          // console.log("getclusterdetailpromises: All promises
          // length " + JSON.stringify(result.length));
          // console.log("getclusterdetailpromises: All promises " +
          // JSON.stringify(result));

          for (var i = 0; i < result.length; i++) {
            if (typeof result[i].nodes !== 'undefined') {
              // console.log("promises: nodes" + JSON.stringify(nodes));
              for (var j = 0; j < result[i].nodes.length; j++) {
                nodes.push(result[i].nodes[j]);
                // console.log("update_model_for_deployment_full promises.all:
                // nodes[j].id" +
                // JSON.stringify(nodes[j].id));
              }
            }

            if (typeof result[i].edges !== 'undefined') {
              // console.log("update_model_for_deployment_full promises.all:
              // edges" + JSON.stringify(edges));
              for (var j = 0; j < result[i].edges.length; j++) {
                edges.push(result[i].edges[j]);
              }
            }

            if (typeof result[i].deploymentsnapshot !== 'undefined') {
              deploymentsnapshot = result[i].deploymentsnapshot;
            }

            if (typeof result[i].tree !== 'undefined') {
              for (var j = 0; j < result[i].tree.length; j++) {
                tree.push(result[i].tree[j]);
                //console.log("update_model_for_deployment_full tree array
                // pushed" + JSON.stringify(result[i].tree[j]));
              }
            }
          }

          // walk the deploymentsnapshot get counts for overview
          // charting

          //console.log("update_model_for_deployment_full" +
          //" deploymentsnapshot.stats: " +
          // JSON.stringify(deploymentsnapshot.stats));
          var deployment_metrics = commonfactory.create_chart_metrics(deploymentsnapshot.stats);
          // console.log("update_model_for_deployment_full" +
          // "deployment_metrics: " + JSON.stringify(deployment_metrics));

          composite = {
            nodes: nodes,
            edges: edges,
            tree: tree,
            //deploymentsnapshot: deploymentsnapshot  // asit
            errors: deploymentsnapshot
          };

          //console.log("update_model_for_deployment_full final composite:
          // " + JSON.stringify(composite));

          // update the model
          gs.Model.set_metrics(deployment_metrics);

          resolve(composite);

        }).catch(function (e) {
          console.log("err update_model_for_deployment_full: some promise" +
                      " failed: " + e);
        });
      }).error(function (e) {
        console.log("err update_model_for_deployment_full deploymentdetail: " + e);
      });
    }).catch(function (e) {
      console.log("err update_model_for_deployment_full: " + e);
      reject(e);
    });
  };

  this.display_entire_explorer = function (b_refresh, commonfactory, deployment_id, customer_id, sts, ets) {
    var graphservice = this;

    graphservice.getdeploymentmarkers({
                                        "deployment_id": deployment_id,
                                        "customer_id": customer_id,
                                        "sts": sts,
                                        "ets": ets
                                      }).success(function (response) {

      var m = response;

      // if in realtime node, we need to override the refresh first time
      // in order to draw a new timeline with min zoom when we have data
      if (graphservice.Model.get_realtime_mode() === true) {
        if (m.markers.length > 0) {

          var init_timeline_in_rt = graphservice.Model.get_init_timeline_flag();
          if (init_timeline_in_rt) {
            console.log("display_entire_explorer got set,refresh override");
            b_refresh = false; // override
          }
        }
      }
      if (b_refresh === false) {
        var refresh = false;    // new object creation
      }
      else {
        refresh = true;         // marker update
      }

      console.log("display_entire_explorer: b_refresh: " + JSON.stringify(b_refresh));

      if (m.markers.length > 0) {     // if there are markers
        graphservice.display_markers_proc(commonfactory, m, refresh);

        // timeline initialization is over after first marker set is
        // applied. Disable flag
        graphservice.Model.set_init_timeline_flag(false);
      }
      else {
        console.log("display_entire_explorer: no markers found");
        // graphservice.display_markers_proc(commonfactory, m, false);
        // since there are no markers found, return
        return;
      }

      // Get graph for the specific marker and scope (the first marker in
      // this case with the deployment scope)

      var deployment_id = "ec2-dc-01";
      var customer_id   = "1234abcd";
      var sts           = graphservice.Model.get_curr_marker().sts;
      var ets           = graphservice.Model.get_curr_marker().ets;

      var displaydeployment = false;
      graphservice.update_model_for_deployment_full(commonfactory, deployment_id, customer_id, sts, ets, displaydeployment).then(function (response) {
        var composite = response;

        graphservice.display_overview(commonfactory, composite);
        if (graphservice.Model.get_realtime_mode() === true) {
          //graphservice.display_errors_datatable(commonfactory,
          // composite, true); asit
        }
        else {
          //graphservice.display_errors_datatable(commonfactory,
          // composite, b_refresh); asit
        }

        var selectinitial = true;
        var refresh       = false;    // used on tree creation

        if (b_refresh === true) {
          selectinitial = true;
          refresh       = true;    // used on tree updates
        }

        //graphservice.display_tree(commonfactory, false);
        // asit:
        //graphservice.display_tree(composite, selectinitial, refresh);

        // display stacked_bar
        // graphservice.display_error_summary_chart(commonfactory,
        // composite); asit

      }).catch(function (e) { // getdeploymentmarkers
        console.log("err: update_model_for_deployment_full: " + JSON.stringify(e));
      });

    }).error(function (error) {
      console.log("err: display_entire_explorer: " + error);
    });
  };

  /////////////////////////////////////////
  // Network processing ends             //
  ////////////////////////////////////////

  this.display_overview = function (commonfactory, composite) {
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

    $('#network').width($('#network').parent().width() - 5);
    $('#network').height($('#network').parent().height() - 3);

    // create a network
    network_container = $("#network")[0];

    network_data = {
      nodes: network_nodes,
      edges: network_edges
    };

    network_options = commonfactory.get_network_options(composite.nodes);
    var network     = new vis.Network(network_container, network_data, network_options);

    /*
     network.setOptions({
     nodes: {shadow: true},
     edges: {shadow: true}
     });

     nodes.clear();
     edges.clear();
     nodes.add(nodesarray);
     edges.add(edgesarray);
     */

    network.fit();
    $("#network").show();

    network.on("click", function (params) {
      console.log("display_overview network clicked");
    });

  };

  this.display_errors_datatable_create_rows = function (errors) {
    var dr = [];

    if (errors.stats.length <= 0) {
      var row = [];
      for (var i = 0; i < 17; i++) {
        row.push("");
      }
      dr.push(row);
    }
    else {
      for (var i = 0; i < errors.stats.length; i++) {
        var row = [];
        row.push(i.toString());
        row.push(errors.stats[i].contribution.rank);
        row.push(errors.stats[i].thermal);
        row.push(errors.stats[i].anomaly_type);
        row.push(errors.stats[i].plugin);
        row.push(errors.stats[i].target);
        row.push(errors.stats[i].classification);
        row.push(errors.stats[i].tags.message);
        row.push(errors.stats[i].deployment_id);
        row.push(errors.stats[i].customer_id);
        row.push(errors.stats[i].cluster_id);
        row.push(errors.stats[i].thermal);
        row.push(errors.stats[i].host_ip);
        row.push(errors.stats[i].host_name);
        row.push(errors.stats[i].anomaly_class);
        row.push(JSON.stringify(errors.stats[i].ws));
        row.push(JSON.stringify(errors.stats[i].tags));

        /*
         console.log("display_errors_datatable row:" + JSON.stringify(row));
         */

        dr.push(row);
      }
    }
    return dr;
  };

  this.display_errors_datatable = function (commonfactory, composite, refresh) {
    var gs = this;

    var d = [];
    //var errors = composite.deploymentsnapshot; // todo:access based asit
    // on scope
    var errors        = composite.errors;
    var table;
    var rows_selected = []; // Array holding selected row IDs

    var calc_width = function () {
      return $('#grid').parent().width() - 10;
    };

    var calc_height = function () {
      return $('#grid').parent().height() - 10;
    };

    //console.log("display_errors_datatable pw: " +
    // JSON.stringify(calc_width()));
    //console.log("display_errors_datatable ph: " +
    // JSON.stringify(calc_height()));

    d = gs.display_errors_datatable_create_rows(errors);

    //console.log("display_errors_datatable: " + JSON.stringify(d));

    var update_size = function () {
      $('#grid').width(calc_width());
      $('#grid').height(calc_height());

      var t = $.fn.dataTable.fnTables();
      if (t.length > 0) {
        setTimeout(function () {
          for (var i = 0; i < t.length; i++) {
            $(t[i]).dataTable().fnAdjustColumnSizing();
          }
        }, 200);
      }
    };

    var oldt = gs.Model.get_datatable_obj();
    if (typeof oldt !== 'undefined' && oldt !== null) {
      //console.log("display_errors_datatable: found existing instance");
      table = oldt;
      table.clear();
      if (d.length > 0) {
        table.rows.add(d);
        table.draw();
      }
    }
    else {
      // console.log("display_errors_datatable: creating new table");

      table = $('#grid').DataTable({
                                     dom: 'fli<"datatablebuttonpadding">Bpt',

                                     buttons: [
                                       {
                                         text: 'View Chart',
                                         action: function (e, dt, n, config) {
                                           // console.log("rows_selected: " +
                                           // JSON.stringify(rows_selected));

                                           gs.Model.delete_allselectedcharts();

                                           for (var i = 0; i < rows_selected.length; i++) {

                                             var r = dt.rows(rows_selected[i]).data();
                                             // console.log("rows_selected: " +
                                             // commonfactory.stringifyOnce(r["0"]));
                                             // console.log("rows_selected: " +
                                             // commonfactory.stringifyOnce(r[i.toString()]));

                                             var row               = {};
                                             row["rank"]           = r["0"][1];
                                             row["sev"]            = r["0"][2];
                                             row["anomaly_type"]   = r["0"][3];
                                             row["plugin"]         = r["0"][4];
                                             row["target"]         = r["0"][5];
                                             row["classification"] = r["0"][6];
                                             row["message"]        = r["0"][7];

                                             row["deployment_id"] = r["0"][8];
                                             row["customer_id"]   = r["0"][9];
                                             row["cluster_id"]    = r["0"][10];
                                             row["thermal"]       = r["0"][11];
                                             row["host_ip"]       = r["0"][12];
                                             row["host_name"]     = r["0"][13];
                                             row["anomaly_class"] = r["0"][14];
                                             row["ws"]            = r["0"][15];
                                             row["tags"]          = r["0"][16];

                                             //console.log("row :" + commonfactory.stringifyOnce(row));
                                             gs.Model.add_selectedcharts(i, row);
                                           }

                                           if (rows_selected.length > 0) {
                                             gs.openWindow();
                                           }
                                         }
                                       }
                                     ],

                                     pageLength: 5,
                                     "lengthMenu": [[5, 10, 25, 50, -1], [5, 10, 25, 50, "All"]],

                                     select: {
                                       style: 'multi'
                                     },

                                     data: d,
                                     columnDefs: [
                                       {
                                         'targets': 0,
                                         'searchable': false,
                                         'orderable': false,
                                         'width': '1%',
                                         'className': 'dt-body-center',
                                         'render': function (data, type, full, meta) {
                                           return '<input type="checkbox">';
                                         },
                                         visible: true,
                                       },
                                       {
                                         width: "5%",
                                         "targets": 1,
                                         visible: true,
                                       },
                                       {
                                         width: "5%",
                                         "targets": 2,
                                         visible: true
                                       },
                                       {
                                         width: "10%",
                                         "targets": 3,
                                         visible: true
                                       },
                                       {
                                         width: "5%",
                                         "targets": 4,
                                         visible: true
                                       },
                                       {
                                         width: "5%",
                                         "targets": 5,
                                         visible: true
                                       },
                                       {
                                         width: "10%",
                                         "targets": 6,
                                         visible: true
                                       },
                                       {
                                         width: "60%",
                                         "targets": 7,
                                         visible: true
                                       },
                                       {targets: '_all', visible: false},
                                     ],

                                     'order': [[1, 'asc']],
                                     'rowCallback': function (row, data, dataIndex) {
                                       // Get row ID
                                       var rowId = data[0];

                                       // If row ID is in the list of selected row IDs
                                       if ($.inArray(rowId, rows_selected) !== -1) {
                                         $(row).find('input[type="checkbox"]').prop('checked', true);
                                         $(row).addClass('selected');
                                       }
                                     },

                                     "sScrollY": false,
                                     "sScrollX": false,
                                     "autoWidth": false,
                                   }); // DataTable init

      // set instance into model
      gs.Model.set_datatable_obj(table);
    }

    $('#grid tbody').on('click', 'input[type="checkbox"]', function (e) {
      var $row = $(this).closest('tr');

      // Get row data
      var data = table.row($row).data();

      // Get row ID
      var rowId = data[0];

      // Determine whether row ID is in the list of selected row IDs
      var index = $.inArray(rowId, rows_selected);

      // If checkbox is checked and row ID is not in list of selected row IDs
      if (this.checked && index === -1) {
        rows_selected.push(rowId);

        // Otherwise, if checkbox is not checked and row ID is in list of selected row IDs
      }
      else if (!this.checked && index !== -1) {
        rows_selected.splice(index, 1);
      }

      if (this.checked) {
        $row.addClass('selected');
      }
      else {
        $row.removeClass('selected');
      }

      // Update state of "Select all" control
      commonfactory.updateDataTableSelectAllCtrl(table);

      // Prevent click event from propagating to parent
      e.stopPropagation();
    });

    // Handle click on table cells with checkboxes
    $('#grid').on('click', 'tbody td, thead th:first-child', function (e) {
      $(this).parent().find('input[type="checkbox"]').trigger('click');
    });

    // Handle click on "Select all" control
    $('thead input[name="select_all"]', table.table().container()).on('click', function (e) {
      if (this.checked) {
        $('#grid tbody input[type="checkbox"]:not(:checked)').trigger('click');
      }
      else {
        $('#grid tbody input[type="checkbox"]:checked').trigger('click');
      }

      // Prevent click event from propagating to parent
      e.stopPropagation();
    });

    // Handle table draw event
    table.on('draw', function () {
      // Update state of "Select all" control
      commonfactory.updateDataTableSelectAllCtrl(table);
    });

    $(window).resize(function () {
      update_size();
    });
  };

  // Stacked chart
  this.create_chart_plugin_class_ws_by_ts = function (commonfactory, carousel_footer_offset, id) {
    var graphservice = this;

    var w = $('#myCarousel').width();
    var h = $('#myCarousel').height();

    var deployment_stats = graphservice.Model.get_metrics();
    var result_obj       = deployment_stats["distrib_by_rank"]["result_obj"];
    // console.log("create_chart_plugin_class_ws_by_ts: result_obj" +
    // JSON.stringify(result_obj));

    var title  = "WS Distribution";
    var ytitle = "Time";
    var reflow = true;
    /*
     var heatmap_options = {

     chart: {
     type: 'heatmap',
     renderTo: id,
     reflow: reflow,
     width: w,
     height: h,
     marginTop: 40,
     marginBottom: 80,
     plotBorderWidth: 1
     },


     title: {
     text: 'Sales per employee per weekday'
     },

     xAxis: {
     categories: ['Alexander', 'Marie', 'Maximilian', 'Sophia', 'Lukas', 'Maria', 'Leon', 'Anna', 'Tim', 'Laura']
     },

     yAxis: {
     categories: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'],
     title: null
     },

     colorAxis: {
     min: 0,
     minColor: '#FFFFFF',
     maxColor: Highcharts.getOptions().colors[0]
     },

     legend: {
     align: 'right',
     layout: 'vertical',
     margin: 0,
     verticalAlign: 'top',
     y: 25,
     symbolHeight: 280
     },

     tooltip: {
     formatter: function () {
     return '<b>' + this.series.xAxis.categories[this.point.x] + '</b> sold <br><b>' +
     this.point.value + '</b> items on <br><b>' + this.series.yAxis.categories[this.point.y] + '</b>';
     }
     },

     series: [{
     name: 'Sales per employee',
     borderWidth: 1,
     data: [[0, 0, 10], [0, 1, 19], [0, 2, 8], [0, 3, 24], [0, 4, 67], [1, 0, 92], [1, 1, 58], [1, 2, 78], [1, 3, 117], [1, 4, 48], [2, 0, 35], [2, 1, 15], [2, 2, 123], [2, 3, 64], [2, 4, 52], [3, 0, 72], [3, 1, 132], [3, 2, 114], [3, 3, 19], [3, 4, 16], [4, 0, 38], [4, 1, 5], [4, 2, 8], [4, 3, 117], [4, 4, 115], [5, 0, 88], [5, 1, 32], [5, 2, 12], [5, 3, 6], [5, 4, 120], [6, 0, 13], [6, 1, 44], [6, 2, 88], [6, 3, 98], [6, 4, 96], [7, 0, 31], [7, 1, 1], [7, 2, 82], [7, 3, 32], [7, 4, 30], [8, 0, 85], [8, 1, 97], [8, 2, 123], [8, 3, 64], [8, 4, 84], [9, 0, 47], [9, 1, 114], [9, 2, 31], [9, 3, 48], [9, 4, 91]],
     dataLabels: {
     enabled: true,
     color: '#000000'
     }
     }]

     };

     var heatmap_chart   = new Highcharts.Chart(heatmap_options);
     */

  };

  // column chart (Rank Distribution)
  this.create_chart_distrib_by_rank = function (commonfactory, carousel_footer_offset, id) {
    var graphservice = this;

    var w = $('#myCarousel').width();
    var h = $('#myCarousel').height();

    //console.log("create_chart_distrib_by_rank pre-w " +
    // JSON.stringify(w));
    //console.log("create_chart_distrib_by_rank pre-h " +
    // JSON.stringify(h));

    var stats      = graphservice.Model.get_metrics();
    var result_obj = stats["distrib_by_rank"]["result_obj"];

    //console.log("create_chart_distrib_by_rank: result_obj" +
    // JSON.stringify(result_obj));

    var top_count = result_obj.categories.length;
    var title     = "Anomaly Distribution by Top " + top_count + " Ranks"
    var ytitle    = "Rank";
    var reflow    = true;

    // console.log("create_chart_distrib_by_rank: stats" +
    // JSON.stringify(stats));

    if (result_obj.series.length > 0) {
      var col_options = commonfactory.get_column_chart_opt(id,
                                                           reflow,
                                                           w - 1,
                                                           h - carousel_footer_offset,
                                                           title, ytitle,
                                                           result_obj.categories,
                                                           result_obj.series);
      var col_chart   = new Highcharts.Chart(col_options);
    }
    else {
      // no data
      console.log("err: create_chart_distrib_by_rank no data");
      return;
    }
  };

  // pie chart (plugin distrubution by share)
  this.create_chart_plugin_class_by_share = function (commonfactory, carousel_footer_offset, id) {
    var graphservice = this;

    var w = $('#myCarousel').width();
    var h = $('#myCarousel').height();

    //var deployment_stats = graphservice.Model.get_metrics();
    // console.log("create_chart_plugin_class_by_share: deployment_stats" +
    // JSON.stringify(deployment_stats));
    //var result_obj =
    // commonfactory.distrib_by_rank_create_series(deployment_stats["distrib_by_rank"]);
    // console.log("create_chart_plugin_class_by_share: result_obj" +
    // JSON.stringify(result_obj));

    var reflow = true;
    var title  = "pie";
    var series = [{
      name: 'Brands',
      colorByPoint: true,
      data: [{
        name: 'Microsoft Internet Explorer',
        y: 56.33
      }, {
        name: 'Chrome',
        y: 24.03,
        sliced: true,
        selected: true
      }, {
        name: 'Firefox',
        y: 10.38
      }, {
        name: 'Safari',
        y: 4.77
      }, {
        name: 'Opera',
        y: 0.91
      }, {
        name: 'Proprietary or Undetectable',
        y: 0.2
      }]
    }];

    var pie_options = commonfactory.get_pie_chart_opt(id, reflow, w - 1,
                                                      h - carousel_footer_offset,
                                                      title,
                                                      series);
    var pie         = new Highcharts.Chart(pie_options);
  };

  // display error table summaries for scope. Scope data is loaded into
  // composite
  this.display_error_summary_chart = function (commonfactory, composite) {
    var graphservice           = this;
    var carousel_footer_offset = 35;   // spacer for carousel indicators

    // common options for all charts
    Highcharts.setOptions(commonfactory.get_highcharts_theme());
    Highcharts.setOptions(commonfactory.get_highcharts_plotoptions());
    Highcharts.setOptions(commonfactory.get_highcharts_utc());

    graphservice.create_chart_distrib_by_rank(commonfactory, carousel_footer_offset, 'error_overview_c1');
    graphservice.create_chart_plugin_class_by_share(commonfactory, carousel_footer_offset, 'error_overview_c2');
    graphservice.create_chart_plugin_class_ws_by_ts(commonfactory, carousel_footer_offset, 'error_overview_c3');

    // set event
    $(window).bind('resize', function () {
      console.log("display_error_summary_chart highcharts: resize");

      if (typeof $("#myCarousel") !== 'undefined') {
        var c1 = $("#error_overview_c1").highcharts();
        if (typeof c1 !== 'undefined') {
          c1.destroy();
          graphservice.create_chart_distrib_by_rank(commonfactory, carousel_footer_offset, 'error_overview_c1');
        }

        var c2 = $("#error_overview_c2").highcharts();
        if (typeof c2 !== 'undefined') {
          c2.destroy();
          graphservice.create_chart_plugin_class_by_share(commonfactory, carousel_footer_offset, 'error_overview_c2');
        }

        var c3 = $("#error_overview_c3").highcharts();
        if (typeof c3 !== 'undefined') {
          c3.destroy();
          graphservice.create_chart_plugin_class_ws_by_ts(commonfactory, carousel_footer_offset, 'error_overview_c3');
        }
      }
    }).trigger('resize');
  };

  // returns string with id of parent node
  this.get_parent_nodeid = function (obj) {
    var t = $.jstree.reference('#jstreeContainer');

    if (typeof t !== 'undefined' && t !== null) {
      return t.get_parent(obj);
    }
    return ""
  };

  // Returns currently selected node id
  this.get_current_nodeid = function () {
    var t = $.jstree.reference('#jstreeContainer');

    if (typeof t !== 'undefined' && t !== null) {
      var selected = t.get_selected();
      console.log("get_current_nodeid: " + JSON.stringify(selected));
      return selected;
    }
    return [];
  };

  this.display_tree_proc_add_subtree = function (commonfactory, cluster_id) {
    var graphservice = this;
    var t            = $.jstree.reference('#jstreeContainer');

    // populate sub tree
    var deployment_id = commonfactory.get_defaults().deployment_id;
    var customer_id   = commonfactory.get_defaults().customer_id;
    var sts           = graphservice.Model.get_curr_marker().sts;
    var ets           = graphservice.Model.get_curr_marker().ets;

    // get cluster and sub nodes
    graphservice.get_tree_for_cluster(commonfactory, deployment_id, customer_id, cluster_id, sts, ets).then(function (response) {
      console.log("display_tree_proc onselect" +
                  " get_tree_for_cluster " + JSON.stringify(response));
      for (var k = 0; k < response.tree.length; k++) {
        t.create_node(cluster_id, response.tree[k], 'last', function () {});
      }

      t.open_node(cluster_id, function () {
        var jqchildren = t.get_children_dom(cluster_id);
        console.log("display_tree_proc_bind_onselect after adding" +
                    " children: " + JSON.stringify(jqchildren));
      });

    }).catch(function (e) {
      console.log("err: display_tree_proc_bind_onselect onselect" +
                  " get_tree_for_cluster: " + JSON.stringify(e));
    });
  };

  // display_tree: onload event
  // select prev node, load sub tree for nodes if cluster or node is
  // selected
  // load corresponding error and summary chart data
  // open and select the prev node

  this.display_tree_proc_onloaded = function (commonfactory, composite) {
    var graphservice = this;

    $('#jstreeContainer').on('loaded.jstree', function () {

      var t = $.jstree.reference('#jstreeContainer');

      //var scope                 = graphservice.get_current_scope();
      var scope         = graphservice.Model.get_scope();
      var deployment_id = commonfactory.get_defaults().deployment_id;
      var customer_id   = commonfactory.get_defaults().customer_id;
      var sts           = graphservice.Model.get_curr_marker().sts;
      var ets           = graphservice.Model.get_curr_marker().ets;

      // todo: select the prev explorer node if available
      var prev_explorer_node = graphservice.Model.get_prev_explorer_node();

      if (prev_explorer_node === "") {
        console.log("display_tree_proc_onloaded selected root: " + JSON.stringify(composite.tree[0]));
        graphservice.Model.set_prev_explorer_node(composite.tree[0].id);
        prev_explorer_node = graphservice.Model.get_prev_explorer_node();
      }

      console.log("display_tree_proc_onloaded" +
                  " prev_explorer_node: " + JSON.stringify(prev_explorer_node) +
                  " scope " + scope);

      graphservice.setHeader();

      // get errors and metrics objects for scope
      if (scope === 'deployment') {
        var deployment_id = prev_explorer_node;
        graphservice.get_snapshot_for_deployment(commonfactory, deployment_id, customer_id, sts, ets).then(function (response) {
          console.log("display_tree_proc_onloaded" +
                      " deployment snapshot");
          graphservice.display_errors_datatable(commonfactory, response, true);

          t.select_node(deployment_id, true);
          t.open_node(deployment_id, function () {}); // open cluster

          // set metrics into model
          graphservice.Model.set_metrics(response.metrics);
          graphservice.display_error_summary_chart(commonfactory);

        }).catch(function (e) {
          console.log("err: display_tree_proc_onloaded: " + JSON.stringify(e));
        });
      }
      if (scope === 'cluster') {
        var cluster_id = prev_explorer_node;
        t.deselect_all(true);

        graphservice.get_snapshot_for_cluster(commonfactory, deployment_id, customer_id, cluster_id, sts, ets).then(function (response) {
          console.log("display_tree_proc_onloaded" +
                      " cluster snapshot");// + JSON.stringify(response));

          graphservice.display_errors_datatable(commonfactory, response, true);

          graphservice.display_tree_proc_add_subtree(commonfactory, cluster_id);
          t.select_node(cluster_id, true);
          t.open_node(cluster_id, function () {}); // open cluster

          // set metrics into model for summary
          graphservice.Model.set_metrics(response.metrics);
          graphservice.display_error_summary_chart(commonfactory);

        }).catch(function (e) {
          console.log("err: display_tree_proc_onloaded: " + JSON.stringify(e));
        });
      }
      if (scope === 'node') {
        var node_id    = prev_explorer_node;
        var cluster_id = graphservice.get_parent_nodeid(node_id);

        console.log("display_tree_proc_onloaded node_id: " + JSON.stringify(node_id) + " cluster_id: " + JSON.stringify(cluster_id));

        graphservice.get_snapshot_for_node(commonfactory, deployment_id, customer_id, cluster_id, node_id, sts, ets).then(function (response) {
          console.log("display_tree_proc_onloaded" +
                      " node snapshot" + JSON.stringify(response));

          graphservice.display_errors_datatable(commonfactory, response, true);

          graphservice.display_tree_proc_add_subtree(commonfactory, cluster_id);
          t.open_node(cluster_id, function () {}); // open cluster
          t.select_node(node_id, true);

          // set metrics into model
          graphservice.Model.set_metrics(response.metrics);
          graphservice.display_error_summary_chart(commonfactory);

        }).catch(function (e) {
          console.log("err: display_tree_proc_onloaded: " + JSON.stringify(e));
        });
      }

    });
  };

  // display_tree: onselect event
  // load markers for selected scope

  this.display_tree_proc_bind_onselect = function (commonfactory, composite) {
    var graphservice = this;

    $('#jstreeContainer').on('select_node.jstree', function (e, data) {
      var i, j;
      var t = $.jstree.reference('#jstreeContainer');

      //var scope                 = graphservice.get_current_scope();
      var scope        = graphservice.Model.get_scope();
      var selected_obj = data.instance.get_node(data.selected[0]).original;

      graphservice.Model.set_scope(selected_obj.type);
      console.log("display_tree_proc_bind_onselect set scope:" + graphservice.Model.get_scope());

      graphservice.setHeader();

      // set selected node into model
      graphservice.Model.set_prev_explorer_node(selected_obj.id);

      console.log("display_tree_proc_bind_onselect selected node: " + JSON.stringify(selected_obj) +
                  " scope " + JSON.stringify(scope));

      // if this is event triggered from the marker update, disable
      // flag and continue
      if (graphservice.Model.is_init_tree()) {
        console.log("display_tree_proc_bind_onselect disabled init flag ");
        graphservice.Model.disable_init_tree();
        return;
      }

      // get deployment markers for scope, marker select will reload tree
      // reselect previous node with corresponding errors and
      // summary charts
      if (scope === 'deployment' || scope === 'cluster' || scope === 'node') {
        graphservice.display_markers(commonfactory);
      }
    });
  };

  // display_tree_proc:
  // reload tree and bind load and select events
  // init tree flag is used to prevent select event from running on
  // load

  this.display_tree_proc = function (commonfactory, composite, refresh) {
    var graphservice = this;

    var treedata = {
      'core': {
        "check_callback": true,  // required for create_node!
        'multiple': false,
        'data': composite.tree,
      }
    };

    // console.log("display_tree_proc treedata: " +
    // JSON.stringify(treedata))

    // marker update
    var t = $.jstree.reference('#jstreeContainer');

    console.log("display_tree_proc: marker update");

    if (typeof t !== 'undefined' && t !== null) {
      $('#jstreeContainer').jstree().destroy();
    }

    // update tree
    $('#jstreeContainer').jstree(treedata);

    // enable init flag to avoid select event
    graphservice.Model.enable_init_tree();

    // populate sub tree and reselect prev selection
    // display grid and charts
    graphservice.display_tree_proc_onloaded(commonfactory, composite);

    // display markers based on scope on select
    graphservice.display_tree_proc_bind_onselect(commonfactory, composite);
  };

  this.display_tree = function (commonfactory, refresh) {
    var graphservice  = this;
    var scope         = graphservice.Model.get_scope();
    //var scope         = graphservice.get_current_scope();
    var deployment_id = commonfactory.get_defaults().deployment_id;
    var customer_id   = commonfactory.get_defaults().customer_id;
    var sts           = graphservice.Model.get_curr_marker().sts;
    var ets           = graphservice.Model.get_curr_marker().ets;

    console.log("display_tree: current markers: " + JSON.stringify(sts) + " " + JSON.stringify(ets));
    console.log("display_tree: scope: " + JSON.stringify(scope));

    if (scope === 'deployment' || scope === 'cluster') {

      // get root and all sub clusters
      graphservice.get_tree_for_deployment(commonfactory, deployment_id, customer_id, sts, ets).then(function (response) {
        console.log("display_tree get_tree_for_deployment " + JSON.stringify(response));

        graphservice.display_tree_proc(commonfactory, response, refresh);

      }).catch(function (e) {
        console.log("err: display_tree: " + JSON.stringify(e));
      });
    }
  };

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
                              'content': '.',
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
                              'content': '.',
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
      // console.log("display_marker scope_markers: setting hrly zoom");
      zmin = hourms;                // one 1hr in ms
      zmax = ((dayms) * 4 * 1); // about 1 weeks in ms

      // mindt.setUTCMilliseconds(m.markers[0].start - (dayms * 14));
      // maxdt.setUTCMilliseconds(m.markers[m.markers.length - 1].start
      // + hourms);

      mindt.setUTCMilliseconds(m.time_range.start - dayms);
      maxdt.setUTCMilliseconds(m.time_range.end + hourms);

      //console.log("display_marker scope_markers: mindt: " +
      // mindt.toString());
      //console.log("display_marker scope_markers: maxdt: " +
      // maxdt.toString());
    }

    var maxht = commonfactory.timeline_height_px() + "px";

    var timeline_options = {
      //width: '100%',
      //height: '124px',
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

        console.log("display_markers_proc onselect curr marker: " + JSON.stringify(graphservice.Model.get_curr_marker()));
        console.log("display_markers_proc onselect prev marker: " + JSON.stringify(graphservice.Model.get_prev_marker()));

        // update tree for the current scope
        graphservice.display_tree(commonfactory, true);
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
    var update_timeline    = false;
    var timeline;

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

      // see if we need to create a new timeline or update the
      // existing one

      if (graphservice.Model.get_realtime_mode() === true) {
        // update timeline in rt mode
        update_timeline         = true;
        var init_timeline_in_rt = graphservice.Model.get_init_timeline_flag();
        if (init_timeline_in_rt) {
          console.log("display_markers_proc got set, update override");
          graphservice.Model.set_init_timeline_flag(false);
          update_timeline = false;
        }
      }

      if (update_timeline === true) {
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
        graphservice.display_markers_proc_move_timeline(timeline, -1);
        timeline.setSelection(graphservice.Model.get_curr_marker().id);
        timeline.redraw();
      }
      else { // create new instance

        var prev_timeline = graphservice.Model.get_timeline_obj();

        // console.log("display_markers_proc !update_timeline getting timeline: " +
        // commonfactory.stringifyOnce(prev_timeline));

        if (prev_timeline !== null && typeof prev_timeline !== 'undefined') {
          // destroy prev timeline if existing from older run
          console.log("display_markers_proc destroying prev timeline");
          prev_timeline.destroy();
        }

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
          graphservice.display_markers_proc_move_timeline(timeline, -1);

          // set model to the latest marker
          graphservice.Model.set_curr_marker(marker_array[marker_array.length - 1]);

          // update tree for the current scope
          graphservice.display_tree(commonfactory, true);

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
    var current_node  = graphservice.get_current_nodeid();
    var deployment_id = commonfactory.get_defaults().deployment_id;
    var customer_id   = commonfactory.get_defaults().customer_id;
    var tr            = graphservice.get_timerange_for_mode();

    if (typeof current_node === 'undefined' || current_node === null) {
      console.log("err: display_markers: current_node not found!");
      return;
    }

    console.log("display_markers scope: " +
                JSON.stringify(scope) + " current_node " +
                JSON.stringify(current_node));

    if (scope === 'deployment') {
      graphservice.getdeploymentmarkers({
                                          "deployment_id": deployment_id,
                                          "customer_id": customer_id,
                                          "sts": tr.sts,
                                          "ets": tr.ets
                                        }).success(function (response) {

        var m = response;

        if (m.markers.length > 0) {     // if there are markers
          graphservice.display_markers_proc(commonfactory, m);
        }
        else {
          console.log("display_markers: deployment no markers found");
          return;
        }
      });
    }

    if (scope === 'cluster') {
      // get currently selected cluster
      console.log("current_node: " + current_node);
      var cluster_id = current_node;
      graphservice.getclustermarkers({
                                       "deployment_id": deployment_id,
                                       "customer_id": customer_id,
                                       "cluster_id": cluster_id,
                                       "sts": tr.sts,
                                       "ets": tr.ets
                                     }).success(function (response) {

        var m = response;

        if (m.markers.length > 0) {     // if there are markers
          graphservice.display_markers_proc(commonfactory, m);
        }
        else {
          console.log("display_markers: cluster no markers found");
          return;
        }

      });
    }

    if (scope === 'node') {
      // get currently selected node

      var cluster_id = graphservice.get_parent_nodeid(current_node);
      var node_id    = current_node;

      console.log("display_markers current_node: " + current_node + " cluster_id "
                  + JSON.stringify(cluster_id));

      console.log("display_markers getnodemarkers deployment: " + deployment_id +
                  customer_id + " cluster " + cluster_id + " node " + node_id);

      console.log("display_markers getnodemarkers sts: " + tr.sts + " ets " + tr.ets);

      graphservice.getnodemarkers({
                                    "deployment_id": deployment_id,
                                    "customer_id": customer_id,
                                    "cluster_id": cluster_id,
                                    "node_id": node_id,
                                    "sts": tr.sts,
                                    "ets": tr.ets
                                  }).success(function (response) {

        var m = response;

        console.log("display_markers getnodemarkers" + JSON.stringify(m));

        if (m.markers.length > 0) {     // if there are markers
          graphservice.display_markers_proc(commonfactory, m);
        }
        else {
          console.log("display_markers: node no markers found");
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
                                layout: layout
                              });
  };

  this.Model = {

    state: {
      curr_marker: "",
      prev_marker: "",            // todo: remove
      curr_explorer_node: "",     // todo: remove
      prev_explorer_node: "",
      curr_graph_node: "",        // todo: remove

      realtimemode: false,

      selectedcharts: [],
      active_windows: {},

      scope_markers: {},
      scope: "",

      init_tree: false,
      init_rt_timeline: false,

      obj_timeline: {
        timeline: {},
        timeline_items: {}
      },

      obj_datatable: {},

      temp_marker_map: {},    // todo: remove

      // scope
      data: {
        errors: {
          metrics: [],   // charts for deployment scope
        }
      }
    },
  };

  this.Model.enable_init_tree = function () {
    this.state.init_tree = true;
  },

    this.Model.disable_init_tree = function () {
      this.state.init_tree = false;
    },

    this.Model.is_init_tree = function () {
      return this.state.init_tree;
    },

    this.Model.set_scope = function (scope) {
      this.state.scope = scope;
    },

    this.Model.get_scope = function () {
      return this.state.scope;
    },


  // timeline object ///////////////////////////

  this.Model.set_timeline_obj = function (timeline, timeline_items) {
    if (typeof this.obj_timeline === 'undefined') {
      this.obj_timeline                = {};
      this.obj_timeline.timeline       = {};
      this.obj_timeline.timeline_items = {};
    }
    this.obj_timeline.timeline       = timeline;
    this.obj_timeline.timeline_items = timeline_items;
  };

  this.Model.get_timeline_obj = function () {
    if (typeof this.obj_timeline === 'undefined') {
      return null;
    }
    else return this.obj_timeline.timeline;
  };

  this.Model.get_timeline_items_obj = function () {
    if (typeof this.obj_timeline === 'undefined') {
      return null;
    }
    else return this.obj_timeline.timeline_items;
  };

  // datatable object ///////////////////////////

  this.Model.set_datatable_obj = function (obj) {
    if (typeof this.obj_datatable === 'undefined') {
      this.obj_datatable = {};
    }
    this.obj_datatable = obj;
  };

  this.Model.get_datatable_obj = function (obj) {
    return this.obj_datatable;
  };

  // flags ///////////////////////////

  this.Model.set_init_timeline_flag = function (b) {
    this.init_rt_timeline = b;
  };

  this.Model.get_init_timeline_flag = function () {
    return this.init_rt_timeline;
  };

  this.Model.enable_realtime_mode = function () {
    this.realtimemode = true;
  };

  // realtime node  ///////////////////////////

  this.Model.disable_realtime_mode = function () {
    this.realtimemode = false;
  };

  this.Model.get_realtime_mode = function () {
    return this.realtimemode;
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

  // chartviewer window handling  ///////////////////////////

  this.Model.set_scope_markers = function (obj) {
    this.state.scope_markers = obj;
  };
  this.Model.get_scope_markers = function () {
    return this.state.scope_markers;
  };


  this.Model.set_metrics = function (metrics) {
    this.state.data.errors.metrics = metrics;
  };

  this.Model.get_metrics = function () {
    return this.state.data.errors.metrics
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

  this.Model.set_curr_explorer_node = function (obj) {
    if (typeof this.state.curr_explorer_node === 'undefined') {
      this.state.curr_explorer_node = obj;
    }
    else {
      this.state.curr_explorer_node = obj;
    }
  };
  this.Model.set_prev_explorer_node = function (obj) {
    if (typeof this.state.prev_explorer_node === 'undefined') {
      this.state.prev_explorer_node = obj;
    }
    else {
      this.state.prev_explorer_node = obj;
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
  this.Model.get_state       = function () {
    return this.state;
  };


  this.Model.get_curr_explorer_node = function () {
    return this.state.curr_explorer_node;
  };
  this.Model.get_prev_explorer_node = function () {
    return this.state.prev_explorer_node;
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
      momentutcets = moment.utc().startOf('minute');
      momentutcsts = moment(momentutcets).subtract(14, "days");
      sts          = momentutcsts.valueOf();
      ets          = momentutcets.valueOf();
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

  this.testapi = function (commonfactory, deployment_id, customer_id, cluster_id, node_id, sts, ets) {
    var graphservice = this;

    /*
     graphservice.getclustermarkers({
     "deployment_id": deployment_id,
     "customer_id": customer_id,
     "cluster_id": cluster_id,
     "sts": sts,
     "ets": ets
     }).success(function (response) {

     var m = response;
     //console.log("testapi getclustermarkers" + JSON.stringify(m));

     }).error(function (error) {
     console.log("err: testapi getclustermarkers: " + error);
     });

     graphservice.getnodemarkers({
     "deployment_id": deployment_id,
     "customer_id": customer_id,
     "cluster_id": cluster_id,
     "node_id": node_id,
     "sts": sts,
     "ets": ets
     }).success(function (response) {

     var m = response;
     //console.log("testapi getnodemarkers" + JSON.stringify(m));

     }).error(function (error) {
     console.log("err: testapi getnodemarkers: " + error);
     });

     graphservice.getnodedetail({
     "deployment_id": deployment_id,
     "customer_id": customer_id,
     "cluster_id": cluster_id,
     "node_id": node_id,
     "sts": sts,
     "ets": ets
     }).success(function (response) {
     var m = response;
     //console.log("testapi getnodedetail" + JSON.stringify(m));

     }).error(function (error) {
     //console.log("err: testapi getnodedetail: " + error);
     });

     graphservice.getnodesnapshot({
     "deployment_id": deployment_id,
     "customer_id": customer_id,
     "cluster_id": cluster_id,
     "node_id": node_id,
     "sts": sts,
     "ets": ets
     }).success(function (response) {
     var m = response;
     //console.log("testapi getnodesnapshot" + JSON.stringify(m));

     }).error(function (error) {
     console.log("err: testapi getnodesnapshot: " + error);
     });
     */

    /*
     Sept 21
     1474452000000 15:30
     1474455600000	16:30

     graphservice.Model.set_scope('deployment');

     var scope = graphservice.Model.get_scope();
     graphservice.get_tree_for_deployment(commonfactory, deployment_id, customer_id, 1474452000000, 1474455600000).then(function (response) {
     console.log("get_tree_for_deployment" + JSON.stringify(response));
     }).catch(function (e) {
     console.log("err: get_tree_for_deployment: " + JSON.stringify(e));
     });

     graphservice.Model.set_scope('cluster');

     graphservice.get_tree_for_cluster(commonfactory, deployment_id, customer_id, "myappcluster", 1474452000000, 1474455600000).then(function (response) {
     console.log("get_tree_for_cluster" + JSON.stringify(response));
     }).catch(function (e) {
     console.log("err: get_tree_for_cluster: " + JSON.stringify(e));
     });

     graphservice.Model.set_scope('node');
     graphservice.get_snapshot_for_node(commonfactory, deployment_id, customer_id, "myappcluster", "192.168.6.21", 1474452000000, 1474455600000).then(function (response) {
     console.log("get_snapshot_for_node" + JSON.stringify(response));
     }).catch(function (e) {
     console.log("err: get_snapshot_for_node: " + JSON.stringify(e));
     });

     graphservice.Model.set_scope('cluster');
     graphservice.get_snapshot_for_cluster(commonfactory, deployment_id, customer_id, "myappcluster", 1474452000000, 1474455600000).then(function (response) {
     console.log("get_snapshot_for_cluster" + JSON.stringify(response));
     }).catch(function (e) {
     console.log("err: get_snapshot_for_cluster: " + JSON.stringify(e));
     });

     graphservice.Model.set_scope('deployment');
     graphservice.get_snapshot_for_deployment(commonfactory, deployment_id, customer_id, 1474452000000, 1474455600000).then(function (response) {
     console.log("get_snapshot_for_deployment" + JSON.stringify(response));
     }).catch(function (e) {
     console.log("err: get_snapshot_for_deployment: " + JSON.stringify(e));
     });

     graphservice.Model.set_scope('deployment');
     graphservice.get_markers(commonfactory, deployment_id, customer_id, "", "", 1474452000000, 1474455600000).then(function (response) {
     console.log("get_markers" + JSON.stringify(response));
     }).catch(function (e) {
     console.log("err: get_markers: " + JSON.stringify(e));
     });

     graphservice.Model.set_scope('cluster');
     graphservice.get_markers(commonfactory, deployment_id, customer_id, "myappcluster", "", 1474452000000, 1474455600000).then(function (response) {
     console.log("get_markers" + JSON.stringify(response));
     }).catch(function (e) {
     console.log("err: get_markers: " + JSON.stringify(e));
     });

     graphservice.Model.set_scope('node');
     graphservice.get_markers(commonfactory, deployment_id, customer_id, "myappcluster", "192.168.6.21", 1474452000000, 1474455600000).then(function (response) {
     console.log("get_markers" + JSON.stringify(response));
     }).catch(function (e) {
     console.log("err: get_markers: " + JSON.stringify(e));
     });
     */

    //graphservice.Model.set_scope('deployment');
    //graphservice.display_tree(commonfactory, false);

    //commonfactory.sleep(1000 * 5).then(function () {
    console.log("test api new scope" + JSON.stringify(graphservice.Model.get_scope()));
    //graphservice.display_tree(commonfactory, true);
    //});
  };



}]); // App.Service

app.controller('summaryController', ['$scope', 'commonfactory', 'graphservice', '$window', function ($scope, commonfactory, graphservice, $window) {

  $(document).ready(function () {

    // console.log("model:
    // "+ JSON.stringify(graphservice.Model.get_state()));

    /*

    // Load fonts (one time)
    Highcharts.createElement('link', {
      href: 'https://fonts.googleapis.com/css?family=Unica+One',
      rel: 'stylesheet',
      type: 'text/css'
    }, null, document.getElementsByTagName('head')[0]);


    // create button with realtime set to off
    $('#realtimebutton').jqxSwitchButton({
                                           height: 20,
                                           width: 60,
                                           checked: false
                                         });
    graphservice.Model.disable_realtime_mode(); // off by default

    var timerId;

    $('.jqx-switchbutton').on('unchecked', function (event) {
      var minms = 60000;

      graphservice.Model.set_init_timeline_flag(true);
      graphservice.Model.enable_realtime_mode();

      // enable clock
      var b_refresh = true;
      timerId       = setInterval(function () { graphservice.process_rt(commonfactory, b_refresh) }, minms);
    });

    $('.jqx-switchbutton').on('checked', function (event) {
      graphservice.Model.disable_realtime_mode();

      clearInterval(timerId);
      console.log("Enabled Manual Mode");

      graphservice.Model.set_init_timeline_flag(true);
      graphservice.display_markers(commonfactory);
    });

    // starting scope
    graphservice.Model.set_scope('deployment');

    graphservice.display_markers(commonfactory);

    graphservice.display_layout(commonfactory);

    // graphservice.testapi(commonfactory, deployment_id, customer_id,
    // "myappcluster", "192.168.6.21", tr.sts, tr.ets);

    $('#myCarousel').carousel({interval: 5000});

    // hook up buttons
    $('#myCarousel').click(function () {
      $('#homeCarousel').carousel('cycle');
    });
    $('#myCarousel').click(function () {
      $('#homeCarousel').carousel('pause');
    });

    */

  }).error(function (error) {
    console.log("err: onReady: " + error);
  });



}]);

