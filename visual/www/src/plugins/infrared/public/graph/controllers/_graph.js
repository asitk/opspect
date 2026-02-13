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

import 'plugins/infrared/graph/styles/graph.css';

const app = uiModules.get('apps/graph', [
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
                /*{
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
                 },*/ // explorerpanel
                {
                  type: 'layoutGroup',
                  orientation: 'vertical',
                  width: '100%',
                  items: [
                    {
                      type: 'documentGroup',
                      width: '100%',
                      height: '100%',
                      items: [
                        {
                          type: 'documentPanel',
                          title: 'Overview',
                          contentContainer: 'GraphPanel'
                        },
                        {
                         type: 'documentPanel',
                         title: 'Top Costly Requests',
                         contentContainer: 'CostlyDetailsPanel',
                        },
                      ]
                    },
                    /*{
                     type: 'tabbedGroup',
                     width: '100%',
                     height: '55%',
                     items: [{
                     type: 'layoutPanel',
                     title: 'Error List',
                     contentContainer: 'ErrorListPanel'
                     }]
                     }*/
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
    // Network                                                      //
    /////////////////////////////////////////////////////////////////

    /*
     *
     * @param ctx   :  The context object where to draw
     * @param x     :  The x position of the rectangle.
     * @param y     :  The y position of the rectangle.
     * @param w     :  The width of the rectangle.
     * @param h     :  The height of the rectangle.
     * @param text  :  The text we are going to centralize.
     * @param fh    :  The font height (in pixels).
     * @param spl   :  Vertical space between lines
     */
    paint_centered_wrap: function (ctx, x, y, w, h, text, fh, spl) {
      // The painting properties
      // Normally I would write this as an input parameter
      var Paint = {
        RECTANGLE_STROKE_STYLE: 'black',
        RECTANGLE_LINE_WIDTH: 1,
        VALUE_FONT: '12px Arial',
        VALUE_FILL_STYLE: 'red'
      }
      /*
       * @param ctx   : The 2d context
       * @param mw    : The max width of the text accepted
       * @param font  : The font used to draw the text
       * @param text  : The text to be splitted   into
       */
      var split_lines = function (ctx, mw, font, text) {
        // We give a little "padding"
        // This should probably be an input param
        // but for the sake of simplicity we will keep it
        // this way
        mw           = mw - 10;
        // We setup the text font to the context (if not already)
        ctx2d.font   = font;
        // We split the text by words
        var words    = text.split(' ');
        var new_line = words[0];
        var lines    = [];
        for (var i = 1; i < words.length; ++i) {
          if (ctx.measureText(new_line + " " + words[i]).width < mw) {
            new_line += " " + words[i];
          }
          else {
            lines.push(new_line);
            new_line = words[i];
          }
        }
        lines.push(new_line);
        // DEBUG
        // for(var j = 0; j < lines.length; ++j) {
        //    console.log("line[" + j + "]=" + lines[j]);
        // }
        return lines;
      }
      // Obtains the context 2d of the canvas
      // It may return null
      //var ctx2d = canvas.getContext('2d');
      var ctx2d = ctx;
      if (ctx2d) {
        // draw rectangular
        ctx2d.strokeStyle = Paint.RECTANGLE_STROKE_STYLE;
        ctx2d.lineWidth   = Paint.RECTANGLE_LINE_WIDTH;

        ctx2d.fillStyle = "#ffffff";

        //ctx2d.strokeRect(x, y, w, h);

        // Paint text
        var lines = split_lines(ctx2d, w, Paint.VALUE_FONT, text);
        // Block of text height
        var both  = lines.length * (fh + spl);
        if (both >= h) {
          // We won't be able to wrap the text inside the area
          // the area is too small. We should inform the user
          // about this in a meaningful way
        }
        else {
          // We determine the y of the first line
          var ly = (h - both) / 2 + y + spl * lines.length;
          var lx = 0;
          for (var j = 0; j < lines.length; ++j, ly += fh + spl) {
            // We continue to centralize the lines
            lx = x + w / 2 - ctx2d.measureText(lines[j]).width / 2;
            // DEBUG
            // console.log("ctx2d.fillText('" + lines[j] + "', " + lx +
            // ", " + ly + ")");
            ctx2d.fillText(lines[j], lx, ly);
          }
        }
      }
      else {
        // Do something meaningful
      }
    },

    /**
     * Draws a rounded rectangle using the current state of the canvas.
     * If you omit the last three params, it will draw a rectangle
     * outline with a 5 pixel border radius
     * @param {CanvasRenderingContext2D} ctx
     * @param {Number} x The top left x coordinate
     * @param {Number} y The top left y coordinate
     * @param {Number} width The width of the rectangle
     * @param {Number} height The height of the rectangle
     * @param {Number} [radius = 5] The corner radius; It can also be an object
     *                 to specify different radii for corners
     * @param {Number} [radius.tl = 0] Top left
     * @param {Number} [radius.tr = 0] Top right
     * @param {Number} [radius.br = 0] Bottom right
     * @param {Number} [radius.bl = 0] Bottom left
     * @param {Boolean} [fill = false] Whether to fill the rectangle.
     * @param {Boolean} [stroke = true] Whether to stroke the rectangle.
     */
    roundRect: function (ctx, x, y, width, height, radius, fill, stroke, data) {
      if (typeof stroke == 'undefined') {
        stroke = true;
      }
      if (typeof radius === 'undefined') {
        radius = 5;
      }
      if (typeof radius === 'number') {
        radius = {tl: radius, tr: radius, br: radius, bl: radius};
      }
      else {
        var defaultRadius = {tl: 0, tr: 0, br: 0, bl: 0};
        for (var side in defaultRadius) {
          radius[side] = radius[side] || defaultRadius[side];
        }
      }

      ctx.beginPath();
      ctx.moveTo(x + radius.tl, y);
      ctx.lineTo(x + width - radius.tr, y);
      ctx.quadraticCurveTo(x + width, y, x + width, y + radius.tr);

      ctx.lineTo(x + width, y + height - radius.br);
      ctx.quadraticCurveTo(x + width, y + height, x + width - radius.br, y + height);

      ctx.lineTo(x + radius.bl, y + height);
      ctx.quadraticCurveTo(x, y + height, x, y + height - radius.bl);

      ctx.lineTo(x, y + radius.tl);
      ctx.quadraticCurveTo(x, y, x + radius.tl, y);

      // add data

      var section = 20;
      var lborder = 5;
      var rborder = 13;

      ctx.moveTo(x, y + section); // header
      ctx.lineTo(x + width, y + section);

      ctx.moveTo(x, y + section * 2);
      ctx.lineTo(x + width, y + section * 2);

      ctx.moveTo(x + width / 2, y + section); // split
      ctx.lineTo(x + width / 2, y + section * 2);

      ctx.moveTo(x, y + section * 2);
      ctx.lineTo(x + width, y + section * 2);

      ctx.moveTo(x, y + section * 3);
      ctx.lineTo(x + width, y + section * 3);

      ctx.moveTo(x + width / 2, y + section * 3); // split
      ctx.lineTo(x + width / 2, y + section * 5);

      ctx.moveTo(x, y + section * 5);
      ctx.lineTo(x + width, y + section * 5);

      ctx.moveTo(x, y + section * 6);
      ctx.lineTo(x + width, y + section * 6);

      ctx.moveTo(x, y + section * 7);
      ctx.lineTo(x + width, y + section * 7);

      ctx.moveTo(x + width / 2, y + section * 6); // split
      ctx.lineTo(x + width / 2, y + section * 7);

      if (fill) {
        ctx.fill();
      }
      if (stroke) {
        ctx.stroke();
      }

      // add header
      ctx.font      = "8pt Arial";
      ctx.fillStyle = "#ffffff";
      ctx.fillText("Stats", x + lborder, y + rborder);

      // add data
      ctx.fillStyle = "#00ff00";
      ctx.fillText(data.s, x + lborder, y + section + rborder);
      ctx.fillText(data.d, x + width / 2 + lborder, y + section + rborder);

      ctx.fillText(data.avg_response, x + lborder, y + section * 2 + rborder);

      ctx.fillText(data.avg_ttlb, x + lborder, y + section * 3 + rborder);
      ctx.fillText(data.avg_ttfb, x + width / 2 + lborder, y + section * 3 + rborder);
      ctx.fillText(data.max_ttlb, x + lborder, y + section * 4 + rborder);
      ctx.fillText(data.max_ttfb, x + width / 2 + lborder, y + section * 4 + rborder);

      ctx.fillText(data.error_count, x + lborder, y + section * 5 + rborder);

      ctx.fillText(data.sent, x + lborder, y + section * 6 + rborder);
      ctx.fillText(data.recv, x + width / 2 + lborder, y + section * 6 + rborder);

      this.paint_centered_wrap(ctx,
                               x,
                               y + (section * 7),
                               width,
                               height - (section * 7),
                               data.services, 8, 2);

      ctx.closePath();
    },

    create_stats_mapping: function (props) {

      var data = {
        s:"S:" + props.src_ip,
        d:"D:" + props.dst_ip,
        avg_response:"Avg Response:" + props.avg_rsp_size,
        avg_ttlb:"Avg ttlb:" + props.avg_ttlb,
        avg_ttfb:"Avg ttfb:" + props.avg_ttfb,
        max_ttlb:"Max ttlb:" + props.max_ttlb,
        max_ttfb:"Max ttfb:" + props.max_ttfb,
        error_count:"Error count:" + props.error_count,
        sent: "Sent:" + props.sent_bytes,
        recv: "Recv:" + props.recv_bytes,
        services: "Services: " + props.svc_info.name,
      };

      return data;
    },

    display_keys: function (ctx, topright) {

      // display key:
      // var sizeWidth  = ctx.canvas.clientWidth;
      // var sizeHeight = ctx.canvas.clientHeight;

      var x_ico_offset  = 25;
      var x_text_offset = 100;
      var ypad          = 25;

      // fa-flag-checkered f11e -- non replicated
      ctx.font = '20px FontAwesome';
      ctx.fillText('\uf11e', topright.x + x_ico_offset, topright.y);

      ctx.font      = "8pt Arial";
      ctx.fillStyle = "#ffffff";
      ctx.fillText("Non Replicated", topright.x + x_text_offset, topright.y);

      // fa-bomb f1e2 -- critical
      ctx.font = '20px FontAwesome';
      ctx.fillText('\uf1e2', topright.x + x_ico_offset, topright.y + ypad);

      ctx.font      = "8pt Arial";
      ctx.fillStyle = "#ffffff";
      ctx.fillText("Critical", topright.x + x_text_offset, topright.y + ypad);

      // fa-fire  Unicode: f06d  -- hotspotting
      ctx.font = '20px FontAwesome';
      ctx.fillText('\uf06d', topright.x + x_ico_offset, topright.y + ypad * 2);

      ctx.font      = "8pt Arial";
      ctx.fillStyle = "#ffffff";
      ctx.fillText("Hotspotting", topright.x + x_text_offset, topright.y + ypad * 2);

      // fa-spinner  Unicode: f110  -- slow/bottleneck
      ctx.font = '20px FontAwesome';
      ctx.fillText('\uf110', topright.x + x_ico_offset, topright.y + ypad * 3);

      ctx.font      = "8pt Arial";
      ctx.fillStyle = "#ffffff";
      ctx.fillText("Slow Performance", topright.x + x_text_offset, topright.y + ypad * 3);

      // non participation

      // fa-spinner  Unicode: f110  -- slow/bottleneck
      ctx.font = '20px FontAwesome';
      ctx.fillText('\uf05c', topright.x + x_ico_offset, topright.y + ypad * 4);

      ctx.font      = "8pt Arial";
      ctx.fillStyle = "#ffffff";
      ctx.fillText("Non Participation", topright.x + x_text_offset, topright.y + ypad * 4);


      ctx.font      = "8pt Arial";
      ctx.fillStyle = "#ffffff";
      ctx.fillText(" * counts represent instance errors", topright.x + x_text_offset, topright.y + ypad * 5);
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
        //autoResize: true,
        //height: '100%',
        //width: '100%',
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
              //min: 20,
              //max: 30,
              //maxVisible: 30,
              //drawThreshold: 5
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
          "external_user": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf007',
              size: 70,
              color: 'lightskyblue',
            },
            label: "*"
          },

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
              size: 60,
              color: processcolor,
            },
            label: "Process"
          },
          "smprocess": {
            shape: "icon",
            icon: {
              face: "FontAwesome",
              code: '\uf013',
              size: 25,
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
