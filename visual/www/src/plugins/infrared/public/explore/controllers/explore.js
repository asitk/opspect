'use strict';

//import _ from 'lodash';
import Promise from 'bluebird';
import $ from 'jquery';
import 'angular';

import moment from 'moment';
import 'moment-timezone';

import Highcharts from 'highcharts/highcharts.js';
import 'highcharts/highcharts-more.js';
import Heatmap from 'highcharts/modules/heatmap.js';
Heatmap(Highcharts);

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
import 'plugins/infrared/explore/controllers/_explore';
import uiRoutes from 'ui/routes';
import uiModules from 'ui/modules';
import chrome from 'ui/chrome';

import 'plugins/infrared/explore/styles/explore.css';  // load after vis

import explore_template from 'plugins/infrared/explore/index.html';
import chart_template from 'plugins/infrared/explore/chart/index.html';

const app = uiModules.get('apps/explore', []);

uiRoutes
  .when('/explore/chart', {
    redirectTo: '/explore/chart',
    template: chart_template
  });

uiRoutes
  .when('/explore/chart?_g=()', {
    redirectTo: '/explore/chart',
    template: chart_template
  });

uiRoutes
  .when('/explore', {
    template: explore_template
  });

app.service('chartservice', ['$http', '$window', function ($http, $window) {

  this.display_chart_in_viewer_gethidden_series = function (sts, ets) {
    var minms         = 60000;
    var diff          = ets - sts;

    var no_of_mins = Math.round(diff / minms);
    var patched_values = [];
    for (var qidx = 0; qidx < no_of_mins; qidx++) {
      var inner = [];
      inner.push(sts + (qidx * minms));
      inner.push(1); // add default 0

      patched_values.push(inner);
    }
    return patched_values;
  }

  this.display_chart_in_viewer_patchvalues = function (values, sts, ets) {
    var minms         = 60000;
    var diff          = ets - sts;

    var no_of_mins = Math.round(diff / minms);
    console.log("display_chart_in_viewer_patchvalues no_of_mins: " + no_of_mins);

    var patched_values = [];
    for (var qidx = 0; qidx < no_of_mins; qidx++) {
      var inner = [];
      inner.push(sts + (qidx * minms));
      inner.push(0); // add default 0

      patched_values.push(inner);
    }

    // patch qresult
    for (var i = 0; i < values.length; i++)
    {
      if (values[i][1] > 1)
      {
        // locate corresponding ts in patched map
        for (var qidx = 0; qidx < no_of_mins; qidx++) {

          if (patched_values[qidx][0] == values[i][0])
          {
            patched_values[qidx][1] = 1;
          }
        }
      }
    }
    // console.log("display_chart_in_viewer_patchvalues patched_values" +
    //            JSON.stringify(patched_values));
    return patched_values;
  },

  this.display_chart_in_viewer = function (chartid, explore_common, composite, peercomposite, title, subtitle) {
    var qresponse = composite.qresponse;
    var qrequest  = composite.qrequest;

    var qpeerresponse = peercomposite.qresponse;
    var minms         = 60000;

    // console.log("display_chart_in_viewer id:" +
    // JSON.stringify(chartid) + " title: " + title + " sub: " + subtitle);

    // common options for all charts
    Highcharts.setOptions(explore_common.get_highcharts_theme('chartviewer'));
    Highcharts.setOptions(explore_common.get_highcharts_plotoptions());
    Highcharts.setOptions(explore_common.get_highcharts_utc());

    // console.log("display_chart_in_viewer composite: " +
    // JSON.stringify(composite));
    //console.log("display_chart_in_viewer peerbaseline: " +
    // JSON.stringify(peercomposite));

    //console.log("display_chart_in_viewer qrequest: " +
    // JSON.stringify(qrequest));
    //console.log("display_chart_in_viewer qresponse: " +
    // JSON.stringify(qresponse));

    var diff = ets - sts;
    console.log("display_chart_in_viewer composite:  ** ts diff ** " + JSON.stringify(diff));

    // get plotbands for hour markers or above
    //if (diff >= hourms) {
    var plotbands = explore_common.get_plot_bands_from_window_score(composite.context.ws);
    //}

    // for resource exhaustion we need to extrapolate
    // for the hour using future time

    // thresholds : only to be applied for threadhold class
    //              and resource exhaustion

    var eta                         = composite.context.eta;
    var high_water_mark             = composite.context.high_water_mark;
    var display_resource_exhaustion = composite.context.display_resource_exhaustion;
    var display_threshold_detection = composite.context.display_threshold_detection;
    var display_service_detection   = composite.context.display_service_detection;
    var display_heartbeat_detection = composite.context.display_heartbeat_detection;
    var thermal                     = composite.context.thermal;

    /*
       console.log("display_chart_in_viewer high_water_mark: " + JSON.stringify(composite.context.high_water_mark));
       console.log("display_chart_in_viewer" +
       " display_threshold_detection: " + JSON.stringify(composite.context.display_threshold_detection));
       console.log("display_chart_in_viewer" +
       " display_resource_exhaustion: " + JSON.stringify(composite.context.display_resource_exhaustion));
       console.log("display_chart_in_viewer eta: " + JSON.stringify(composite.context.eta));
       console.log("display_chart_in_viewer thermal: " + thermal);
     */

    var high_water_mark_plot       = {};
    var series                     = [];
    var plotlines                  = [];
    var start_plotbandsts          = plotbands[0].from;
    var last_plotbandets           = plotbands[plotbands.length - 1].to;
    var color_by_thermal           = explore_common.get_thermal_color_for_plotbands(thermal);
    var first_starttime_by_thermal = composite.context.ws[String(thermal)][0].startTime;

    /*
     console.log("display_chart_in_viewer color_by_thermal," +
     " first_starttime_by_thermal" +
     JSON.stringify(color_by_thermal) + " " + JSON.stringify(first_starttime_by_thermal));
     */

    // flatten values for heartbeat detection and service detection
    if (display_service_detection || display_heartbeat_detection) {
      console.log("display_chart_in_viewer qresponse : " + JSON.stringify(qresponse.queries[0].results[0].values));

      qresponse.queries[0].results[0].values = this.display_chart_in_viewer_patchvalues(qresponse.queries[0].results[0].values, sts, ets);
      qpeerresponse.queries[0].results[0].values = this.display_chart_in_viewer_patchvalues(qpeerresponse.queries[0].results[0].values, sts, ets);

      // add a hidden series to handle cases where only patched points
      // exists, ex: where service detection has failed for 60 of 60 mins
      var hidden_series = this.display_chart_in_viewer_gethidden_series(sts, ets);
      var hidden_plot = {
        data: hidden_series,
        visible: false,
        showInLegend: false,
      };
      series.push(hidden_plot);
      //console.log("hidden_series: " + JSON.stringify(hidden_series));
    }

    if (display_resource_exhaustion) {
      var eta_plotband = {};

      if (composite.context.eta > last_plotbandets) {
        eta_plotband = {
          from: first_starttime_by_thermal,
          to: eta,
          color: color_by_thermal,
        };

      }
      else {
        eta_plotband = {
          from: first_starttime_by_thermal,
          to: last_plotbandets,
          color: color_by_thermal,
        };
      }

      plotbands.push(eta_plotband);
    }
    else {
      // todo: push all the plotbands here rather than pre-creating ?
    }

    if (display_threshold_detection || display_resource_exhaustion) {
      high_water_mark_plot = {
        value: high_water_mark,
        color: 'orangered',
        dashStyle: 'longdashdot',
        width: 2,
        label: {
          text: 'Hi',
          align: 'left',
          style: {
            color: 'white',
            fontWeight: 'bold'
          }
        }
      };

      plotlines.push(high_water_mark_plot);

      var hvalues     = [[start_plotbandsts, high_water_mark * 1.1], [last_plotbandets, high_water_mark * 1.1]];
      var hidden_plot = {
        data: hvalues,
        visible: false,
        showInLegend: false,
      };

      //console.log(JSON.stringify(hvalues));

      series.push(hidden_plot);
    }

    var query_plot = {
      color: 'dodgerblue',
      minPointLength: 1,
      data: qresponse.queries[0].results[0].values,
      name: subtitle,
      lineWidth: 2,
      marker: {
        enabled: true,
        symbol: 'circle',
        radius: 4
      }
    };

    var peer_plot = {
          minPointLength: 1,
          color: 'lightgreen',
          data: qpeerresponse.queries[0].results[0].values,
          name: 'Peer',
          lineWidth: 2,
          marker: {
            enabled: true,
            symbol: 'square',
            radius: 4
          }
    };

    series.push(query_plot);
    series.push(peer_plot);

    var chartoptions = {
      chart: {
        renderTo: chartid,
        ignoreHiddenSeries: false,
        reflow: true,
        zoomType: 'x',
        type: 'spline',
      },

      credits: {
        enabled: false
      },

      yAxis: {
        min: 0,
        minorGridLineWidth: 1,
        plotLines: plotlines,
      },

      xAxis: {
        type: 'datetime',
        minTickInterval: 60000,     // 1min
        tickInterval: 60000,
        lineWidth: 0,
        minorGridLineWidth: 0,
        lineColor: 'transparent',
        gridLineWidth: 0,
        plotBands: plotbands,
      },

      series: series
    };

    var chart = new Highcharts.Chart(explore_common.get_highcharts_title(chartoptions, title));
  }
}]); // App.Service

