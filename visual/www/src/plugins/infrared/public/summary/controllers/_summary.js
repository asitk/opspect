'use strict';

import _ from 'lodash';
import Promise from 'bluebird';
import $ from 'jquery';
import 'angular';

import Highcharts from 'highcharts/highcharts.js';
import 'highcharts/highcharts-more.js';
import 'highcharts/modules/heatmap.js';
import 'highcharts/modules/exporting.js';

import 'jqwidgets-framework/jqwidgets/styles/jqx.base.css';
import jqwidgets from 'jqwidgets-framework/jqwidgets/jqx-all.js';

import VisProvider from 'ui/vis';
import vis from 'vis/dist/vis.min.js';
import 'vis/dist/vis.min.css';

import 'jstree/dist/themes/default/style.min.css'
import 'jstree/dist/jstree.min.js'

import uiModules from 'ui/modules';

import 'plugins/infrared/summary/styles/summary.css';

const app = uiModules.get('apps/summary', [
  //'kibana/notify',
  //'kibana/courier',
  //'kibana/index_patterns',
]);

// Helper functions
app.factory('commonfactory', ['$http', function ($http) {
  return {

    // sleep time expects milliseconds
    sleep: function (time) {
      return new Promise((resolve) => setTimeout(resolve, time));
    },

    get_defaults: function () {
      var deployment_id = "ec2-dc-01";
      var customer_id   = "1234abcd";

      return {
        deployment_id: deployment_id,
        customer_id: customer_id
      };
    },

    ////////////////////////////////////////////////////
    // Explorer                                      //
    ///////////////////////////////////////////////////

    create_layout: function (ht) {
      var layout = [
        {
          type: 'layoutGroup',
          width: '100%',
          height: ht.toString(),
          items: [
            {
              type: 'layoutGroup',
              orientation: 'horizontal',
              width: '100%',
              height: '100%',
              //height: ht.toString(),
              items: [
                {
                  type: 'layoutGroup',
                  orientation: 'horizontal',
                  width: '15%',
                  minWidth: '10%',
                  height: '100%',
                  //alignment: 'left',
                  items: [
                    {
                      type: 'tabbedGroup',
                      height: '100%',
                      width: '100%',
                      items: [
                        {
                          type: 'layoutPanel',
                          title: 'Explorer',
                          contentContainer: 'ExplorerPanel'
                        }
                      ]
                    }
                  ]
                }, // explorerpanel
                {
                  type: 'layoutGroup',
                  orientation: 'vertical',
                  width: '85%',
                  items: [
                    {
                      type: 'documentGroup',
                      width: '100%',
                      height: '45%',
                      items: [
                        {
                          type: 'documentPanel',
                          title: 'Anomalies',
                          contentContainer: 'AnomalyPanel',
                        },
                        {
                          type: 'documentPanel',
                          title: 'Overview',
                          contentContainer: 'GraphPanel'
                        },
                      ]
                    },
                    {
                      type: 'tabbedGroup',
                      width: '100%',
                      height: '55%',
                      items: [{
                        type: 'layoutPanel',
                        title: 'Error List',
                        contentContainer: 'ErrorListPanel'
                      }]
                    }
                  ] /* items */
                }
              ] /*sub top level items */
            }
          ] /* top level items */
        }
      ];
      /* layout */

      return layout;
    },

    timeline_height_px: function () {
      return 150;
    },

    calc_explorer_layout: function () {
      var header_ht   = 38;
      var timeline_ht = this.timeline_height_px();
      var ht          = $(window).height() - timeline_ht - header_ht;
      var wd          = $(window).width();

      return {
        ht: ht,
        wd: wd,
      };
    },

    ////////////////////////////////////////////////////
    // General Helpers                               //
    ///////////////////////////////////////////////////

    stringifyOnce: function (obj, replacer, indent) {
      var printedObjects    = [];
      var printedObjectKeys = [];

      function printOnceReplacer(key, value) {
        if (printedObjects.length > 2000) { // browsers will not print more than 20K
          return 'object too long';
        }
        var printedObjIndex = false;
        printedObjects.forEach(function (obj, index) {
          if (obj === value) {
            printedObjIndex = index;
          }
        });

        if (key == '') { //root element
          printedObjects.push(obj);
          printedObjectKeys.push("root");
          return value;
        }

        else if (printedObjIndex + "" != "false" && typeof(value) == "object") {
          if (printedObjectKeys[printedObjIndex] == "root") {
            return "(pointer to root)";
          }
          else {
            return "(see " + ((!!value && !!value.constructor) ? value.constructor.name.toLowerCase() : typeof(value)) + " with key " + printedObjectKeys[printedObjIndex] + ")";
          }
        }
        else {

          var qualifiedKey = key || "(empty key)";
          printedObjects.push(value);
          printedObjectKeys.push(qualifiedKey);
          if (replacer) {
            return replacer(key, value);
          }
          else {
            return value;
          }
        }
      }

      return JSON.stringify(obj, printOnceReplacer, indent);
    },

    ////////////////////////////////////////////////////
    // Error Grid Helpers                             //
    ///////////////////////////////////////////////////

    //
    // Updates "Select all" control in a data table
    //
    updateDataTableSelectAllCtrl: function (table) {
      var $table            = table.table().node();
      var $chkbox_all       = $('tbody input[type="checkbox"]', $table);
      var $chkbox_checked   = $('tbody input[type="checkbox"]:checked', $table);
      var chkbox_select_all = $('thead input[name="select_all"]', $table).get(0);

      // If none of the checkboxes are checked
      if ($chkbox_checked.length === 0) {
        chkbox_select_all.checked = false;
        if ('indeterminate' in chkbox_select_all) {
          chkbox_select_all.indeterminate = false;
        }

        // If all of the checkboxes are checked
      }
      else if ($chkbox_checked.length === $chkbox_all.length) {
        chkbox_select_all.checked = true;
        if ('indeterminate' in chkbox_select_all) {
          chkbox_select_all.indeterminate = false;
        }

        // If some of the checkboxes are checked
      }
      else {
        chkbox_select_all.checked = true;
        if ('indeterminate' in chkbox_select_all) {
          chkbox_select_all.indeterminate = true;
        }
      }
    },

    ////////////////////////////////////////////////////
    // Chartviewer Helpers                            //
    ///////////////////////////////////////////////////

    get_plot_bands_from_window_score: function (ws) {
      //var ws = JSON.parse(ws_json_str);
      var minms     = 60000;
      var plotbands = [];

      // console.log("get_plot_bands_from_window_score ws: " +
      // JSON.stringify(ws));
      for (var key in ws) {
        var count = ws[key].length;
        for (var i = 0; i < count; i++) {
          //console.log("get_plot_bands_from_window_score key: " +
          // JSON.stringify(ws[key][i]) + " i: " + JSON.stringify(i));

          // Skip the plotbands for 30 and 100 (greens)
          if (ws[key][i].scoreType == "30" || ws[key][i].scoreType == "100") {
            continue;
          }

          var color = this.get_thermal_color_for_plotbands(ws[key][i].scoreType);
          var sts   = ws[key][i].startTime;
          var ets   = sts + ws[key][i].duration * minms;

          //console.log("plotbands: sts" + JSON.stringify(sts) + " ets:
          // " + JSON.stringify(ets));
          //console.log("plotbands: color " + JSON.stringify(color));

          plotbands.push({
                           from: sts,
                           to: ets,
                           color: color
                         });
          //console.log("plotbands:  " + JSON.stringify(plotbands));
        }
      }

      return plotbands;
    },

    get_vis_thermal_class: function (thermal) {
      switch (thermal) {
        case 1:
          return "red";
        case 20:
          return "orange";
        case 30:
          return "green1";
        case 100:
          return "green2";
        default:
          return "white";
      }
    },

    get_thermal_color: function (thermal) {
      switch (thermal) {
        case 1:
          return "crimson";
        case 20:
          return "gold";
        case 30:
          return "greenyellow";
        case 100:
          return "springgreen";
        default:
          return "ghostwhite";
      }
    },

    get_thermal_color_for_plotbands: function (thermal) {
      switch (thermal) {
        case 1:
          return 'indianred'
        case 20:
          return "lightyellow";
        case 30:
          return "#e5f2e5";
        case 100:
          return "#cce5cc";
        default:
          return "ghostwhite";
      }
    },

    ////////////////////////////////////////////////////
    // Stats processing for charts                    //
    ///////////////////////////////////////////////////

    update_sev: function (key, baseobj, subwsobj) {
      this.incrcounter(key, baseobj);

      // Add counts for severities
      for (var sub_key in subwsobj) {
        var m_sev = {};
        this.incrcounter(sub_key, m_sev);
      }

      // assign sev map to key
      baseobj[key].m_sev = m_sev;
    },

    /*
     {
     "categories": ["1","2","3","4","5"],
     "series": [
     {
     "name": "Anomaly.Heartbeat Detection",
     "data": [1,0,0,0,0]
     },
     {
     "name": "Anomaly.Error Detection",
     "data": [1,1,0,0,1]
     }
     ]
     }
     */
    distrib_by_rank_create_series: function (m_rank) {
      // get the top % ranks
      var last_rank = parseInt(m_rank.lkey);
      var top;

      if (last_rank > 50) {
        top = Math.round(last_rank * 0.10);
      }
      else {
        top = last_rank;
      }

      // add ranks and series
      var rank_series    = [];
      var selected_ranks = [];
      var rank_keys      = Object.keys(m_rank);
      for (var rid = 0; rid < top; rid++) {
        var curr_rank = rank_keys[rid];
        selected_ranks.push(curr_rank);

        //console.log("distrib_by_rank_create_series: rid " + rid);

        // Add counts for anomalies (act)
        var act_keys = Object.keys(m_rank[curr_rank].act);
        for (var actid = 0; actid < act_keys.length; actid++) {
          var curr_act = act_keys[actid];

          var str_curr_act  = curr_act.toString();
          var tokens        = str_curr_act.split(".");
          var str_curr_type = tokens[1];    // anomaly type

          //console.log("current_activity: " +
          //JSON.stringify(str_curr_type));
          // console.log("count: "
          // +JSON.stringify(m_rank[curr_rank].act[curr_act]));

          // see if this anomaly is already present in the series object
          var bFound = 0;
          for (var sid = 0; sid < rank_series.length; sid++) {
            if (rank_series[sid]["name"] == curr_act) {
              act_series_obj = rank_series[sid];
              bFound         = 1;
            }
          }

          if (!bFound) {
            // create new object and add it to series array
            var act_series_obj  = new Object;
            act_series_obj.name = curr_act;
            //act_series_obj.name = str_curr_type;
            act_series_obj.data = [];
            for (var dx = 0; dx < top; dx++)
              act_series_obj.data[dx] = 0;

            rank_series.push(act_series_obj);

            //console.log("distrib_by_rank_create_series: adding series" +
            //  JSON.stringify(rank_series) + "curr_act " +
            //  JSON.stringify(curr_act));
          }

          //console.log("distrib_by_rank_create_series: act_series_obj" +
          // JSON.stringify(act_series_obj));

          var ct = m_rank[curr_rank].act[curr_act].count;
          if (act_series_obj.name == curr_act) {
            //if (act_series_obj.name == str_curr_type) {
            // existing act, push data
            act_series_obj.data[rid] = ct;    // set the count into
          }                                   // the rank index
        }
      } // next rank

      //console.log("distrib_by_rank_create_series: categories" +
      //JSON.stringify(selected_ranks));
      //console.log("distrib_by_rank_create_series: series" +
      //JSON.stringify(rank_series));

      var result_obj = {
        categories: selected_ranks,
        series: rank_series
      }

      return result_obj;
    },

    /*  distrib_by_rank_create_counters

    {
      "1": {
      "count": 2,
        "act": {
        "Anomaly.Heartbeat Detection": {
          "count": 1
        },
        "Anomaly.Error Detection": {
          "count": 1
        }
      },
      "share": 0.0032566224694686365
    },
      "2": {
      "count": 2,
        "act": {
        "Anomaly.Service Detection": {
          "count": 1
        },
        "Anomaly.Error Detection": {
          "count": 1
        }
      },
      "share": 0.003182716211608755
    },
      "fkey": 1,
      "lkey": 536
    }

    */

    distrib_by_rank_create_counters: function (key, baseobj, anomaly_class_type, plugin_classification, share) {
      this.incrcounter(key, baseobj);
      this.incrsubcounter(key, anomaly_class_type, "act", baseobj);
      //this.incrsubcounter(key, plugin_classification, "pcl", baseobj);

      baseobj[key]["share"] = share;

      if (typeof baseobj["fkey"] === 'undefined') {
        baseobj["fkey"] = key;
      }
      baseobj["lkey"] = key;
    },

    // creates a metrics object for all charts from given stats
    // object

    create_chart_metrics: function (statsobj) {
      var metrics                 = {};
      var m_plugin_classification = {};
      var m_anomaly_class_type    = {};

      var distrib_by_rank = {};

      // console.log("create_chart_metrics: length" +
      // JSON.stringify(statsobj.length));

      for (var i = 0; i < statsobj.length; i++) {

        /*
         this.update_sev(statsobj[i].plugin + "." + statsobj[i].classification, m_plugin_classification,
         statsobj[i].ws);
         this.update_sev(statsobj[i].anomaly_class + "." + statsobj[i].anomaly_type, m_anomaly_class_type,
         statsobj[i].ws);
         */

        this.distrib_by_rank_create_counters(statsobj[i].contribution.rank, distrib_by_rank,
                                             statsobj[i].anomaly_class + "." + statsobj[i].anomaly_type,
                                             statsobj[i].plugin + "." + statsobj[i].classification,
                                             statsobj[i].contribution.share
        )
      }

      //console.log("create_chart_metrics: m_plugin_classification #:" +
      // JSON.stringify(this.count_properties(m_plugin_classification)));
      //console.log("create_chart_metrics: m_plugin_classification " +
      // JSON.stringify(m_plugin_classification));
      //console.log("create_chart_metrics: m_anomaly_class_type " +
      // JSON.stringify(m_anomaly_class_type));

      //metrics.m_plugin_classification = m_plugin_classification;
      //metrics.m_anomaly_class_type = m_anomaly_class_type;

      metrics["distrib_by_rank"]               = {};
      metrics["distrib_by_rank"]["result_obj"] = this.distrib_by_rank_create_series(distrib_by_rank);

      return metrics;
    },

    incrsubcounter: function (key, subkey, subclass, obj) {
      if (typeof obj[key] === 'undefined') obj[key] = {};
      if (typeof obj[key][subclass] === 'undefined') obj[key][subclass] = {};
      if (typeof obj[key][subclass][subkey] === 'undefined') {
        obj[key][subclass][subkey]       = {};
        obj[key][subclass][subkey].count = 0;
      }

      if (subkey in obj[key][subclass]) {
        obj[key][subclass][subkey].count++;
      }
    },

    incrcounter: function (key, obj) {
      if (typeof obj[key] === 'undefined') {
        obj[key]       = {};
        obj[key].count = 0;
      }

      if (key in obj) {
        obj[key].count++;
      }
    },

    count_properties: function (obj) {
      var count = 0;

      for (var prop in obj) {
        if (obj.hasOwnProperty(prop))
          ++count;
      }

      return count;
    },

    ////////////////////////////////////////////////////
    // Query helpers                                  //
    ///////////////////////////////////////////////////

    queryabsolute: function (jsonobj) {

      var qobj = {
        rawq: btoa(JSON.stringify(jsonobj))
      }

      var config = {
        params: qobj,
        headers: {
          "Content-Type": "application/json"
        }
      };
      return $http.get('http://www.nuvidata.com:5601/service/queryabsolute', config);

      //return
      // $http.post('http://www.nuvidata.com:5601/service/queryabsolute', jsonobj);
    },

    // Todo: Add params
    query: function (deployment_id, customer_id, metrics, sts, ets, context) {
      var gs = this;

      // save the start and end timestamps for use in update events
      return new Promise(function (resolve, reject) {
        var queryabsolutefn = gs.queryabsolute;

        var qrequest = (function () {
          var obj;

          obj = {
            "deployment_id": deployment_id,
            "customer": customer_id,
            "metrics": metrics,
            "cache_time": 0,
            "start_absolute": sts,
          };

          if (ets > 0) {
            obj.end_absolute = ets;
          }
          return obj;
        })();

        console.log("query qrequest: " + JSON.stringify(qrequest));

        // create query
        queryabsolutefn(qrequest).success(function (qresponse) {
          console.log("query queryabsolutefn: " + JSON.stringify(qresponse));

          var composite = {
            qrequest: qrequest,
            qresponse: qresponse,
            context: context,
          };

          resolve(composite);
        }).error(function (e) {
          console.log("query queryabsolutefn err : " + e);
          reject(e);
        })
      }).catch(function (e) {
        console.log("err query: " + e);
      });
    }, // query

    //////////////////////////////////////////////////////////////////
    // Highcharts Helpers                                           //
    /////////////////////////////////////////////////////////////////

    get_stacked_column_chart_opt: function (id, reflow, w, h, title, ytitle, categories, series) {
      var col_options = {
        chart: {
          renderTo: id,
          reflow: reflow,
          type: 'column',
          width: w,
          height: h,
        },
        title: {
          text: 'Stacked column chart'
        },
        xAxis: {
          //categories: ['Apples', 'Oranges', 'Pears', 'Grapes', 'Bananas']

          startOnTick: true,
          endOnTick: true,
          type: "datetime",
          //"minTickInterval": 86400000

        },
        yAxis: {
          //type: 'datetime',
          //minTickInterval: 60000,     // 1min
          //tickInterval: 60000,
          //lineWidth: 0,
          //minorGridLineWidth: 0,
          //lineColor: 'transparent',

          min: 0,
          title: {
            text: 'Total fruit consumption'
          },
          stackLabels: {
            enabled: true,
            style: {
              fontWeight: 'bold',
              color: (Highcharts.theme && Highcharts.theme.textColor) || 'gray'
            }
          }
        },
        legend: {
          align: 'right',
          x: -30,
          verticalAlign: 'top',
          y: 25,
          floating: true,
          backgroundColor: (Highcharts.theme && Highcharts.theme.background2) || 'white',
          borderColor: '#CCC',
          borderWidth: 1,
          shadow: false
        },
        tooltip: {
          headerFormat: '<b>{point.x}</b><br/>',
          pointFormat: '{series.name}: {point.y}<br/>Total: {point.stackTotal}'
        },
        plotOptions: {
          column: {
            stacking: 'normal',
            pointWidth: 40,
            dataLabels: {
              enabled: true,
              color: (Highcharts.theme && Highcharts.theme.dataLabelsColor) || 'white',
              style: {
                textShadow: '0 0 3px black'
              }
            }
          }
        },
        series: [{
          "name": "Camera",
          "data": [
            [1362614400000, 6],
            [1362528000000, 2]
          ]
        }, {
          "name": "NewCamera2",
          "data": [
            [1362614400000, 1]
          ]
        }],
      }

      return col_options;
    },

    get_column_chart_opt: function (id, reflow, w, h, title, ytitle, categories, series) {
      var col_options = {
        chart: {
          renderTo: id,
          reflow: reflow,
          type: 'column',
          width: w,
          height: h,
        },

        credits: {
          enabled: false
        },

        title: {
          text: title,
        },
        subtitle: {
          text: ''
        },
        xAxis: {
          categories: categories,
          crosshair: true
        },
        yAxis: {
          min: 0,
          title: {
            text: ytitle
          }
        },
        tooltip: {
          headerFormat: '<span style="font-size:10px">{point.key}</span><table>',
          pointFormat: '<tr><td style="color:{series.color};padding:0">{series.name}: </td>' +
          '<td style="padding:0"><b>{point.y:.1f} R</b></td></tr>',
          footerFormat: '</table>',
          shared: true,
          useHTML: true
        },
        plotOptions: {
          column: {
            pointPadding: 0.1,
            borderWidth: 0
          },
        },
        series: series,
      };

      return col_options;
    },

    get_pie_chart_opt: function (id, reflow, w, h, title, series) {
      var pie_options = {
        chart: {
          renderTo: id,
          reflow: reflow,
          width: w,
          height: h,
          plotBackgroundColor: null,
          plotBorderWidth: null,
          plotShadow: false,
          type: 'pie'
        },

        credits: {
          enabled: false
        },

        title: {
          text: title,
        },
        tooltip: {
          pointFormat: '{series.name}: <b>{point.percentage:.1f}%</b>'
        },
        plotOptions: {
          pie: {
            allowPointSelect: true,
            cursor: 'pointer',
            dataLabels: {
              enabled: false
            },
            showInLegend: true
          }
        },
        series: series
      };

      return pie_options;
    },

    get_highcharts_theme: function (type) {
      var theme = {
        colors: ["#2b908f", "#90ee7e", "#f45b5b", "#7798BF", "#aaeeee", "#ff0066", "#eeaaee",
          "#55BF3B", "#DF5353", "#7798BF", "#aaeeee"],
        chart: {
          backgroundColor: '#2a2a2b',
          /*{
           linearGradient: { x1: 0, y1: 0, x2: 1, y2: 1 },
           stops: [
           [0, '#2a2a2b'],
           [1, '#3e3e40']
           ]
           },*/
          style: {
            //fontFamily: "'Unica One', sans-serif"
            fontFamily: "'Lucida Console', monaco, monospace"
          },
          plotBorderColor: '#606063'
        },
        title: {
          style: {
            //color: '#E0E0E3',
            //color:'#32CD32',              // green
            color: '#41d1cc',                // aqua
            textTransform: 'lowercase',
            fontSize: '18px'
          }
        },
        subtitle: {
          style: {
            color: '#E0E0E3',
            //color:'#32CD32',              // green
            textTransform: 'lowercase'
          }
        },
        xAxis: {
          gridLineColor: '#707073',
          labels: {
            style: {
              color: '#E0E0E3'
            }
          },
          lineColor: '#707073',
          minorGridLineColor: '#505053',
          tickColor: '#707073',
          title: {
            style: {
              color: '#A0A0A3'
              //color:'#32CD32',              // green
            }
          }
        },
        yAxis: {
          gridLineColor: '#707073',
          labels: {
            style: {
              color: '#E0E0E3'
            }
          },
          lineColor: '#707073',
          minorGridLineColor: '#505053',
          tickColor: '#707073',
          tickWidth: 1,
          title: {
            style: {
              color: '#A0A0A3'
            }
          }
        },
        tooltip: {
          backgroundColor: 'rgba(0, 0, 0, 0.85)',
          style: {
            color: '#F0F0F0'
          }
        },
        plotOptions: {
          series: {
            dataLabels: {
              color: '#B0B0B3'
            },
            marker: {
              lineColor: '#333'
            }
          },
          boxplot: {
            fillColor: '#505053'
          },
          candlestick: {
            lineColor: 'white'
          },
          errorbar: {
            color: 'white'
          }
        },
        legend: {
          itemStyle: {
            color: '#E0E0E3',
            //color:'#32CD32',              // green
            //fontWeight: 'bold',
            fontSize: '14px',
            fontFamily: ' "Exo 2", sans-serif',
          },
          itemHoverStyle: {
            color: '#FFF'
          },
          itemHiddenStyle: {
            color: '#606063'
          }
        },
        credits: {
          style: {
            color: '#666'
          }
        },
        labels: {
          style: {
            color: '#707073'
          }
        },

        drilldown: {
          activeAxisLabelStyle: {
            color: '#F0F0F3'
          },
          activeDataLabelStyle: {
            color: '#F0F0F3'
          }
        },

        navigation: {
          buttonOptions: {
            symbolStroke: '#DDDDDD',
            theme: {
              fill: '#505053'
            }
          }
        },

        // scroll charts
        rangeSelector: {
          buttonTheme: {
            fill: '#505053',
            stroke: '#000000',
            style: {
              color: '#CCC'
            },
            states: {
              hover: {
                fill: '#707073',
                stroke: '#000000',
                style: {
                  color: 'white'
                }
              },
              select: {
                fill: '#000003',
                stroke: '#000000',
                style: {
                  color: 'white'
                }
              }
            }
          },
          inputBoxBorderColor: '#505053',
          inputStyle: {
            backgroundColor: '#333',
            color: 'silver'
          },
          labelStyle: {
            color: 'silver'
          }
        },

        navigator: {
          handles: {
            backgroundColor: '#666',
            borderColor: '#AAA'
          },
          outlineColor: '#CCC',
          maskFill: 'rgba(255,255,255,0.1)',
          series: {
            color: '#7798BF',
            lineColor: '#A6C7ED'
          },
          xAxis: {
            gridLineColor: '#505053'
          }
        },

        scrollbar: {
          barBackgroundColor: '#808083',
          barBorderColor: '#808083',
          buttonArrowColor: '#CCC',
          buttonBackgroundColor: '#606063',
          buttonBorderColor: '#606063',
          rifleColor: '#FFF',
          trackBackgroundColor: '#404043',
          trackBorderColor: '#404043'
        },

        // special colors for some of the
        legendBackgroundColor: 'rgba(0, 0, 0, 0.5)',
        background2: '#505053',
        dataLabelsColor: '#B0B0B3',
        textColor: '#C0C0C0',
        contrastTextColor: '#F0F0F3',
        maskColor: 'rgba(255,255,255,0.3)'
      };

      theme.chart.style.fontFamily = (function family() {
        switch (type) {
          case "chartviewer":
            return "'Lucida Console', monaco, monospace";
          default:
            return "'Unica One', sans-serif";
        }
      })();

      theme.title.style.textTransform = (function family() {
        switch (type) {
          case "chartviewer":
            return "lowercase";
          default:
            return "uppercase";
        }
      })();

      return theme;
    },

    get_highcharts_plotoptions: function () {
      return {
        //connectNulls: false,
        area: {
          fillColor: {
            linearGradient: {
              x1: 0,
              y1: 0,
              x2: 0,
              y2: 1
            },
            stops: [
              [0, Highcharts.getOptions().colors[0]],
              [1, Highcharts.Color(Highcharts.getOptions().colors[0]).setOpacity(0).get('rgba')]
            ]
          },
          marker: {
            radius: 4
          },
          lineWidth: 1,
          states: {
            hover: {
              lineWidth: 1
            }
          },
          threshold: null
        },
      }
    },

    get_highcharts_title: function (options, title) {
      if (typeof options === 'undefined') {
        return {
          title: {
            text: title,
          }
        }
      }
      else {
        var options      = options;
        options['title'] = {
          text: title,
        }
        return options;
      }
    },

    get_highcharts_utc: function () {
      return {
        global: {
          useUTC: false
        }
      }
    },

    //////////////////////////////////////////////////////////////////
    // Graph                                                        //
    /////////////////////////////////////////////////////////////////

    get_tree_thermal_class: function (thermal) {
      switch (thermal) {
        case 1:
          return "tree_color_red";
        case 20:
          return "tree_color_orange";
        case 30:
          return "tree_color_green1";
        case 100:
          return "tree_color_green2";
        default:
          return "tree_color_default";
      }
    },

    get_tree_name_prefix: function (thermal_count) {
      //return "c-" + thermal_count["1"] + " w-" + thermal_count["20"]
      // + " ";
      return "C(" + thermal_count["1"] + ") ";
    },

    get_icon_for_tree: function (type, thermal) {
      switch (type) {
        case "deployment":
          return "fa fa-futbol-o fa-1x " + this.get_tree_thermal_class(thermal);
          break;
        case "process":
          return "fa fa-gear fa-1x " + this.get_tree_thermal_class(thermal);
          break;
        case "desktop":
          return "fa fa-desktop fa-1x " + this.get_tree_thermal_class(thermal);
          break;
        case "appserver":
          return "fa fa-cubes fa-1x " + this.get_tree_thermal_class(thermal);
          break;
        case "webserver":
          return "fa fa-globe fa-1x " + this.get_tree_thermal_class(thermal);
          break;
        case "dbserver":
          return "fa fa-database fa-1x " + this.get_tree_thermal_class(thermal);
          break;
        case "queueserver":
          return "fa fa-ellipsis-h fa-1x " + this.get_tree_thermal_class(thermal);
          break;
        case "cacheserver":
          return "fa fa-tasks fa-1x " + this.get_tree_thermal_class(thermal);
          break;
        default:
          return "fa fa-futbol-o";
      }
    },

    get_network_options: function (nodes) {

      // todo : support dynamic roles
      var deploymentcolor    = 'white';
      var processcolor       = 'white';
      var desktopcolor       = 'white';
      var appservercolor     = 'white';
      var smappservercolor   = 'white';
      var webservercolor     = 'white';
      var smwebservercolor   = 'white';
      var dbservercolor      = 'white';
      var smdbservercolor    = 'white';
      var queueservercolor   = 'white';
      var smqueueservercolor = 'white';
      var cacheservercolor   = 'white';
      var smcacheservercolor = 'white';

      // walk nodes and
      // map thermals for each group to colorcodes
      for (var i = 0; i < nodes.length; i++) {
        // console.log("commonfactory get_network_options nodes: " +
        // JSON.stringify(nodes[i]));

        switch (nodes[i].group) {
          case "deployment":
            deploymentcolor = this.get_thermal_color(nodes[i].thermal);
            break;
          case "process":
            processcolor = this.get_thermal_color(nodes[i].thermal);
            break;
          case "desktop":
            desktopcolor = this.get_thermal_color(nodes[i].thermal);
            break;
          case "appserver":
            appservercolor   = this.get_thermal_color(nodes[i].thermal);
            smappservercolor = this.get_thermal_color(nodes[i].thermal);
            break;
          case "webserver":
            webservercolor   = this.get_thermal_color(nodes[i].thermal);
            smwebservercolor = this.get_thermal_color(nodes[i].thermal);
            break;
          case "dbserver":
            dbservercolor   = this.get_thermal_color(nodes[i].thermal);
            smdbservercolor = this.get_thermal_color(nodes[i].thermal);
            break;
          case "queueserver":
            queueservercolor   = this.get_thermal_color(nodes[i].thermal);
            smqueueservercolor = this.get_thermal_color(nodes[i].thermal);
            break;
          case "cacheserver":
            cacheservercolor   = this.get_thermal_color(nodes[i].thermal);
            smcacheservercolor = this.get_thermal_color(nodes[i].thermal);
            break;
        }
      }

      return {
        autoResize: false,
        height: '100%',
        width: '100%',
        locale: "en",

        interaction: {
          dragNodes: true,
          dragView: true,
          hideEdgesOnDrag: false,
          hideNodesOnDrag: false,
          hover: false,
          hoverConnectedEdges: true,
          keyboard: {
            enabled: false,
            speed: {x: 10, y: 10, zoom: 0.02},
            bindToWindow: true
          },
          multiselect: false,
          navigationButtons: false,
          selectable: true,
          selectConnectedEdges: false,
          tooltipDelay: 300,
          zoomView: true
        },

        edges: {
          scaling: {
            min: 20,
            max: 30,
            label: {
              enabled: true,
              min: 20,
              max: 30,
              maxVisible: 30,
              drawThreshold: 5
            },
            customScalingFunction: function (min, max, total, value) {
              if (max === min) {
                return 0.5;
              }
              else {
                let scale = 1 / (max - min);
                return Math.max(0, (value - min) * scale);
              }
            }
          },
        },

        physics: {
          enabled: false,
          barnesHut: {
            centralGravity: 0.5,
            springLength: 25,
            springConstant: 0.07,
            avoidOverlap: 0.2
          },
          minVelocity: 0.75
        },

        nodes: {
          shape: "dot",
          size: 30,
          font: {
            size: 20,
            color: "white"
          },
          borderWidth: 2
        },

        groups: {
          "deployment": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf1e3',
              size: 70,
              color: deploymentcolor,
            },
            label: "Process"
          },
          "process": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf013',
              size: 20,
              color: processcolor,
            },
            label: "Process"
          },
          "desktop": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf108',
              size: 25,
              color: desktopcolor
            }
          },
          "appserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf1b3',
              size: 60,
              color: appservercolor,
            },
            label: "App Server"
          },
          "smappserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf1b2',
              size: 25,
              color: smappservercolor,
            },
            label: "App Server"
          },
          "webserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf0ac',
              size: 60,
              color: webservercolor,
            },
            label: "Web Server"
          },
          "smwebserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf0ac',
              size: 25,
              color: smwebservercolor,
            },
            label: "Web Server"
          },
          "dbserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf1c0',
              size: 60,
              color: dbservercolor,
            },
            label: "Database"
          },
          "smdbserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf1c0',
              size: 25,
              color: smdbservercolor,
            },
            label: "Database"
          },
          "queueserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf141',
              size: 60,
              color: queueservercolor,
            },
            label: "Queue"
          },
          "smqueueserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf141',
              size: 25,
              color: smqueueservercolor,
            },
            label: "Queue"
          },
          "cacheserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf0ae',
              size: 60,
              color: cacheservercolor,
            },
            label: "Cache"
          },
          "smcacheserver": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf0ae',
              size: 25,
              color: smcacheservercolor,
            },
            label: "Cache"
          },
        }
      }
    }
  };
}]);
