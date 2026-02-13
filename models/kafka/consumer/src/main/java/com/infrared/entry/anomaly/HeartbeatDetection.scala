package com.infrared.entry.anomaly

import com.infrared.entry.Metrics
import com.infrared.entry.Metrics.MetricsStats
import com.infrared.entry.anomaly.Anomaly._
import com.infrared.entry.infra.Cluster._
import com.infrared.entry.infra.Node
import com.infrared.entry.infra.Node._
import com.infrared.util.CassandraDB._
import com.infrared.util.Log

import scala.collection.immutable._
import scala.collection.mutable.HashMap

/**
 * Created by prashun on 9/8/16.
 */
class HeartbeatDetection(_cluster_map: ClusterMap, _time_range: TimeRange) extends AnomalyTrait {
  SINGLE_MAJORITY = 0.08d
  ABS_MAJORITY = 0.2d
  private val class_type = ClassType.SYSTEM_METRICS_ANOMALY
  private val sub_type = SubType.HEARTBEAT_DETECTION
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
    //We have to get 4 slots here
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

  private[this] case class Slot(ts: Long, present: Boolean)

  def workOnDistribution(_arows: Array[Metrics.MetricsStats]): List[AnomalyCollection] = {
    val t1 = System.currentTimeMillis()
    Log.getLogger.trace(s"Start of ${sub_type}")
    val arows = _arows.filter(_.plugin == "system").filter(_.classification == "uptime")
    var detections = List[AnomalyCollection]()

    val host_map = HashMap[String, NodeMap]()
    val nm = Node.getMaps(Some(cluster_map.customer_id), Some(cluster_map.deployment_id),
      Some(cluster_map.cluster_id), None, Some(true), Some(time_range))
    nm.foreach(x => {
      host_map += (x.host_ip -> x)
    })

    //Now find out of the uniq IP how many missing metrics reads
    arows.groupBy(x => {
      (x.customer_id, x.deployment_id, x.cluster_id, x.target, x.host_ip, x.plugin, x.classification)
    }).values
      .foreach(v => {
        host_map.remove(v.head.host_ip)
        var tshm = HashMap[Long,Boolean]()
        v.foreach(x => {
          tshm += (x.ts -> true)
        })

        var windowScore = HashMap[Long,WindowScore]()
        var heatMap = HashMap[Long,Int]()
        for (i <- time_range.start until time_range.end by GRAIN * 60 * 1000) {
          if (!tshm.contains(i)) {
            tshm += (i -> false)
          }
        }
        val x = tshm.toList.sortWith(_._1 < _._1).map(x => {
          new Slot(x._1, x._2)
        }).toArray
        //Sliding window of 5 backwards on the array
        for (i <- x.indices) {
          val score = {
            if (simulate) getSimulatedScore else getScore(x, i)
          }
          val severity = getSeverity(score)

          if (x(i).ts >= time_range.start && x(i).ts < time_range.end) {
            heatMap += (x(i).ts -> severity)
            windowScore += (x(i).ts -> new WindowScore(score, x(i).ts, GRAIN, severity))
          }
        }

        val ws = getWSFromWindowScore(windowScore.toMap)

        val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)

        if (qs.severity != sevGrades.last.grade) {
          val z = v.head
          val ms = new MetricsStats(time_range.start, z.customer_id, z.deployment_id, z.cluster_id, z.target, z.host_ip, z.host_name,
            "host", "uptime", 0, 0, z.is_non_numeric, "{}", "{}", "{}", "")
          val message = getMessage(ms, sub_type, qs, cluster_replicated)
          var tags = HashMap[String, String]()
          tags += ("message" -> message)
          if (simulate) tags += ("simulate" -> "true")
          val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap.toMap)
          detections ::= new AnomalyCollection(ms, sc, tags.toMap)
        }
      })

    //The remaining ips in the host_map are nodes that have blipped out
    host_map.toList.foreach(x => {
      var windowScore = HashMap[Long,WindowScore]()
      var heatMap = HashMap[Long,Int]()
      val score = 1
      val severity = getSeverity(score)
      for (i <- time_range.start until time_range.end by GRAIN * 60 * 1000) {
        heatMap += (i -> severity)
        windowScore += (i -> new WindowScore(score, i, GRAIN, severity))
      }

      val ws = getWSFromWindowScore(windowScore.toMap)

      val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)

      if (qs.severity != sevGrades.last.grade) {
        val z = x._2
        val ms = new MetricsStats(time_range.start, z.customer_id, z.deployment_id, z.cluster_id, "*", z.host_ip, z.host_name,
          "host", "uptime", 0, 0, false, "{}", "{}", "{}", "")

        val message = getMessage(ms, sub_type, qs, cluster_replicated)
        var tags = HashMap[String, String]()
        tags += ("message" -> message)
        if (simulate) tags += ("simulate" -> "true")
        val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap.toMap)
        detections ::= new AnomalyCollection(ms, sc, tags.toMap)
      }
    })

    val diff = System.currentTimeMillis() - t1
    Log.getLogger.trace(s"End of ${sub_type} - took ${diff} ms")
    detections
  }
}
