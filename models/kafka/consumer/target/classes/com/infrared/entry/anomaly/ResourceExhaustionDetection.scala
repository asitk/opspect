package com.infrared.entry.anomaly

import com.infrared.entry.Metrics
import com.infrared.entry.anomaly.Anomaly._
import com.infrared.util.CassandraDB._
import com.infrared.util.{JacksonWrapper, Log}

import scala.collection.immutable._
import scala.collection.mutable.HashMap
import scala.util._

/**
 * Created by prashun on 9/8/16.
 */
class ResourceExhaustionDetection(_cluster_replicated: Boolean, _time_range: TimeRange) extends AnomalyTrait {
  private val class_type = ClassType.SYSTEM_METRICS_ANOMALY
  val sub_type = SubType.RESOURCE_EXHAUSTION_DETECTION
  private val cluster_replicated: Boolean = _cluster_replicated
  private val time_range: TimeRange = _time_range
  private var highWaterMarkPresent: Boolean = false
  private var highWaterMark: Double = 0d

  def willExhaustionOccur(avgAccel: Double, avgVel: Double, finalDest: Double, ts: Long): (Boolean, Long) = {
    var dst: Double = 0
    var matched = false
    var first_time = true
    var last_matched_val = 0l
    var last_last_failed_val = 0l
    var last_failed_val = 0l
    var confirm = false
    var stopit = false
    var i: Long = MAX_ETA_FOR_RESOURCE_EXHAUSTION * 60
    var count = 0

    do {
      count += 1
      val t =  i
      dst = avgVel / 60 * t + 0.5 * avgAccel / 3600 * t * t
      if (dst >= finalDest) {
        //Find the earliest point when this is true
        if (first_time) {
          first_time = false
        }
        last_matched_val = t
        matched = true
        val m: Long = i / (60 * 2)
        i = m * 60
        stopit = if (i == 0) true else false
      } else {
        last_last_failed_val = last_failed_val
        last_failed_val = i
        if (first_time) {
          //This means its not going to exhaust in that time skip
          stopit = true
          matched = false
        } else {
          val m: Long = (last_matched_val - i) / (60 * 2)
          i += m * 60
        }
        if (confirm) {
          stopit = true
        }
        if (matched && !confirm && (last_failed_val == last_last_failed_val && last_last_failed_val != 0l)) {
          confirm = true
        }
      }
    } while (!stopit)
    Log.getLogger.trace(s"No of iterations = ${count}")
    if (matched) {
      Log.getLogger.trace(s"Resource exhaustion will occur at ${last_matched_val / 60} minutes")
    }
    (matched, last_matched_val / 60) //In minutes
  }

  private def findETA(x: Array[Metrics.MetricsStats], pos: Int): (Boolean, Long) = {
    //Get the value of the high_water_mark if exists and calculate the time to get there
    val val_stats = getAsDStat(x(pos).val_stats)
    val vel_stats = getAsDStat(x(pos).vel_stats)
    val avg_vel = vel_stats.mean
    val avg_accel = {
      if (pos > 0) {
        val vel_stats_before = getAsDStat(x(pos - 1).vel_stats)
        val avg_vel_before = vel_stats_before.mean
        val time_delta = x(pos).ts - x(pos - 1).ts
        (avg_vel - avg_vel_before) / time_delta
      } else {
        0d
      }
    }

    val x_mean = JacksonWrapper.deserialize[Metrics.DStats](x(pos).val_stats).mean
    willExhaustionOccur(avg_accel, avg_vel, highWaterMark - x_mean, x(pos).ts)
  }

  private def getScore(set: Array[Metrics.MetricsStats], pos: Int): (Double, Long) = {
    var score: Double = 0
    val j: Int = math.min(4, pos)
    var k: Int = 1
    var min_eta: Long = MAX_ETA_FOR_RESOURCE_EXHAUSTION
    var max_score = {
      if (j == 0) 1 else 0
    }
    //We have to get 5 slots here
    for (i <- 1 until j + 2) {
      max_score += i
    }

    for (i <- pos - j until pos + 1) {
      val (status, eta) = findETA(set, i)
      score += k * {
        if (status) {
          min_eta = math.min(min_eta, eta)
          1
        } else 0
      }
      k += 1
    }

    if (max_score > 0) {
      score /= max_score
    }

    (score, min_eta)
  }

  def workOnDistribution(arows: Array[Metrics.MetricsStats]): List[AnomalyCollection] = {
    val t1 = System.currentTimeMillis()
    Log.getLogger.trace(s"Start of ${sub_type}")
    var detections = List[AnomalyCollection]()
    arows.filterNot(_.is_non_numeric)
      .filterNot(_.plugin == "nwgraph")
      .groupBy(x => {
        (x.customer_id, x.deployment_id, x.cluster_id, x.target, x.host_ip, x.plugin, x.classification)
      }).values
      .foreach(v => {
        var windowScore = HashMap[Long,WindowScore]()
        var heatMap = HashMap[Long,Int]()

        val tags = JacksonWrapper.deserialize[Map[String,String]](v.head.tags)
        highWaterMarkPresent = false
        highWaterMark = 0.0d

        try {
          highWaterMark = tags.getOrElse("high_water_mark", "").toString.toDouble
          highWaterMarkPresent = true
        } catch {
          case e: Exception => {}
        }

        if (highWaterMarkPresent) {
          val x = v.sortWith(_.ts < _.ts)
          var min_eta = MAX_ETA_FOR_RESOURCE_EXHAUSTION
          for (i <- x.indices) {
            val (score, eta) = {
              if (simulate) {
                val score = getSimulatedScore
                val eta = (Random.nextDouble()/2 * 100).toLong
                (score, eta)
              } else {
                getScore(x, i)
              }
            }
            min_eta = math.min(eta, min_eta)
            val severity = getSeverity(score)
            val ws = windowScore.getOrElse(i - 1, null)

            if (x(i).ts >= time_range.start && x(i).ts < time_range.end) {
              heatMap += (x(i).ts -> severity)
              windowScore += (x(i).ts -> new WindowScore(score, x(i).ts, GRAIN, severity))
            }
          }

          val ws = getWSFromWindowScore(windowScore.toMap)

          val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)

          if (qs.severity != sevGrades.last.grade) {
            var tags = HashMap[String, String]()
            tags += ("high_water_mark" -> highWaterMark.toString)
            tags += ("eta" -> min_eta.toString)
            x.head.tags.replace(x.head.tags, JacksonWrapper.serialize(tags))
            val message = getMessage(x.head, sub_type, qs, cluster_replicated)
            tags += ("message" -> message)
            if (simulate) tags += ("simulate" -> "true")
            val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap.toMap)
            detections ::= new AnomalyCollection(x.head, sc, tags.toMap)
          }
        }
      })

    val diff = System.currentTimeMillis() - t1
    Log.getLogger.trace(s"End of ${sub_type} - took ${diff} ms")
    detections
  }
}

