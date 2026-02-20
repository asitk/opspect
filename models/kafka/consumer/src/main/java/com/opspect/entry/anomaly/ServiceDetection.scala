package com.opspect.entry.anomaly

/** Created by prashun on 12/8/16.
  */

import com.opspect.entry.Metrics.MetricsStats
import com.opspect.entry.anomaly.Anomaly._
import com.opspect.entry.infra.Cluster
import com.opspect.entry.infra.Node._
import com.opspect.entry.{Metrics, NWObject}
import com.opspect.util.CassandraDB._
import com.opspect.util.Log

import scala.collection.immutable._
import scala.collection.mutable.HashMap

/** Created by prashun on 9/8/16.
  */
class ServiceDetection(_cluster_map: Cluster.ClusterMap, _time_range: TimeRange)
    extends AnomalyTrait {
  SINGLE_MAJORITY = 0.03d
  ABS_MAJORITY = 0.1d
  private val class_type = ClassType.SYSTEM_METRICS_ANOMALY
  private val sub_type = SubType.SERVICE_DETECTION
  private val cluster_map = _cluster_map
  private val cluster_replicated: Boolean = cluster_map.replicated

  private val time_range: TimeRange = _time_range

  private def getScore(set: Array[Slot], pos: Int): Double = {
    var score: Double = 0
    val j: Int = math.min(4, pos)
    var k: Int = 1
    var max_score = {
      if (j == 0) 1 else 0
    }
    // We have to get 4 slots here
    for (i <- 1 until j + 2) {
      max_score += i
    }

    for (i <- pos - j until pos + 1) {
      score += k * {
        if (set(i).present) 0 else 1
      }
      k += 1
    }

    score / max_score
  }

  def getUniqServicesByCluster(
      arows: Array[Metrics.MetricsStats]
  ): Map[(String, String), String] = {
    // Just read any entries from the DB if any. This will be updated the next minute
    val nw_svc_list = NWObject.getServiceListFromDB(
      cluster_map.customer_id,
      cluster_map.deployment_id,
      time_range
    )
    val service_map = HashMap[(String, String), String]()

    nw_svc_list.foreach(x => {
      x.nw_svc_info_list
        .filter(_.cluster_id == cluster_map.cluster_id)
        .foreach(y => {
          val key = (y.cluster_id, y.name)
          service_map += (key -> y.name)
        })
    })
    service_map.toMap
  }

  private[this] case class Slot(ts: Long, present: Boolean)

  // Works only for a given cluster
  def workOnDistribution(
      _arows: Array[Metrics.MetricsStats]
  ): List[AnomalyCollection] = {
    var detections = List[AnomalyCollection]()
    val t1 = System.currentTimeMillis()
    Log.getLogger.trace(s"Start of ${sub_type}")
    val arows = _arows
      .filter(_.plugin == "procstat")
      .filter(_.classification == "memory_rss")

    // Group the services together first by cluster
    val service_map = getUniqServicesByCluster(_arows)

    val svc_map = HashMap[(String, String, String), NodeMap]()
    val nm = getMaps(
      Some(cluster_map.customer_id),
      Some(cluster_map.deployment_id),
      Some(cluster_map.cluster_id),
      None,
      Some(true),
      Some(time_range)
    )
    nm.foreach(x => {
      service_map.foreach(y => {
        val (cluster_id, target) = y._1
        val key = (cluster_id, target, x.host_ip)
        svc_map += (key -> x)
      })
    })

    arows
      .groupBy(x => {
        (
          x.customer_id,
          x.deployment_id,
          x.cluster_id,
          x.host_ip,
          x.plugin,
          x.target
        )
      })
      .values
      .foreach(v => {
        val key = (v.head.cluster_id, v.head.target, v.head.host_ip)
        svc_map.remove(key)

        var tshm = HashMap[Long, Boolean]()
        v.foreach(x => {
          tshm += (x.ts -> true)
        })
        var windowScore = HashMap[Long, WindowScore]()
        var heatMap = HashMap[Long, Int]()
        for (i <- time_range.start until time_range.end by GRAIN * 60 * 1000) {
          if (!tshm.contains(i)) {
            tshm += (i -> false)
          }
        }
        val x = tshm.toList
          .sortWith(_._1 < _._1)
          .map(x => {
            new Slot(x._1, x._2)
          })
          .toArray
        // Sliding window of 5 backwards on the array
        for (i <- x.indices) {
          val score = {
            if (simulate) getSimulatedScore else getScore(x, i)
          }
          val severity = getSeverity(score)

          if (x(i).ts >= time_range.start && x(i).ts < time_range.end) {
            heatMap += (x(i).ts -> severity)
            windowScore += (x(i).ts -> new WindowScore(
              score,
              x(i).ts,
              GRAIN,
              severity
            ))
          }
        }

        val ws = getWSFromWindowScore(windowScore.toMap)

        val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)

        if (qs.severity != sevGrades.last.grade) {
          val z = v.head
          val ms = new MetricsStats(
            time_range.start,
            z.customer_id,
            z.deployment_id,
            z.cluster_id,
            z.target,
            z.host_ip,
            z.host_name,
            "service",
            "uptime",
            0,
            0,
            false,
            z.val_stats,
            z.vel_stats,
            z.tags,
            z.val_str
          )
          val message = getMessage(ms, sub_type, qs, cluster_replicated)
          var tags = HashMap[String, String]()
          tags += ("message" -> message)
          if (simulate) tags += ("simulate" -> "true")
          val sc = new SeverityClassification(
            class_type,
            sub_type,
            qs,
            ws,
            heatMap.toMap
          )
          detections ::= new AnomalyCollection(ms, sc, tags.toMap)
        }
      })

    // Now go through the rest of the svc_map
    svc_map.toList.foreach(x => {
      val (cluster_id, target, host_ip) = x._1
      val svc_name = target
      val nm = x._2
      var windowScore = HashMap[Long, WindowScore]()
      var heatMap = HashMap[Long, Int]()
      val score = 1
      val severity = getSeverity(score)
      for (i <- time_range.start until time_range.end by GRAIN * 60 * 1000) {
        heatMap += (i -> severity)
        windowScore += (i -> new WindowScore(score, i, GRAIN, severity))
      }

      val ws = getWSFromWindowScore(windowScore.toMap)

      val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
      val z = nm
      val ms = new MetricsStats(
        time_range.start,
        z.customer_id,
        z.deployment_id,
        z.cluster_id,
        svc_name,
        z.host_ip,
        z.host_name,
        "service",
        "uptime",
        0,
        0,
        false,
        "{}",
        "{}",
        "{}",
        ""
      )

      val message = getMessage(ms, sub_type, qs, cluster_replicated)
      var tags = HashMap[String, String]()
      tags += ("message" -> message)
      if (simulate) tags += ("simulate" -> "true")
      val sc =
        new SeverityClassification(class_type, sub_type, qs, ws, heatMap.toMap)
      detections ::= new AnomalyCollection(ms, sc, tags.toMap)
    })
    val diff = System.currentTimeMillis() - t1
    Log.getLogger.trace(s"End of ${sub_type} - took ${diff} ms")
    detections
  }
}
