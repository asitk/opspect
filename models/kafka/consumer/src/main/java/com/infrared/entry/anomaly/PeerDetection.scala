package com.infrared.entry.anomaly

import com.infrared.entry.Metrics
import com.infrared.entry.anomaly.Anomaly._
import com.infrared.util.CassandraDB._
import com.infrared.util.Log

import scala.collection.immutable._
import scala.collection.mutable.HashMap
/**
 * Created by prashun on 9/8/16.
 */
class PeerDetection(_cluster_replicated: Boolean, _time_range: TimeRange) extends AnomalyTrait{
  private val class_type = ClassType.SYSTEM_METRICS_ANOMALY
  val sub_type = SubType.PEER_DETECTION
  private val cluster_replicated: Boolean = _cluster_replicated

  private val time_range: TimeRange = _time_range

  private def getScore(set: Array[Metrics.MetricsStats], pos: Int): Double = {
    var score: Double = 0
    val j: Int = math.min(4, pos)
    var k: Int = 1
    val avoid_vals = Array[Double](0.0, Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity)
    var max_score = {
      if (j == 0) 1 else 0
    }
    //We have to get 5 slots here
    for (i <- 1 until j + 2) {
      max_score += i
    }

    for (i <- pos - j until pos + 1) {
      score += k * {
        if (avoid_vals.contains(set(i).final_score) || set(i).final_score.toString == "NaN") {
          0
        } else 1
      }
      k += 1
    }
    val fscore = score / max_score
    if (fscore > 1.0) {
      throw new Exception(s"Cant get score > 1 on plugin = ${set.head.plugin} classification = ${set.head.classification} target = ${set.head.target} j = ${j}, pos = ${pos} k = ${k} max_score = ${max_score} and score = ${score}")
    }
    fscore
  }

  def workOnDistribution(arows: Array[Metrics.MetricsStats]): List[AnomalyCollection] = {
    val t1 = System.currentTimeMillis()
    Log.getLogger.trace(s"Start of ${sub_type}")
    var detections = List[AnomalyCollection]()
    arows.filterNot(_.plugin == "nwgraph")
    .filterNot(_.plugin == "procstat")
      .groupBy(x => {
        (x.customer_id, x.deployment_id, x.cluster_id, x.target, x.host_ip, x.plugin, x.classification)
      }).values
      .foreach(v => {
      val x = v.sortWith(_.ts < _.ts)
      var windowScore = HashMap[Long,WindowScore]()
      var heatMap = HashMap[Long,Int]()
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
        val message = getMessage(x.head, sub_type, qs, cluster_replicated)
          var tags = HashMap[String, String]()
          tags += ("message" -> message)
          if (simulate) tags += ("simulate" -> "true")
          val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap.toMap)
          detections ::= new AnomalyCollection(x.head, sc, tags.toMap)
      }
    })
    val diff = System.currentTimeMillis() - t1
    Log.getLogger.trace(s"End of ${sub_type} - took ${diff} ms")
    detections
  }
}