app.controller('chartController', ['$scope', 'explore_common', 'chartservice', '$window', function ($scope, explore_common, chartservice, $window) {

  chrome.setVisible(false);
  $(".app-links-wrapper").hide();

  $(document).ready(function () {
    var sts = $window.sts;
    var ets = $window.ets;
    //var curr_explorer_node = $window.curr_explorer_node;

    if (typeof $window.selectedcharts === 'undefined') {
      return;
    }

    var startDt      = new Date(sts);
    var endDt        = new Date(ets);
    var startTimeStr = moment(startDt).format('MMMM Do YYYY, h:mm:ss a');
    var endTimeStr   = moment(endDt).format('h:mm:ss a');

    $("#chartviewerfixedheader").html("From: " + startTimeStr + " - To: " + endTimeStr);

    var nselected = explore_common.count_properties($window.selectedcharts);
    // console.log("chartController selectedcharts: " +
    // JSON.stringify($window.selectedcharts));

    // get the no of lines to process (3 charts per row)
    var nlines = nselected / 3;
    nlines     = Math.floor(nlines);
    // console.log("chartController nselected: " +
    // JSON.stringify(nselected) + " nlines " + JSON.stringify(nlines));

    var iDiv = document.createElement('div');
    iDiv.id  = 'block';
    document.getElementById('errorchart').appendChild(iDiv);

    var count = 1;
    var innerDiv;

    if (nselected < 3) {
      for (var j = 0; j < nselected; j++) {
        innerDiv           = document.createElement('div');
        innerDiv.id        = 'chartid_' + count;
        innerDiv.className = 'floating-box-2';
        iDiv.appendChild(innerDiv);
        count = count + 1;
      }
    }
    else {
      if (nselected === 3) {
        // Single line, use long format
        for (var j = 0; j < 3; j++) {  // 3 rows
          innerDiv           = document.createElement('div');
          innerDiv.id        = 'chartid_' + count;
          innerDiv.className = 'floating-box-1';
          iDiv.appendChild(innerDiv);
          count = count + 1;
        }
      }
      else {
        for (var i = 0; i < nlines; i++) {
          for (var j = 0; j < 3; j++) {  // 3 rows
            innerDiv           = document.createElement('div');
            innerDiv.id        = 'chartid_' + count;
            innerDiv.className = 'floating-box-3';
            iDiv.appendChild(innerDiv);
            count = count + 1;
          }
        }
      }
    }

    // if 1 chart remaining use full width
    if (count === nselected) {
      innerDiv           = document.createElement('div');
      innerDiv.id        = 'chartid_' + count;
      innerDiv.className = 'floating-box-1';
      iDiv.appendChild(innerDiv);
    }

    // if 2 remaining use 40% width
    if (count === nselected - 1) {
      for (; count < nselected + 1; count++) {
        innerDiv           = document.createElement('div');
        innerDiv.id        = 'chartid_' + count;
        innerDiv.className = 'floating-box-2';
        iDiv.appendChild(innerDiv);
      }
    }

    // console.log("chartController selectedcharts: Last row count " +
    // JSON.stringify(count));

    // load charts
    var cid = 1;
    for (var index in $window.selectedcharts) {
      console.log("chartController selectedcharts: " + index, typeof index); //logs 1..3, "string"

      var customer_id   = $window.selectedcharts[index].data.customer_id;
      var deployment_id = $window.selectedcharts[index].data.deployment_id;
      var cluster_id    = $window.selectedcharts[index].data.cluster_id;
      var thermal       = $window.selectedcharts[index].data.thermal;

      var plugin         = $window.selectedcharts[index].data.plugin;
      var classification = $window.selectedcharts[index].data.classification;
      var host_ip        = $window.selectedcharts[index].data.host_ip;
      var host_name      = $window.selectedcharts[index].data.host_name;
      var target         = $window.selectedcharts[index].data.target;
      var ws             = JSON.parse($window.selectedcharts[index].data.ws);
      var tags           = JSON.parse($window.selectedcharts[index].data.tags);
      var anomaly_class  = $window.selectedcharts[index].data.anomaly_class;
      var anomaly_type   = $window.selectedcharts[index].data.anomaly_type;

      console.log("chartController selectedcharts anomaly_class: " + JSON.stringify(anomaly_class));

      var display_resource_exhaustion = 0;
      var display_threshold_detection = 0;
      var display_service_detection   = 0;
      var display_heartbeat_detection = 0;

      console.log("anomaly_type: " + anomaly_type + " plugin " + plugin + " classification " + classification);

      // anomaly type processing ...
      if (anomaly_class.toLowerCase() === "system metric" ||
          anomaly_class.toLowerCase() === "network activity") {

        if (anomaly_type.toLowerCase() === "error detection") {
          console.log("chartController selectedcharts anomaly_type: " + JSON.stringify(anomaly_type));
        }

        if (anomaly_type.toLowerCase() === "service detection") {
          console.log("chartController selectedcharts anomaly_type: " + JSON.stringify(anomaly_type));

          if (plugin.toLowerCase() === "service") {
            if (classification.toLowerCase() === "uptime") {
              console.log("chartController patching service uptime");

              plugin         = "procstat";
              classification = "memory_rss";

              display_service_detection = 1;
            }
          }
        }

        if (anomaly_type.toLowerCase() === "heartbeat detection") {
          if (plugin.toLowerCase() === "host") {
            if (classification.toLowerCase() === "uptime") {
              console.log("chartController patching host uptime");

              plugin         = "system";
              classification = "uptime";

              display_heartbeat_detection = 1;
            }
          }
        }

        var minms           = 60000;
        var high_water_mark = 0;
        var eta             = sts + (minms * 59); // todo: check eta and remove

        if (anomaly_type.toLowerCase() === "resource exhaustion detection") {
          console.log("chartController selectedcharts anomaly_type: " + JSON.stringify(anomaly_type));

          // Add to List of plugins that support thresholds
          if (plugin.toLowerCase() === "mem") {
            if (classification.toLowerCase() === "used") {

              // generate threshold line
              high_water_mark             = tags.high_water_mark;
              //eta = tags["eta"];          // todo
              display_resource_exhaustion = 1;
            }
          }
        }

        if (anomaly_type.toLowerCase() === "threshold detection") {
          console.log("chartController selectedcharts anomaly_type: " + JSON.stringify(anomaly_type));

          // generate threshold line
          high_water_mark = tags.high_water_mark;
          //eta = tags["eta"];

          display_threshold_detection = 1;
        }
      }

      // var name = plugin.classification;
      var metrics = [{
        "name": plugin + "." + classification,
        "tags": [
          {
            "name": "customer_id",
            "values": [customer_id]
          },
          {
            "name": "deployment_id",
            "values": [deployment_id]
          },
          {
            "name": "cluster_id",
            "values": [cluster_id]
          },
          {
            "name": "target",
            "values": [target]
          },
          {
            "name": "host_name",
            "values": [host_name]             // specific host
          },
          //{
          //  "name": "host_ip",
          //  "values": [host_ip]               // specific ip
          //}
        ]
      }
      ];

      var peerbaseline = [{
        "name": plugin + "." + classification,
        "tags": [
          {
            "name": "customer_id",
            "values": [customer_id]
          },
          {
            "name": "deployment_id",
            "values": [deployment_id]
          },
          {
            "name": "cluster_id",
            "values": [cluster_id]
          },
          {
            "name": "target",
            "values": [target]
          },
          /*{
           "name":"host_name",
           "values": ['*']                         // peerbaseline
           },*/
          {
            "name": "host_ip",
            "values": ['255.255.255.255']           // peerbaseline
          }
        ]
      }];

      var context            = {};
      context.plugin         = plugin;
      context.classification = classification;
      context.target         = target;
      context.deployment_id  = deployment_id;
      context.customer_id    = customer_id;
      context.thermal        = thermal;
      context.sts            = sts;
      context.ets            = ets;

      context.peerbaseline = peerbaseline;
      context.ws           = ws;
      context.id           = cid++;
      context.host_name    = host_name;
      context.host_ip      = host_ip;

      context.eta                         = eta;
      context.high_water_mark             = parseFloat(high_water_mark);
      context.display_resource_exhaustion = display_resource_exhaustion;
      context.display_threshold_detection = display_threshold_detection;
      context.display_service_detection   = display_service_detection;
      context.display_heartbeat_detection = display_heartbeat_detection;

      var end_ts = ets;

      if (context.display_resource_exhaustion === 1) {
        if (eta > ets) {
          end_ts = eta;
        }
      }

      // reduce the sts by 10 mins to provide data coverage for min
      // timestamps
      explore_common.query(deployment_id, customer_id, metrics, sts - 10 * 60 * 1000, end_ts, context).then(function (composite) {

        // update chart
        // console.log("chartController qresult: " +
        // JSON.stringify(composite.qrequest));
        // console.log("display_controller " + JSON.stringify(chartid) +
        // plugin + " " + classification);

        // var metricscount = composite.qrequest.metrics.length;

        var title;
        var subtitle;

        var tokens = composite.qrequest.metrics[0].name.split(".");
        title = tokens[0] + "." + tokens[1]; // default

        if (composite.context.plugin === 'procstat') {  // handle service
          if (tokens[1] === "memory_rss") {
            title = composite.context.target + "." + "uptime";
          }
          else title = composite.context.target + "." + tokens[1];
        }

        if (composite.context.plugin === 'system' &&
          composite.context.classification === 'uptime') {
          title = "host.uptime";
        }

        subtitle = composite.context.host_name + "." + composite.context.host_ip;

        var chartid = 'chartid_' + composite.context.id;
        var psts    = composite.context.sts - 5 * 60 * 1000;
        var pets    = composite.context.ets;

        // get baseline
        explore_common.query(deployment_id, customer_id, composite.context.peerbaseline, psts, pets, composite.context).then(function (peercomposite) {
          chartservice.display_chart_in_viewer(chartid, explore_common, composite, peercomposite, title, subtitle);

        }).catch(function (e) {
          console.log("err: " + e);
        });

      }).catch(function (e) {
        console.log("err: " + e);
      });
    }
  });
}]);

app.service('explore_service', ['$http', '$window', function ($http, $window) {
  var explore_service = this;

  this.setHeader = function () {
    var explore_service = this;

    var sts = explore_service.Model.get_curr_marker().sts;
    var ets = explore_service.Model.get_curr_marker().ets;

    var startDt      = new Date(sts);
    var endDt        = new Date(ets);
    var startTimeStr = moment(startDt).format('MMMM Do YYYY, h:mm:ss a');
    var endTimeStr   = moment(endDt).format('h:mm:ss a');

    var scope = explore_service.Model.get_scope();
    //var scope        = explore_service.get_current_scope();

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

    var selectedcharts = explore_service.Model.get_state().selectedcharts;

    var rowarray = [];
    for (var i = 0; i < selectedcharts.length; i++) {
      rowarray.push(selectedcharts[i].rowindex);
    }
    rowarray     = rowarray.sort();
    var hashstr  = rowarray.toString();
    var hashcode = this.hashCode(hashstr).toString();

    // check if this hash exists if so just do a win.focus

    if (explore_service.Model.is_activewindow(hashcode)) {
      console.log("found active window!");

      explore_service.Model.set_activewindow_focus(hashcode);
    }
    else {
      // add window
      console.log("Did not find active window!");

      var win = $window.open("http://www.nuvidata.com:5601/app/infrared#/explore/chart?id=1", hashcode, "width=200,height=100");
      // console.log("openWindow: " +
      // JSON.stringify(explore_service.Model.get_state().selectedcharts));

      win.selectedcharts = explore_service.Model.get_state().selectedcharts;
      win.sts            = explore_service.Model.get_curr_marker().sts;
      win.ets            = explore_service.Model.get_curr_marker().ets;
      //win.curr_explorer_node =
      // explore_service.Model.get_curr_explorer_node();

      this.Model.add_activewindow(hashcode, win);
      console.log("name: " + win.name);

      win.onbeforeunload = function () {
        console.log("onunload: " + this.name);
        explore_service.Model.del_activewindow(this.name);
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

  this.getclusterservices = function (deployment_id, customer_id, cluster_id, sts, ets) {
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
        var composite     = {};
        console.log("processclusterservice: " + JSON.stringify(response));

        composite.clusterservices = clusterservices;
        resolve(composite);
      }).error(function (e) {
        console.log("getclusterservice err: " + e);
        reject(e);
      });

    });
  };

  this.getclusterconnections = function (deployment_id, customer_id, cluster_id, sts, ets) {
    var gs = this;

    return new Promise(function (resolve, reject) {
      var getclusterconnectionsfn = gs.getclusterconnection;

      if (typeof getclusterconnectionsfn === "undefined") {
        console.log("err: getclusterconnectionsfn:" +
                    " getclusterconnectionsfn is undefined");
      }

      getclusterconnectionsfn({
                                "deployment_id": deployment_id,
                                "customer_id": customer_id,
                                "cluster_id": cluster_id,
                                "sts": sts,
                                "ets": ets
                              }).success(function (response) {
        var clusterconnections = response;

        if ($.isEmptyObject(clusterconnections) == false) {
           console.log("getclusterconnections: " + JSON.stringify(clusterconnections));

          var composite = {};
          composite.clusterconnections = {};

          if (clusterconnections.connections.length > 0) {
            composite.clusterconnections = clusterconnections;
            resolve(composite);
          }
          else {
            console.log("getclusterconnections: clusterconnections" +
                        " length is 0 for " + cluster_id);
            var composite = {};
            composite.clusterconnections = {};
            resolve(composite);
          }
        }
        else {
          console.log("getclusterconnections: clusterconnections is empty for " + deployment_id);
          var composite = {};
          reject(composite);
        }
      }).error(function (e) {
        console.log("getclusterconnections err: " + e);
        reject(e);
      });
    })
  };

  // returns markers corresponding to scope
  this.get_markers = function (explore_common, deployment_id, customer_id, cluster_id, node_id, sts, ets) {
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
  this.get_snapshot_for_node = function (explore_common, deployment_id, customer_id, cluster_id, node_id, sts, ets) {
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
            var node_metrics  = explore_common.create_chart_metrics(nodesnapshot.stats, nodesnapshot.thermal_summary, sts, ets);
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
  this.get_snapshot_for_cluster = function (explore_common, deployment_id, customer_id, cluster_id, sts, ets) {
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

            var cluster_metrics = explore_common.create_chart_metrics(clustersnapshot.stats, clustersnapshot.thermal_summary, sts, ets);
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
  this.get_snapshot_for_deployment = function (explore_common, deployment_id, customer_id, sts, ets) {
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

            var deployment_metrics = explore_common.create_chart_metrics(deploymentsnapshot.stats, deploymentsnapshot.thermal_summary, sts, ets);

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
  this.get_tree_for_cluster = function (explore_common, deployment_id, customer_id, cluster_id, sts, ets) {
    var explore_service = this;

    var scope = explore_service.Model.get_scope();

    if (scope === 'cluster') {
      return new Promise(function (resolve, reject) {
        explore_service.getclusterdetail({
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

            var icon_str        = explore_common.get_icon_for_tree("desktop",
                                                                   clusterdetail.nodeDetailsList[cindex].thermal);
            var name_prefix_str = explore_common.get_tree_name_prefix(clusterdetail.nodeDetailsList[cindex].thermal_count)
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
  this.get_tree_for_deployment = function (explore_common, deployment_id, customer_id, sts, ets) {
    var explore_service = this;

    var scope = explore_service.Model.get_scope();

    if (scope === 'deployment' || scope === 'cluster') {  // details upto cluster level

      return new Promise(function (resolve, reject) {
        console.log("get_tree_for_deployment"
                    + " deployment_id: " + JSON.stringify(deployment_id)
                    + " customer_id: " + JSON.stringify(customer_id)
                    + " sts: " + JSON.stringify(sts)
                    + " ets: " + JSON.stringify(ets));

        var tree      = [];
        var composite = {};

        explore_service.getdeploymentdetail({
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

          var icon_str        = explore_common.get_icon_for_tree("deployment", deploymentdetail.thermal);
          var name_prefix_str = explore_common.get_tree_name_prefix(deploymentdetail.thermal_count) + deploymentdetail.name;

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
            var icon_str        = explore_common.get_icon_for_tree(deploymentdetail.clusterDetailsList[dindex].role,
                                                                   deploymentdetail.clusterDetailsList[dindex].thermal);
            var name_prefix_str = explore_common.get_tree_name_prefix(deploymentdetail.clusterDetailsList[dindex].thermal_count)
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

        // console.log("display_errors_datatable row:" +
        // JSON.stringify(row));

        dr.push(row);
      }
    }
    return dr;
  };

  /**
   * @summary     PageResize
   * @description Automatically alter the DataTables page length to fit the table
   into a container
   * @version     1.0.0
   * @file        dataTables.pageResize.js
   * @author      SpryMedia Ltd (www.sprymedia.co.uk)
   * @contact     www.sprymedia.co.uk/contact
   * @copyright   Copyright 2015 SpryMedia Ltd.
   *
   * License      MIT - http://datatables.net/license/mit
   *
   * This feature plug-in for DataTables will automatically change the DataTables
   * page length in order to fit inside its container. This can be particularly
   * useful for control panels and other interfaces which resize dynamically with
   * the user's browser window instead of scrolling.
   *
   * Page resizing in DataTables can be enabled by using any one of the following
   * options:
   *
   * * Adding the class `pageResize` to the HTML table
   * * Setting the `pageResize` parameter in the DataTables initialisation to
   *   be true - i.e. `pageResize: true`
   * * Setting the `pageResize` parameter to be true in the DataTables
   *   defaults (thus causing all tables to have this feature) - i.e.
   *   `$.fn.dataTable.defaults.pageResize = true`.
   * * Creating a new instance: `new $.fn.dataTable.PageResize( table );` where
   *   `table` is a DataTable's API instance.
   *
   * For more detailed information please see:
   *     http://datatables.net/blog/2015-04-10
   */
  (function($){

    var PageResize = function ( dt )
    {
      var table = dt.table();

      this.s = {
        dt:        dt,
        host:      $(table.container()).parent(),
        header:    $(table.header()),
        footer:    $(table.footer()),
        body:      $(table.body()),
        container: $(table.container()),
        table:     $(table.node())
      };

      var host = this.s.host;
      if ( host.css('position') === 'static' ) {
        host.css( 'position', 'relative' );
      }

      this._attach();
      this._size();
    };


    PageResize.prototype = {
      _size: function ()
      {
        var settings = this.s;
        var dt = settings.dt;
        var t = dt.table();
        var offsetTop = $( settings.table ).offset().top;
        var rowHeight = $( 'tr', settings.body ).eq(0).height();
        var availableHeight = settings.host.height();
        var scrolling = t.header().parentNode !== t.body().parentNode;

        // Subtract the height of the header, footer and the elements
        // surrounding the table
        if ( ! scrolling ) {
          availableHeight -= settings.header.height();
          availableHeight -= settings.footer.height();
        }
        availableHeight -= offsetTop;
        availableHeight -= settings.container.height() - ( offsetTop + settings.table.height() );

        //console.log("PAGERESIZE: settings.host.height(): " +settings.host.height());
        //console.log("PAGERESIZE: settings.header.height(): " +
        // settings.header.height());
        //console.log("PAGERESIZE: settings.footer.height(): " +
        // settings.footer.height());
        //console.log("PAGERESIZE: offsetTop: " + offsetTop);
        //console.log("PAGERESIZE: settings.container.height(): " +
        // settings.container.height());
        //console.log("PAGERESIZE: availableHeight: " + availableHeight);

        var drawRows = Math.floor( availableHeight / rowHeight );

        if ( drawRows !== Infinity && drawRows !== -Infinity &&
          ! isNaN( drawRows )   && drawRows > 0 &&
          drawRows !== dt.page.len()
        ) {
          // dt.page.len( drawRows ).draw();
          if (drawRows > 1) {
            dt.page.len(drawRows - 1).draw(); // asit
          }
          else {
            dt.page.len(drawRows).draw();
          }
        }
      },

      _attach: function () {
        // There is no `resize` event for elements, so to trigger this effect,
        // create an empty HTML document using an <object> which will issue a
        // resize event inside itself when the document resizes. Since it is
        // 100% x 100% that will occur whenever the host element is resized.
        var that = this;
        var obj = $('<object/>')
          .css( {
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  height: '100%',
                  width: '100%',
                  zIndex: -1
                } )
          .attr( 'type', 'text/html' );

        obj[0].onload = function () {
          var body = this.contentDocument.body;
          var height = body.offsetHeight;

          this.contentDocument.defaultView.onresize = function () {
            var newHeight = body.clientHeight || body.offsetHeight;

            if ( newHeight !== height ) {
              height = newHeight;

              that._size();
            }
          };
        };

        obj
          .appendTo( this.s.host )
          .attr( 'data', 'about:blank' );
      }
    };


    $.fn.dataTable.PageResize = PageResize;
    $.fn.DataTable.PageResize = PageResize;

    // Automatic initialisation listener
    $(document).on( 'init.dt', function ( e, settings ) {
      if ( e.namespace !== 'dt' ) {
        return;
      }

      var api = new $.fn.dataTable.Api( settings );

      if ( $( api.table().node() ).hasClass( 'pageResize' ) ||
        settings.oInit.pageResize ||
        $.fn.dataTable.defaults.pageResize )
      {
        new PageResize( api );
      }
    } );

  }(jQuery));
  
  this.display_errors_datatable = function (explore_common, composite, refresh) {
    var gs = this;

    var d             = [];
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

    //console.log("display_errors_datatable create_rows: " +
    // JSON.stringify(d));

    var oldt = gs.Model.get_datatable_obj();
    if (typeof oldt !== 'undefined' && oldt !== null) {
      console.log("display_errors_datatable found existing instance");
      table = oldt;

      table.clear();
      if (d.length > 0) {
        table.rows.add(d);
        table.draw();
      }
    }
    else {
      console.log("display_errors_datatable: creating new table");

      var wrapperHeight = $('#errorlistinner').height();

      table = $('#grid').DataTable({
                                     //dom:
                                     //'li<"datatablebuttonpadding">fBpt',
                                     dom: '<"ctrlwrap"if<"datatablebuttonpadding">Bpt>',
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
                                             // explore_common.stringifyOnce(r["0"]));
                                             // console.log("rows_selected: " +
                                             // explore_common.stringifyOnce(r[i.toString()]));

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

                                             //console.log("row :" + explore_common.stringifyOnce(row));
                                             gs.Model.add_selectedcharts(i, row);
                                           }

                                           if (rows_selected.length > 0) {
                                             gs.openWindow();
                                           }
                                         }
                                       }
                                     ],

                                     //pageLength: 5,
                                     //"lengthMenu": [[5, 10, 25, -1],
                                     // [5, 10, 25, "All"]],

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
                                         width: "5%",
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

                                     //"sScrollY": true,
                                     "sScrollX": false,
                                     "autoWidth": true,
                                     pageResize: true, // using auto resize
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
      explore_common.updateDataTableSelectAllCtrl(table);

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
      explore_common.updateDataTableSelectAllCtrl(table);
    });
  };

  // Stacked chart
  this.create_chart_plugin_class_ws_by_ts = function (explore_common, carousel_footer_offset, id) {
    var explore_service = this;

    var w = $('#anomalycarousel').width();
    var h = $('#anomalycarousel').height();

    var deployment_stats = explore_service.Model.get_metrics();
    var result_obj       = deployment_stats["heatmap_by_type_classification"]["result_obj"];

    var title  = "Anomaly Detection Heatmap";
    var subtitle = "Model Progression Over Time";
    var ytitle = "Time";
    var reflow = true;

    var heatmap_options = explore_common.get_heatmap_opt(id,
                                                        reflow,
                                                        w -1,
                                                        h - carousel_footer_offset,
                                                        title, subtitle, ytitle,
                                                        result_obj.categories,
                                                        result_obj.series);
    
    var heatmap_chart = new Highcharts.Chart(heatmap_options);
  };

  // column chart (Rank Distribution)
  this.create_chart_distrib_by_rank = function (explore_common, carousel_footer_offset, id) {
    var explore_service = this;

    var w = $('#anomalycarousel').width();
    var h = $('#anomalycarousel').height();

    // console.log("create_chart_distrib_by_rank pre-w " +
    // JSON.stringify(w));
    // console.log("create_chart_distrib_by_rank pre-h " +
    // JSON.stringify(h));

    var stats      = explore_service.Model.get_metrics();
    var result_obj = stats["distrib_by_rank"]["result_obj"];

    // console.log("create_chart_distrib_by_rank: result_obj" +
    // JSON.stringify(result_obj));

    var top_count = result_obj.categories.length;
    var title     = "Anomaly Distribution by Top " + top_count + " Ranks"
    var ytitle    = "Rank";
    var reflow    = true;

    // console.log("create_chart_distrib_by_rank: stats" +
    // JSON.stringify(stats));

    if (result_obj.series.length > 0) {
      var col_options = explore_common.get_column_chart_opt(id,
                                                            reflow,
                                                            w - 1,
                                                            h - carousel_footer_offset,
                                                            title, ytitle,
                                                            result_obj.categories,
                                                            result_obj.series);
      var col_chart_chart_distrib_by_rank   = new Highcharts.Chart(col_options);
    }
    else {
      // no data
      console.log("err: create_chart_distrib_by_rank no data");
      return;
    }
  };

  // pie chart (plugin distrubution by share)
  this.create_chart_plugin_class_by_share = function (explore_common, carousel_footer_offset, id) {
    var explore_service = this;

    var w = $('#anomalycarousel').width();
    var h = $('#anomalycarousel').height();

    var stats      = explore_service.Model.get_metrics();
    var result_obj = stats["pie_distrib_by_share"]["result_obj"];
    var title     = "Major.Classification by Rank";
    var reflow    = true;

    // console.log("create_chart_plugin_class_by_share: pie" +
    // JSON.stringify(stats["pie_distrib_by_share"]["result_obj"]));

    var pie_options = explore_common.get_pie_chart_opt(id,
                                                       reflow,
                                                       w - 1,
                                                       h - carousel_footer_offset,
                                                       title,
                                                       result_obj.series,
                                                       "Classification");
    var pie         = new Highcharts.Chart(pie_options);
  };

  // pie chart (plugin distrubution by share)
  this.create_chart_service_by_sev = function (explore_common, id, w, h, target) {
    var explore_service = this;

    var stats      = explore_service.Model.get_metrics();
    var result_obj = stats["pie_distrib_by_target"]["result_obj"];
    var title     = "Service Anomaly Type by Sev";
    var reflow    = true;

    //console.log("create_chart_service_by_sev pie: " +
    // JSON.stringify(result_obj));
    //console.log("create_chart_service_by_sev target: " +
    // JSON.stringify(target));

    var pie_options = explore_common.get_pie_chart_opt(id,
                                                       reflow,
                                                       w,
                                                       h,
                                                       title,
                                                       result_obj[target],
                                                       target);

    //console.log("create_chart_service_by_sev options: " +
    // JSON.stringify(pie_options));

    var pie_service_by_sev         = new Highcharts.Chart(pie_options);
  };


  this.create_barchart_service_by_costly_requests = function (explore_common, id, w, h, connection) {

    var title      = "Costly Requests";
    var reflow     = true;
    var categories = ["Costly Requests"];
    var series     = [];

    var ttfbobj = {};
    ttfbobj["name"] = "Time to First Byte";
    ttfbobj["data"] = [];

    var ttlbobj = {};
    ttlbobj["name"] = "Time to Last Byte";
    ttlbobj["data"] = [];

    var req_obj = {};

    if (connection.costliest_request_stats.length > 0) {
      for (var ridx = 0; ridx < connection.costliest_request_stats.length; ridx++) {

        console.log("costliest_request_stats: " + JSON.stringify(connection.costliest_request_stats[ridx]));

        (ttfbobj["data"]).push(connection.costliest_request_stats[ridx].ttfb);
        (ttlbobj["data"]).push(connection.costliest_request_stats[ridx].ttlb);

        var request = connection.costliest_request_stats[ridx].req;
        var r = request.split(" ");

        console.log("create_barchart_service_by_costly_requests r: " + r);

        if (typeof req_obj[r[0]+r[1]] === 'undefined') {
          req_obj[r[0]+r[1]] = [];
          req_obj[r[0]+r[1]].push(connection.costliest_request_stats[ridx].count);
        }
        else {
          req_obj[r[0]+r[1]].push(connection.costliest_request_stats[ridx].count);
        }
      }
    }

    // create series array
    for (var key in req_obj) {

        var series_request = {};
        series_request.name = key;
        series_request.data = req_obj[key];
        series.push(series_request);
    }

    series.push(ttfbobj);
    series.push(ttfbobj);

    console.log("create_barchart_service_by_costly_requests series: " + JSON.stringify(series));

    var col_options = explore_common.get_column_chart_opt(id,
                                                          reflow,
                                                          w,
                                                          h,
                                                          title,
                                                          "",
                                                          categories,
                                                          series
    );
    
    console.log("create_barchart_service_by_costly_requests req_obj: " + JSON.stringify(req_obj));
    console.log("create_barchart_service_by_costly_requests ttfbobj: " + JSON.stringify(ttfbobj));
    console.log("create_barchart_service_by_costly_requests ttlbobj: " + JSON.stringify(ttlbobj));
    console.log("create_barchart_service_by_costly_requests col_options: " + JSON.stringify(col_options));

    var barchart_service_by_costly_requests         = new Highcharts.Chart(col_options);
  };

  // display error table summaries for scope. Scope data is loaded into
  // composite
  this.display_error_summary_chart = function (explore_common, composite) {
    var explore_service        = this;
    var carousel_footer_offset = 35;   // spacer for carousel indicators

    // common options for all charts
    Highcharts.setOptions(explore_common.get_highcharts_theme());
    Highcharts.setOptions(explore_common.get_highcharts_plotoptions());
    Highcharts.setOptions(explore_common.get_highcharts_utc());

    explore_service.create_chart_distrib_by_rank(explore_common, carousel_footer_offset, 'error_overview_c1');
    explore_service.create_chart_plugin_class_by_share(explore_common, carousel_footer_offset, 'error_overview_c2');
    explore_service.create_chart_plugin_class_ws_by_ts(explore_common, carousel_footer_offset, 'error_overview_c3');

    // set event
    $(window).bind('resize', function () {
      // console.log("display_error_summary_chart highcharts: resize");

      if (typeof $("#anomalycarousel") !== 'undefined') {
        var c1 = $("#error_overview_c1").highcharts();
        if (typeof c1 !== 'undefined') {
          c1.destroy();
          explore_service.create_chart_distrib_by_rank(explore_common, carousel_footer_offset, 'error_overview_c1');
        }

        var c2 = $("#error_overview_c2").highcharts();
        if (typeof c2 !== 'undefined') {
          c2.destroy();
          explore_service.create_chart_plugin_class_by_share(explore_common, carousel_footer_offset, 'error_overview_c2');
        }

        var c3 = $("#error_overview_c3").highcharts();
        if (typeof c3 !== 'undefined') {
          c3.destroy();
          explore_service.create_chart_plugin_class_ws_by_ts(explore_common, carousel_footer_offset, 'error_overview_c3');
        }
      }
    }).trigger('resize');
  };

  this.display_services_datatable_create_rows = function (svc_info) {
    var dr = [];

    var row = [];
    row.push(svc_info.ipver);
    row.push(svc_info.cluster_id);
    row.push(svc_info.port);
    row.push(svc_info.proto);
    row.push(svc_info.name);
    row.push(svc_info.interface);

    console.log("display_errors_datatable row:" + JSON.stringify(row));

    dr.push(row);
    return dr;
  };

  $('#servicetabs').on('tabclick', function (event)
  {
    var clickedItem = event.args.item;

    var service = $('#servicetabs').jqxTabs('getTitleAt', clickedItem);
    var table = $('#grid').DataTable();

    if (clickedItem === 0) {
      table
        .columns( 5 )             // target
        .search("")
        .draw();
    }
    else {
      table
        .columns(5)             // target
        .search(service)
        .draw();
    }
  });

  this.display_cluster_services = function(explore_common, clusterservices, clusterconnections) {
    var main_tab_id=1;

    // remove older tabs
    var length = $('#servicetabs').jqxTabs('length');
    console.log("display_cluster_services tab len=" + length);

    for (var lidx=1; lidx < length; lidx++) {
      console.log("table destroyed id" + lidx);
      $('#topgrid'+lidx).DataTable().destroy();
      $('#servicetabs').jqxTabs('removeAt', lidx);
    }
    
    // walk the service array and create new tabs
    main_tab_id=1; // start at the tab 1
    for (var i=0; i < clusterservices.services.length; i++) {
      console.log("display_cluster_services clusterservices.services: ") + JSON.stringify(clusterservices.services[i]);

      $('#servicetabs').jqxTabs('addLast', clusterservices.services[i].svc_info.name, explore_common.create_service_content(main_tab_id));

      // create chart tabs after create_service_content
      $("#servicecharttabs"+main_tab_id).jqxTabs({ width: '100%', height: '100%'});
      var w = $('#ssev_graph'+main_tab_id).width();
      var h = $('#ssev_graph'+main_tab_id).height();

      $('#topgrid'+main_tab_id).DataTable( {
                                 data: this.display_services_datatable_create_rows(clusterservices.services[i].svc_info),
                                    "bPaginate": false,
                                    "bFilter": false,
                                    "bInfo": false,
                                    'sDom': 't',
                                    columns: [
                                   { title: "IPver" },
                                   { title: "Cluster" },
                                   { title: "Port" },
                                   { title: "Proto" },
                                   { title: "Service" },
                                   { title: "Interface" }
                                 ]
                               } );

      $("#bottompanel"+main_tab_id).jqxPanel({ width: '100%', height: '100%'});

      this.create_chart_service_by_sev(explore_common, 'ssev_graph'+main_tab_id, w, h, clusterservices.services[i].svc_info.name);

      // create update bottom panel and create charts for costly
      // requests and errors

      var connections = clusterconnections.connections;
      for (var conidx=0; conidx < connections.length; conidx++) {
        if (connections[conidx].svc_info.name === clusterservices.services[i].svc_info.name) {
          $("#bottompanel"+main_tab_id).jqxPanel('append', explore_common.create_bottom_content(main_tab_id, connections[conidx]));

          this.create_barchart_service_by_costly_requests(explore_common, 'shttp_graph'+main_tab_id, w, h, connections[conidx]);
          this.create_chart_service_by_sev(explore_common, 'serror_graph'+main_tab_id, w, h, clusterservices.services[i].svc_info.name);

        }
      }

      $('#servicetabs').jqxTabs('ensureVisible', main_tab_id);

      main_tab_id++;
    }

    // select the first tab
    $("#servicetabs").jqxTabs('select', 0);
    $('#servicetabs').jqxTabs('enableAt', 0);
    $('#servicetabs').jqxTabs('focus');
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

  this.display_tree_proc_add_subtree = function (explore_common, cluster_id) {
    var explore_service = this;
    var t               = $.jstree.reference('#jstreeContainer');

    // populate sub tree
    var deployment_id = explore_common.get_defaults().deployment_id;
    var customer_id   = explore_common.get_defaults().customer_id;
    var sts           = explore_service.Model.get_curr_marker().sts;
    var ets           = explore_service.Model.get_curr_marker().ets;

    // get cluster and sub nodes
    explore_service.get_tree_for_cluster(explore_common, deployment_id, customer_id, cluster_id, sts, ets).then(function (response) {
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

  this.display_tree_proc_onloaded = function (explore_common, composite) {
    var explore_service = this;

    $('#jstreeContainer').on('loaded.jstree', function () {

      var t = $.jstree.reference('#jstreeContainer');

      //var scope                 = explore_service.get_current_scope();
      var scope         = explore_service.Model.get_scope();
      var deployment_id = explore_common.get_defaults().deployment_id;
      var customer_id   = explore_common.get_defaults().customer_id;
      var sts           = explore_service.Model.get_curr_marker().sts;
      var ets           = explore_service.Model.get_curr_marker().ets;

      // todo: select the prev explorer node if available
      var prev_explorer_node = explore_service.Model.get_prev_explorer_node();

      if (prev_explorer_node === "") {
        console.log("display_tree_proc_onloaded selected root: " + JSON.stringify(composite.tree[0]));
        explore_service.Model.set_prev_explorer_node(composite.tree[0].id);
        prev_explorer_node = explore_service.Model.get_prev_explorer_node();
      }

      console.log("display_tree_proc_onloaded" +
                  " prev_explorer_node: " + JSON.stringify(prev_explorer_node) +
                  " scope " + scope);

      explore_service.setHeader();

      // get errors and metrics objects for scope
      if (scope === 'deployment') {
        var deployment_id = prev_explorer_node;
        explore_service.get_snapshot_for_deployment(explore_common, deployment_id, customer_id, sts, ets).then(function (response) {
          console.log("display_tree_proc_onloaded deployment snapshot");

          // set metrics into model
          explore_service.Model.set_metrics(response.metrics);

          explore_service.display_errors_datatable(explore_common, response, true);

          t.select_node(deployment_id, true);
          t.open_node(deployment_id, function () {}); // open cluster

          explore_service.display_error_summary_chart(explore_common);

        }).catch(function (e) {
          console.log("err: display_tree_proc_onloaded: " + JSON.stringify(e));
        });
      }
      if (scope === 'cluster') {
        var cluster_id = prev_explorer_node;
        t.deselect_all(true);

        var promises = [];

        explore_service.get_snapshot_for_cluster(explore_common, deployment_id, customer_id, cluster_id, sts, ets).then(function (response) {
          // console.log("display_tree_proc_onloaded cluster snapshot");
          // + JSON.stringify(response));

          // set metrics into model for summary
          explore_service.Model.set_metrics(response.metrics);

          promises.push(explore_service.getclusterservices(deployment_id, customer_id, cluster_id, sts, ets));
          promises.push(explore_service.getclusterconnections(deployment_id, customer_id, cluster_id, sts, ets));

          Promise.all(promises).then(function (result) {

            console.log("display_tree_proc_onloaded cluster result: " + JSON.stringify(result));

            var clusterservices;
            for (var sidx=0; sidx < result.length; sidx++) {
              if (typeof (result[sidx]["clusterservices"]) !== 'undefined')
              {
                clusterservices = result[sidx]["clusterservices"];
                break;
              }
            }

            console.log("display_tree_proc_onloaded cluster services: " + JSON.stringify(clusterservices));

            var clusterconnections;
            for (var cidx=0; cidx < result.length; cidx++) {
              if (typeof (result[cidx]["clusterconnections"]) !== 'undefined')
              {
                clusterconnections = result[cidx]["clusterconnections"];
                break;
              }
            }

            console.log("display_tree_proc_onloaded cluster processing clusterconnections: " + JSON.stringify(clusterconnections));

            explore_service.display_cluster_services(explore_common, clusterservices, clusterconnections);
            explore_service.display_errors_datatable(explore_common, response, true);

            explore_service.display_tree_proc_add_subtree(explore_common, cluster_id);
            t.select_node(cluster_id, true);
            t.open_node(cluster_id, function () {}); // open cluster

            explore_service.display_error_summary_chart(explore_common);

          }).catch(function (e) {
            console.log("err display_tree_proc_onloaded cluster processing: some promise failed: " + e);
          });

        }).catch(function (e) {
          console.log("err: display_tree_proc_onloaded: " + JSON.stringify(e));
        });
      }
      if (scope === 'node') {
        var node_id    = prev_explorer_node;
        var cluster_id = explore_service.get_parent_nodeid(node_id);

        console.log("display_tree_proc_onloaded node_id: " + JSON.stringify(node_id) + " cluster_id: " + JSON.stringify(cluster_id));

        explore_service.get_snapshot_for_node(explore_common, deployment_id, customer_id, cluster_id, node_id, sts, ets).then(function (response) {
          console.log("display_tree_proc_onloaded" +
                      " node snapshot" + JSON.stringify(response));

          explore_service.display_errors_datatable(explore_common, response, true);

          explore_service.display_tree_proc_add_subtree(explore_common, cluster_id);
          t.open_node(cluster_id, function () {}); // open cluster
          t.select_node(node_id, true);

          // set metrics into model
          explore_service.Model.set_metrics(response.metrics);
          explore_service.display_error_summary_chart(explore_common);

        }).catch(function (e) {
          console.log("err: display_tree_proc_onloaded: " + JSON.stringify(e));
        });
      }

    });
  };

  // display_tree: onselect event
  // load markers for selected scope

  this.display_tree_proc_bind_onselect = function (explore_common, composite) {
    var explore_service = this;

    $('#jstreeContainer').on('select_node.jstree', function (e, data) {
      var i, j;
      var t = $.jstree.reference('#jstreeContainer');

      //var scope                 = explore_service.get_current_scope();
      var scope        = explore_service.Model.get_scope();
      var selected_obj = data.instance.get_node(data.selected[0]).original;

      explore_service.Model.set_scope(selected_obj.type);
      console.log("display_tree_proc_bind_onselect set scope:" + explore_service.Model.get_scope());

      explore_service.setHeader();

      // set selected node into model
      explore_service.Model.set_prev_explorer_node(selected_obj.id);

      console.log("display_tree_proc_bind_onselect selected node: " + JSON.stringify(selected_obj) +
                  " scope " + JSON.stringify(scope));

      // if this is event triggered from the marker update, disable
      // flag and continue
      if (explore_service.Model.is_init_tree()) {
        console.log("display_tree_proc_bind_onselect disabled init flag ");
        explore_service.Model.disable_init_tree();
        return;
      }

      // get deployment markers for scope, marker select will reload tree
      // reselect previous node with corresponding errors and
      // summary charts
      if (scope === 'deployment' || scope === 'cluster' || scope === 'node') {
        explore_service.display_markers(explore_common);
      }
    });
  };

  // display_tree_proc:
  // reload tree and bind load and select events
  // init tree flag is used to prevent select event from running on
  // load

  this.display_tree_proc = function (explore_common, composite, refresh) {
    var explore_service = this;

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
    explore_service.Model.enable_init_tree();

    // populate sub tree and reselect prev selection
    // display grid and charts
    explore_service.display_tree_proc_onloaded(explore_common, composite);

    // display markers based on scope on select
    explore_service.display_tree_proc_bind_onselect(explore_common, composite);
  };

  this.display_tree = function (explore_common, refresh) {
    var explore_service = this;
    var scope           = explore_service.Model.get_scope();
    //var scope         = explore_service.get_current_scope();
    var deployment_id   = explore_common.get_defaults().deployment_id;
    var customer_id     = explore_common.get_defaults().customer_id;
    var sts             = explore_service.Model.get_curr_marker().sts;
    var ets             = explore_service.Model.get_curr_marker().ets;

    console.log("display_tree: current markers sts: " + JSON.stringify(sts) + " ets: " + JSON.stringify(ets));
    console.log("display_tree: scope: " + JSON.stringify(scope));

    if (scope === 'deployment' || scope === 'cluster') {

      // get root and all sub clusters
      explore_service.get_tree_for_deployment(explore_common, deployment_id, customer_id, sts, ets).then(function (response) {
        console.log("display_tree get_tree_for_deployment " + JSON.stringify(response));

        explore_service.display_tree_proc(explore_common, response, refresh);

      }).catch(function (e) {
        console.log("err: display_tree: " + JSON.stringify(e));
      });
    }

    // todo: handle node scope
    // Reset scope back to cluster
    // get tree details
    // display tree proc and try to reselect node if found
  };

  // create an array of markers
  this.display_markers_proc_create_array = function (explore_common, m) {
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

      var thermalclass = explore_common.get_vis_thermal_class(m.markers[i].thermal);
      var diff         = m.markers[i].end - m.markers[i].start;

      if (explore_service.Model.get_realtime_mode() === true) {
        // realtime mode

        if (diff <= minms) {
          //console.log("display_markers_proc_create_array: realtime Mode," +
          //             " adding min marker");
          marker_array.push({
                              id: i,
                              'start': markerdt,
                              //'content': '.',
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
                              //'content': '.',
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
    explore_service.Model.set_scope_markers(new_scope_markers);

    return marker_array;
  };

  // create options object for timeline
  this.display_markers_proc_create_options = function (explore_common, m) {
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
      zmin = hourms * 12;                 // 12hrs in ms
      zmax = ((dayms) * 14 * 1);           // about 2 weeks in ms

      //mindt.setUTCMilliseconds(m.markers[0].start - (dayms * 14));
      //maxdt.setUTCMilliseconds(m.markers[m.markers.length - 1].start
      // + hourms);

      mindt.setUTCMilliseconds(m.time_range.start - dayms);
      maxdt.setUTCMilliseconds(m.time_range.end + (hourms * 4));

      //console.log("display_marker scope_markers: mindt: " +
      // mindt.toString());
      //console.log("display_marker scope_markers: maxdt: " +
      // maxdt.toString());
    }

    var maxht = explore_common.timeline_height_px() + "px";

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
  this.display_markers_processor_onselect = function (explore_common) {
    var explore_service = this;

    var timeline = explore_service.Model.get_timeline_obj();

    timeline.on('select', function (properties) {
      var cache         = [];
      var state         = explore_service.Model.get_state();
      var scope_markers = state.scope_markers;
      var marker_array  = scope_markers.marker_array;

      // console.log("display_markers_proc properties: " +
      // explore_common.stringifyOnce(properties));
      // console.log("display_markers_proc marker_array: " +
      // explore_common.stringifyOnce(marker_array));

      console.log("display_markers_proc onselect");

      if (properties === null || typeof properties === 'undefined') return;
      if (properties.items === null || typeof properties.items === 'undefined') return;
      if (properties.items[0] === null || typeof properties.items[0] === 'undefined') return;
      if (properties.items.length <= 0) return;

      if (typeof marker_array[properties.items[0]] === 'undefined') return;
      if (typeof marker_array[properties.items[0]].id === 'undefined') return;
      if (marker_array[properties.items[0]].id === null) return;

      // check if the marker has changed
      var clicked_id = marker_array[properties.items[0]].id;
      var curr_id    = explore_service.Model.get_curr_marker().id;

      if (clicked_id === curr_id) {
        console.log("display_markers_proc clicked id === current id ")
      }
      else {
        explore_service.Model.set_curr_marker(marker_array[properties.items[0]]);
        timeline.focus(timeline.getSelection());

        console.log("display_markers_proc onselect curr marker: " + JSON.stringify(explore_service.Model.get_curr_marker()));
        console.log("display_markers_proc onselect prev marker: " + JSON.stringify(explore_service.Model.get_prev_marker()));

        // update tree for the current scope
        explore_service.display_tree(explore_common, true);
      }
    });
  };

  // display_markers_proc:
  // get markers
  // create timeline and display tree
  // in realtime mode update timeline
  // init and create new timeline on every mode change

  this.display_markers_proc = function (explore_common, m) {
    var explore_service = this;

    var timeline_items     = [];
    var timeline_options   = {};
    var timeline_container = {};
    var marker_array       = [];
    var timeline;
    var prev_timeline

    try {
      console.log("display_markers_proc count: " + JSON.stringify(m.markers.length));
      if (m.markers.length < 0) {
        throw "err: display_markers_proc: no markers found!";
        return;
      }

      // get new or updated marker array
      marker_array = explore_service.display_markers_proc_create_array(explore_common, m);
      if (marker_array.length <= 0) {
        throw "err: display_markers_proc empty marker_array!";
        return;
      }

      // if toggle is set destroy prev timeline if one exists
      if (explore_service.Model.get_init_timeline_flag()) {
        explore_service.Model.set_init_timeline_flag(false);

        prev_timeline = explore_service.Model.get_timeline_obj();
        if (prev_timeline !== null && typeof prev_timeline !== 'undefined') {
          // destroy prev timeline if existing from older run
          console.log("display_markers_proc destroying prev timeline" +
                      " since toggle is set");
          prev_timeline.destroy();
          explore_service.Model.set_timeline_obj(null, null);
        }
      }

      // if prev timeline not found create new one else update
      if (typeof explore_service.Model.get_timeline_obj() !== 'undefined' &&
        explore_service.Model.get_timeline_obj() !== null) {

        // update data, timeline should already be created
        timeline       = explore_service.Model.get_timeline_obj();
        timeline_items = explore_service.Model.get_timeline_items_obj();

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

        console.log("display_markers_proc: updating timeline")
        timeline_options = explore_service.display_markers_proc_create_options(explore_common, m);
        timeline.setOptions(timeline_options);
        explore_service.display_markers_proc_move_timeline(timeline, -1);
        timeline.setSelection(explore_service.Model.get_curr_marker().id);
        timeline.redraw();

        // update tree for the current scope
        explore_service.display_tree(explore_common, true);
      }
      else { // create new instance

        prev_timeline = explore_service.Model.get_timeline_obj();

        if (prev_timeline !== null && typeof prev_timeline !== 'undefined') {
          // destroy prev timeline if existing from older run
          console.log("display_markers_proc destroying prev timeline ?!");
          prev_timeline.destroy();
        }

        console.log("display_markers_proc creating new timeline");

        // create timeline
        if (marker_array.length > 0)
          timeline_items = new vis.DataSet(marker_array);

        timeline_options   = explore_service.display_markers_proc_create_options(explore_common, m);
        timeline_container = $('#timeline')[0];

        timeline = new vis.Timeline(timeline_container);
        timeline.setOptions(timeline_options);

        if (marker_array.length > 0)
          timeline.setItems(timeline_items);
        else
          console.log("display_markers_proc: created empty timeline");

        // set obj into model
        explore_service.Model.set_timeline_obj(timeline, timeline_items);

        // Set select event
        explore_service.display_markers_processor_onselect(explore_common);

        // select the last marker
        if (marker_array.length > 0) {

          // todo: select prev marker if found
          // else
          // select the latest marker and move timeline
          timeline.setSelection(marker_array[marker_array.length - 1].id);
          explore_service.display_markers_proc_move_timeline(timeline, -1);
          timeline.focus(marker_array[marker_array.length - 1].id);

          // set model to the latest marker
          explore_service.Model.set_curr_marker(marker_array[marker_array.length - 1]);

          // update tree for the current scope
          explore_service.display_tree(explore_common, true);

          //console.log("display_markers_proc selectinitial curr marker: "
          // + JSON.stringify(explore_service.Model.get_curr_marker()));
          //console.log("display_markers_proc selectinitial prev marker: "
          // + JSON.stringify(explore_service.Model.get_prev_marker()));
        }
        else {
          // clear current marker
          explore_service.Model.clear_curr_marker();
        }
      } // if (update_timeline === false)
    }
    catch (e) {
      console.log("display_markers_proc err: " + e);
    }
  };

  // get markers based on scope.
  // called from onselect handler of display_tree
  this.display_markers = function (explore_common) {
    var explore_service = this;

    var scope         = explore_service.Model.get_scope();
    var current_node  = explore_service.get_current_nodeid();
    var deployment_id = explore_common.get_defaults().deployment_id;
    var customer_id   = explore_common.get_defaults().customer_id;
    var tr            = explore_service.get_timerange_for_mode();

    if (typeof current_node === 'undefined' || current_node === null) {
      console.log("err: display_markers: current_node not found!");
      return;
    }

    console.log("display_markers scope: " +
                JSON.stringify(scope) + " current_node " +
                JSON.stringify(current_node));

    if (scope === 'deployment') {
      explore_service.getdeploymentmarkers({
                                             "deployment_id": deployment_id,
                                             "customer_id": customer_id,
                                             "sts": tr.sts,
                                             "ets": tr.ets
                                           }).success(function (response) {

        var m = response;

        if (typeof m.markers === 'undefined') {
          console.log("err getdeploymentmarkers: m.markers is undefined!");
          return;
        }

        if (m.markers.length > 0) {     // if there are markers
          explore_service.display_markers_proc(explore_common, m);
        }
        else {
          console.log("display_markers: deployment no markers found");
          return;
        }
      });
    }

    if (scope === 'cluster') {
      // get currently selected cluster
      console.log("display_markers current_node: " + current_node);
      var cluster_id = current_node;
      explore_service.getclustermarkers({
                                          "deployment_id": deployment_id,
                                          "customer_id": customer_id,
                                          "cluster_id": cluster_id,
                                          "sts": tr.sts,
                                          "ets": tr.ets
                                        }).success(function (response) {

        var m = response;

        if (typeof m.markers === 'undefined') {
          console.log("err getclustermarkers m.markers is undefined! ")
          return;
        }

        if (m.markers.length > 0) {     // if there are markers
          explore_service.display_markers_proc(explore_common, m);
        }
        else {
          console.log("display_markers: cluster no markers found");
          return;
        }

      });
    }

    if (scope === 'node') {
      // get currently selected node

      var cluster_id = explore_service.get_parent_nodeid(current_node);
      var node_id    = current_node;

      console.log("display_markers current_node: " + current_node + " cluster_id "
                  + JSON.stringify(cluster_id));

      console.log("display_markers getnodemarkers deployment: " + deployment_id +
                  customer_id + " cluster " + cluster_id + " node " + node_id);

      console.log("display_markers getnodemarkers sts: " + tr.sts + " ets " + tr.ets);

      explore_service.getnodemarkers({
                                       "deployment_id": deployment_id,
                                       "customer_id": customer_id,
                                       "cluster_id": cluster_id,
                                       "node_id": node_id,
                                       "sts": tr.sts,
                                       "ets": tr.ets
                                     }).success(function (response) {

        var m = response;

        if (typeof m.markers === 'undefined') {
          console.log("err getnodemarkers m.markers is undefined! ")
          return;
        }

        console.log("display_markers getnodemarkers" + JSON.stringify(m));

        if (m.markers.length > 0) {     // if there are markers
          explore_service.display_markers_proc(explore_common, m);
        }
        else {
          console.log("display_markers: node no markers found");
          return;
        }
      });
    }
  };

  // show the explorer layout
  this.display_layout = function (explore_common) {
    var withtimeline = true;
    var s      = explore_common.calc_explorer_layout(withtimeline);
    var layout = explore_common.create_layout(s.ht);

    /*
    $('#jqxLayout').jqxLayout({
                                resizable: true,
                                width: '100%',
                                height: s.ht,
                                layout: layout
                              });
    */

    //$('#splitContainer').jqxSplitter({ height: 850, width: 850,
    // orientation: 'horizontal', panels: [{ size: '10%' }, { size: '80%' }] });
    $('#splitter').jqxSplitter({ width: '100%',  height: s.ht-1, panels: [{ size: 200 }] });
    $("#servicetabs").jqxTabs({  height: '100%', width: '100%' });

    //$("#servicecharttabs").jqxTabs({  height: '100%', width: '100%' });
  };

  this.Model = {

    state: {
      curr_marker: "",
      prev_marker: "",            // todo: remove
      //curr_explorer_node: "",     // todo: remove
      prev_explorer_node: "",

      realtimemode: false,
      timelineon: false,

      selectedcharts: [],
      active_windows: {},

      scope_markers: {},
      scope: "",

      init_tree: false,
      init_timeline: false,

      obj_timeline: {
        timeline: {},
        timeline_items: {}
      },

      obj_datatable: {},

      // scope
      data: {
        errors: {
          metrics: [],   // charts for deployment scope
        }
      }
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

  /*
   this.Model.set_curr_explorer_node = function (obj) {
   if (typeof this.state.curr_explorer_node === 'undefined') {
   this.state.curr_explorer_node = obj;
   }
   else {
   this.state.curr_explorer_node = obj;
   }
   };
   */

  this.Model.set_prev_explorer_node = function (obj) {
    if (typeof this.state.prev_explorer_node === 'undefined') {
      this.state.prev_explorer_node = obj;
    }
    else {
      this.state.prev_explorer_node = obj;
    }
  };
  this.Model.get_prev_explorer_node = function () {
    return this.state.prev_explorer_node;
  };

  // state
  this.Model.get_state = function () {
    return this.state;
  };

  this.get_timerange_for_mode = function () {
    var explore_service = this;

    var momentutcets;
    var momentutcsts;
    var sts;
    var ets;

    if (explore_service.Model.get_realtime_mode() === true) {
      // get the current hour's markers (2 mins behind)
      var momentutc_sts = moment.utc().startOf('minute').subtract(63, "minutes");
      var momentutc_ets = moment(momentutc_sts).add(61, "minutes");
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
  this.process_rt = function (explore_common, b_refresh) {
    console.log("explore: process_rt calling display_markers");
    explore_service.display_markers(explore_common);
  };

  // unchecked_handler = on
  this.realtimebutton_unchecked_handler = function (explore_common) {
    var explore_service = this;
    var timerId;
    var minms = 60000;

    explore_service.Model.set_init_timeline_flag(true);
    explore_service.Model.enable_realtime_mode();

    // enable clock
    var b_refresh = true;
    timerId       = setInterval(function () { explore_service.process_rt(explore_common, b_refresh) }, minms);
    return timerId;
  };

  // checked_handler = off
  this.realtimebutton_checked_handler = function (explore_common, timerId) {
    var explore_service = this;
    explore_service.Model.disable_realtime_mode();

    clearInterval(timerId);
    console.log("Enabled Manual Mode");

    explore_service.Model.set_init_timeline_flag(true);
    explore_service.display_markers(explore_common);
  };

  this.timelinebutton_checked_handler = function (explore_common) {
    var explore_service = this;
    var errorgrid_ht = explore_common.errorgrid_height_px();
    var timeline_ht = explore_common.timeline_height_px();
    var ht = errorgrid_ht + timeline_ht;

    // console.log("checked_handler: errorgrid_ht + timeline_ht: " +
    // JSON.stringify(ht));
    explore_service.Model.hide_timeline();

    $("#errorlistinner").animate({height: ht+"px"});
    $("#timeline").hide();
    $(window).trigger('resize');
  };

  // unchecked_handler = on
  this.timelinebutton_unchecked_handler = function (explore_common) {
    var explore_service = this;
    var errorgrid_ht = explore_common.errorgrid_height_px();

    // console.log("unchecked_handler: errorgrid_ht:  " +
    // JSON.stringify(errorgrid_ht));

    explore_service.Model.show_timeline();

    $("#errorlistinner").animate({height: errorgrid_ht+"px"});
    $("#timeline").show();
    $(window).trigger('resize');
  };

  this.button_event_handlers = function (explore_common) {
    var explore_service = this;
    var timerId=0;

    $('.jqx-switchbutton').on('unchecked', function (event) {
      if (event.target.id === 'realtimebutton')
        timerId = explore_service.realtimebutton_unchecked_handler(explore_common);
      if (event.target.id === 'timelinebutton')
        timerId = explore_service.timelinebutton_unchecked_handler(explore_common);
    });

    $('.jqx-switchbutton').on('checked', function (event) {
      if (event.target.id === 'realtimebutton')
        return explore_service.realtimebutton_checked_handler(explore_common, timerId);
      if (event.target.id === 'timelinebutton')
        timerId = explore_service.timelinebutton_checked_handler(explore_common);
    });
  };

  this.initstorage = function(explore_common) {
    explore_service = this;

    // button heights - todo: abstract
    var height = 20;
    var width = 60;

    if (typeof(Storage) !== "undefined") {

      if (typeof sessionStorage.timelineon === 'undefined') {
        // show timeline
        explore_service.Model.show_timeline();
        $('#timelinebutton').jqxSwitchButton({
                                               height: height,
                                               width: width,
                                               checked: true
                                             });
        console.log("explore_controller init: timeline initialized")
      }
      else {
        // timelineon
        if (sessionStorage.timelineon === 'true') {
          // show timeline
          explore_service.Model.show_timeline();
          $('#timelinebutton').jqxSwitchButton({
                                                 height: height,
                                                 width: width,
                                                 checked: true
                                               });
          explore_service.timelinebutton_unchecked_handler(explore_common);
          console.log("explore_controller init: timeline enabled")
        }
        else {
          explore_service.Model.hide_timeline();
          $('#timelinebutton').jqxSwitchButton({
                                                 height: height,
                                                 width: width,
                                                 checked: false
                                               });
          explore_service.timelinebutton_checked_handler(explore_common);
          console.log("explore_controller init: timeline disabled")
        }
      }

      if (typeof sessionStorage.realtimemode === 'undefined') {

        // create button with realtime set to on
        explore_service.Model.enable_realtime_mode();
        $('#realtimebutton').jqxSwitchButton({
                                               height: height,
                                               width: width,
                                               checked: true
                                             });
        console.log("explore_controller init: realtime mode initialized")

      }
      else {

        if (sessionStorage.realtimemode === 'true') {
          explore_service.Model.enable_realtime_mode();
          $('#realtimebutton').jqxSwitchButton({
                                                 height: height,
                                                 width: width,
                                                 checked: true
                                               });
          console.log("explore_controller init: realtime mode enabled")
        }
        else {
          explore_service.Model.disable_realtime_mode();
          $('#realtimebutton').jqxSwitchButton({
                                                 height: height,
                                                 width: width,
                                                 checked: false
                                               });
          console.log("explore_controller init: realtime mode disabled")
        }
      }
    }
    else {
      console.log("Sessionstorage support is unavailable")
    }
  };

}]); // App.Service

app.controller('explore_controller', ['$scope', 'explore_common', 'explore_service', '$window', function ($scope, explore_common, explore_service, $window) {

  $(document).ready(function () {

    // init model
    explore_service.Model.init();

    // init realtime and timeline buttons
    explore_service.initstorage(explore_common);
    
    // Load fonts (one time)
    Highcharts.createElement('link', {
      href: 'https://fonts.googleapis.com/css?family=Unica+One',
      rel: 'stylesheet',
      type: 'text/css'
    }, null, document.getElementsByTagName('head')[0]);

    explore_service.button_event_handlers(explore_common);

    // starting scope
    explore_service.Model.set_scope('deployment');

    explore_service.display_markers(explore_common);

    explore_service.display_layout(explore_common);

    $('#anomalycarousel').carousel({interval: 10000});

    // hook up buttons
    $('#anomalyplaybutton').click(function () {
      $('#anomalycarousel').carousel('cycle');
    });
    $('#anomalypausebutton').click(function () {
      $('#anomalycarousel').carousel('pause');
    });
  }).error(function (error) {
    console.log("err: onReady: " + error);
  });

}]);

