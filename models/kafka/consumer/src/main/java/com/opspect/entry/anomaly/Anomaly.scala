package com.opspect.entry.anomaly

import com.opspect.entry.Metrics
import com.opspect.entry.Metrics.MetricsStats
import com.opspect.util.CassandraDB._
import com.opspect.util.JacksonWrapper

import scala.collection.immutable._
import scala.collection.mutable.HashMap
import scala.util._

/** Created by prashun on 9/6/16.
  */
object Anomaly {
  protected[anomaly] var simulate: Boolean = false
  val PRESET_MILLI = 5 * 60 * 1000
  val GRAIN = 1
  protected[anomaly] val MAX_ETA_FOR_RESOURCE_EXHAUSTION: Long = 24 * 60
  // 1 day in minutes
  protected[anomaly] var ABS_MAJORITY = 0.5d
  // Refers to Percent on 1
  protected[anomaly] var SINGLE_MAJORITY = 0.25d

  val CRITICAL = 1
  val WARN = 20
  val INFO = 30
  val NOTAPROBLEM = 100

  val sevGrades = Array(
    SevBucket(CRITICAL, 0.5, 1.0, "critical"),
    SevBucket(WARN, 0.3, 0.5, "warn"),
    SevBucket(INFO, 0.1, 0.3, "info"),
    SevBucket(NOTAPROBLEM, 0, 0.1, "healthy")
  )

  def setSimulationMode(mode: Boolean): Unit = {
    simulate = mode
  }

  def getSevGrades: Array[SevBucket] = {
    sevGrades
  }

  def getSimulationMode: Boolean = {
    simulate
  }

  def getSimulatedScore: Double = {
    val a = Random.nextBoolean()
    val b = Random.nextBoolean()
    val c = Random.nextBoolean()
    val ret = {
      if (a | b & c) Random.nextDouble() else 0
    }
    ret
  }

  case class SevBucket(grade: Int, min: Double, max: Double, name: String)

  case class WindowScore(
      score: Double,
      startTime: Long,
      duration: Int,
      scoreType: Int
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class AnomalyCollection(
      node: Metrics.MetricsStats,
      anomaly: SeverityClassification,
      tags: Map[String, String]
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class HeatMapDetail(startTime: Long, duration: Int, scoreType: Int)
      extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class HeatMapDetailWithScore(
      startTime: Long,
      duration: Int,
      scoreType: Int,
      score: Double
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class QuantizedSeverity(
      severity: Int,
      total_severity_duration: Int,
      single_max_severity_duration: Int,
      total_duration: Int
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class SeverityClassification(
      cls: String,
      subType: String,
      qs: QuantizedSeverity,
      ws: Map[Int, Array[WindowScore]],
      hm: Map[Long, Int]
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

}

trait AnomalyTrait {

  import Anomaly._

  def getHeatMapFromWindowScore(
      _wsList: Map[Int, Array[WindowScore]]
  ): List[HeatMapDetail] = {
    val wsList = unfoldWindowScore(_wsList)

    var hm = List[HeatMapDetail]()
    wsList.foreach(x => {
      x._2.foreach(y => {
        hm :+= new HeatMapDetail(y.startTime, y.duration, y.scoreType)
      })
    })
    hm.sortWith(_.startTime < _.startTime)
  }

  def getHeatMapWithScoreFromWindowScore(
      _wsList: Map[Int, Array[WindowScore]]
  ): List[HeatMapDetailWithScore] = {
    val wsList = unfoldWindowScore(_wsList)

    var hm = List[HeatMapDetailWithScore]()
    wsList.foreach(x => {
      x._2.foreach(y => {
        hm :+= new HeatMapDetailWithScore(
          y.startTime,
          y.duration,
          y.scoreType,
          y.score
        )
      })
    })
    hm.sortWith(_.startTime < _.startTime)
  }

  def getResultantWindowScore(
      wsList: List[Map[Int, Array[WindowScore]]]
  ): Map[Int, Array[WindowScore]] = {
    var hm = HashMap[Long, WindowScore]()
    wsList.foreach(x => {
      val ws = unfoldWindowScore(x)
      ws.foreach(x => {
        val (i, wsArr) = x
        wsArr.foreach(x => {
          val y = hm.getOrElse(x.startTime, null)
          if (y == null) {
            hm += (x.startTime -> new WindowScore(
              x.score,
              x.startTime,
              x.duration,
              x.scoreType
            ))
          } else {
            if (y.scoreType > x.scoreType) {
              hm.remove(x.startTime)
              hm += (x.startTime -> new WindowScore(
                x.score,
                x.startTime,
                x.duration,
                x.scoreType
              ))
            }
            if (y.scoreType == x.scoreType) {
              hm.remove(x.startTime)
              hm += (x.startTime -> new WindowScore(
                (y.score + x.score) / 2,
                x.startTime,
                x.duration,
                x.scoreType
              ))
            }
          }
        })
      })
    })

    var ws = HashMap[Int, Array[WindowScore]]()
    hm.foreach(x => {
      val (ts, y) = x
      var z = ws.getOrElse(y.scoreType, null)
      if (z == null) {
        ws += (y.scoreType -> Array(y))
      } else {
        ws.remove(y.scoreType)
        z :+= y
        ws += (y.scoreType -> z)
      }
    })

    var ws_opt = HashMap[Int, Array[WindowScore]]()
    ws.foreach(x => {
      val (k, v) = x
      val v1 = getOptimizedWindowScore(v)
      ws_opt += (k -> v1)
    })
    ws_opt.toMap
  }

  def unfoldWindowScore(
      wsList: Map[Int, Array[WindowScore]]
  ): Map[Int, Array[WindowScore]] = {
    var ws = HashMap[Int, Array[WindowScore]]()
    wsList.toList.foreach(kv => {
      val hm = kv._2
      if (hm != null) {
        hm.sortWith(_.startTime < _.startTime)
          .foreach(x => {
            var wsl = Array[WindowScore]()
            for (i <- 0 until x.duration) {
              wsl :+= new WindowScore(
                x.score,
                x.startTime + i * 60 * 1000,
                1,
                x.scoreType
              )
            }
            val key = x.scoreType
            var v = ws.getOrElse(key, Array[WindowScore]())
            if (v.length > 0) {
              ws.remove(key)
            }

            ws += (key -> (v ++ wsl))
          })
      }
    })
    ws.toMap
  }

  def getResultantHeatMap(
      hmList: List[HashMap[Long, Int]],
      time_range: TimeRange
  ): List[HeatMapDetail] = {
    // This is done minute by minute
    var heatMap = HashMap[Long, Int]()
    hmList.foreach(hm => {
      hm.foreach(x => {
        val y: Int = heatMap.getOrElse(x._1, -1)
        if (y == -1) {
          // No one home
          heatMap += (x._1 -> x._2)
        } else {
          if (y > x._2) {
            heatMap.remove(x._1)
            heatMap += (x._1 -> x._2)
          }
        }
      })
    })

    var optHeatMap = Array[HeatMapDetail]()
    var i = -1
    heatMap.toList
      .sortWith(_._1 < _._1)
      .foreach(x => {
        if (i == -1) {
          optHeatMap :+= new HeatMapDetail(x._1, GRAIN, x._2)
          i += 1
        } else {
          val y = optHeatMap(i)
          val next_slot = y.startTime + (y.duration * 60000) // In ms

          if (next_slot == x._1 && optHeatMap(i).scoreType == x._2) {
            optHeatMap.update(
              i,
              new HeatMapDetail(y.startTime, y.duration + GRAIN, y.scoreType)
            )
          } else {
            // If ts is after a gap or different
            optHeatMap :+= new HeatMapDetail(x._1, GRAIN, x._2)
            i += 1
          }
        }
      })
    if (optHeatMap.length == 0) {
      val duration =
        ((time_range.end - time_range.start) / (GRAIN * 60000)).toInt + 1
      optHeatMap :+= new HeatMapDetail(
        time_range.start,
        duration,
        sevGrades.last.grade
      )
    }

    optHeatMap.toList
  }

  def getOptimizedHeatMapWithScore(
      hmList: List[HeatMapDetailWithScore],
      time_range: TimeRange
  ): List[HeatMapDetailWithScore] = {
    var heatMap = Array[HeatMapDetailWithScore]()
    var v = Array[HeatMapDetailWithScore]()
    var i = 0
    val u = hmList.toArray
    for (ts <- time_range.start until time_range.end by 60 * 1000) {
      if (i < u.length && u(i).startTime == ts) {
        v :+= u(i)
        i += 1
      } else {
        v :+= new HeatMapDetailWithScore(ts, 1, sevGrades.last.grade, 0.0)
      }
    }
    var next_ts = 0L
    var j = 0
    for (i <- v.indices) {
      if (v(i).startTime != next_ts) {
        heatMap :+= v(i)
        j += 1
      } else {
        val y = heatMap(j - 1)
        if (y.scoreType == v(i).scoreType) {
          val score = (y.score * y.duration + v(i).score * v(
            i
          ).duration) / (y.duration + v(i).duration).toDouble
          heatMap(j - 1) = new HeatMapDetailWithScore(
            y.startTime,
            y.duration + v(i).duration,
            y.scoreType,
            score
          )
        } else {
          heatMap :+= v(i)
          j += 1
        }
      }
      next_ts = v(i).startTime + (v(i).duration * 60 * 1000)
    }
    heatMap.toList
  }
  def quantizeThermals(
      thermals: Array[Int],
      cluster_replicated: Boolean
  ): Int = {
    var thermal_map = HashMap[Int, Int]()
    sevGrades.foreach(x => {
      thermal_map += (x.grade -> 0)
    })
    var total_count: Int = 0
    thermals.foreach(x => {
      total_count += 1
      val tmp = thermal_map.getOrElseUpdate(x, 0)
      thermal_map.remove(x)
      thermal_map += (x -> (tmp + 1))
    })

    var thermal: Int = sevGrades.last.grade
    var found: Boolean = false
    sevGrades.foreach(x => {
      if (!found) {
        val x_count = thermal_map.getOrElse(x.grade, 0)
        if (
          (!cluster_replicated && x_count > 0) || x_count > 0.5 * total_count
        ) {
          found = true
          thermal = x.grade
        }
      }
    })

    thermal
  }

  def getQuantizedSeverity(
      wslist: List[Map[Int, Array[WindowScore]]],
      cluster_replicated: Boolean,
      time_range: TimeRange
  ): QuantizedSeverity = {
    val sev_ws = sevGrades.map(x => {
      HashMap[Long, WindowScore]()
    })

    wslist.foreach(_ws => {
      val ws = unfoldWindowScore(_ws)
      sevGrades.foreach(sev => {
        val i = sevGrades.indexOf(sev)

        ws.getOrElse(sev.grade, Array())
          .foreach(x => {
            val a = sev_ws(i).getOrElse(x.startTime, null)
            if (a == null) {
              sev_ws(i) += (x.startTime -> x)
            } else {
              // a.scoreType is guaranteed to be x.scoreType
              if (a.score != math.max(a.score, x.score)) {
                val newws =
                  new WindowScore(x.score, x.startTime, x.duration, x.scoreType)
                sev_ws(i).remove(x.startTime)
                sev_ws(i) += (x.startTime -> newws)
              }
            }
          })
      })
    })

    val total_duration: Int = {
      val ts = ((time_range.end - time_range.start) / (GRAIN * 60000)).toInt
      ts
    }

    val optSevWS = sevGrades.map(x => {
      getOptimizedWindowScore(sev_ws(sevGrades.indexOf(x)).toArray.map(_._2))
    })

    val sevQS = sevGrades.map(x => {
      new QuantizedSeverity(x.grade, 0, 0, total_duration)
    })

    optSevWS.foreach(sevWS => {
      val i = optSevWS.indexOf(sevWS)
      sevWS.foreach(ws => {
        if (ws.scoreType == sevGrades(i).grade) {
          val tmp = new QuantizedSeverity(
            sevQS(i).severity,
            sevQS(i).total_severity_duration + ws.duration,
            math.max(sevQS(i).single_max_severity_duration, ws.duration),
            total_duration
          )
          sevQS(i) = tmp
        } else {
          throw new Exception(
            "Getting a window_score type which is not defined in the grades bucket"
          )
        }
      })
    })

    val quantized_severity: QuantizedSeverity = {
      // Criteria should be highest scoreType given priority such that it should have atleast spent
      // 50% of the duration in that state or single max duration is at least 10% of the total duration
      // of the distribution
      var found = false
      var qs: QuantizedSeverity = new QuantizedSeverity(
        sevGrades.last.grade,
        total_duration,
        total_duration,
        total_duration
      )
      sevQS.foreach(x => {
        if (!found) {
          val i = sevQS.indexOf(x)
          if (x.total_severity_duration > 0) {
            if (
              !cluster_replicated ||
              (x.total_severity_duration > ABS_MAJORITY * total_duration) || (x.single_max_severity_duration >= SINGLE_MAJORITY * total_duration)
            ) {
              found = true
              qs = new QuantizedSeverity(
                sevGrades(i).grade,
                x.total_severity_duration,
                x.single_max_severity_duration,
                total_duration
              )
            }
          }
        }
      })
      qs
    }

    quantized_severity
  }

  protected def getOptimizedWindowScore(
      wsArr: Array[WindowScore]
  ): Array[WindowScore] = {
    val wsArray = wsArr.sortWith(_.startTime < _.startTime)
    var optWS = Array[WindowScore]()
    var i = -1
    wsArray.foreach(x => {
      if (x != null) {
        if (i == -1) {
          optWS :+= new WindowScore(
            x.score,
            x.startTime,
            x.duration,
            x.scoreType
          )
          i += 1
        } else {
          val ws = optWS(i)
          val next_slot = ws.startTime + (ws.duration * 60000) // In ms

          if (x.startTime == next_slot) {
            val score = (ws.score + x.score) / 2
            val duration = ws.duration + GRAIN
            optWS.update(
              i,
              new WindowScore(score, ws.startTime, duration, ws.scoreType)
            )
          } else {
            optWS :+= new WindowScore(
              x.score,
              x.startTime,
              x.duration,
              x.scoreType
            )
            i += 1
          }
        }
      }
    })
    optWS
  }

  def getAsDStat(jsonStr: String): Metrics.DStats = {
    JacksonWrapper.deserialize[Metrics.DStats](jsonStr)
  }

  protected def areValuesInTheSameBucket(a: Double, b: Double): Boolean = {
    var status: Boolean = false
    if (getSeverity(a) == getSeverity(b)) {
      status = true
    }
    status
  }

  protected def getSeverity(score: Double): Int = {
    sevGrades.foreach(x => {
      if (score > x.min && score <= x.max) {
        return x.grade
      }
    })
    sevGrades.last.grade
  }

  protected def getWSFromWindowScore1(
      windowScore: Map[Long, WindowScore]
  ): Map[Int, Array[WindowScore]] = {
    var ws = HashMap[Int, Array[WindowScore]]()

    windowScore.toArray
      .sortWith(_._1 < _._1)
      .map(_._2)
      .foreach(x => {
        var y = ws.getOrElse(x.scoreType, null)
        if (y == null) {
          ws += (x.scoreType -> Array(x))
        } else {
          var z = y :+ x
          ws.remove(x.scoreType)
          ws += (x.scoreType -> z)
        }
      })
    ws.toMap
  }

  protected def getWSFromWindowScore(
      windowScore: Map[Long, WindowScore]
  ): Map[Int, Array[WindowScore]] = {
    var ws = HashMap[Int, Array[WindowScore]]()

    windowScore.toArray
      .sortWith(_._1 < _._1)
      .map(_._2)
      .foreach(x => {
        var y = ws.getOrElse(x.scoreType, null)
        if (y == null) {
          ws += (x.scoreType -> Array(x))
        } else {
          var z = Array[WindowScore]()
          if (
            (y.last.startTime + (60 * 1000 * y.last.duration)) == x.startTime
          ) {
            for (i <- 0 until y.length - 1) {
              z :+= y(i)
            }
            val a = new WindowScore(
              math.min(y.last.score, x.score),
              y.last.startTime,
              y.last.duration + x.duration,
              y.last.scoreType
            )
            z :+= a
          } else {
            z = y :+ x
          }
          ws.remove(x.scoreType)
          ws += (x.scoreType -> z)
        }
      })
    ws.toMap
  }

  protected def getMessage(
      node_info: MetricsStats,
      sub_type: String,
      qs: QuantizedSeverity,
      cluster_replicated: Boolean
  ): String = {
    val tags = JacksonWrapper.deserialize[Map[String, String]](node_info.tags)
    val pct1: Double = qs.total_severity_duration * 1.0d / qs.total_duration
    val pct2: Double =
      qs.single_max_severity_duration * 1.0d / qs.total_duration
    val state = sevGrades.filter(_.grade == qs.severity)(0).name
    val minutes_str = "minutes"
    val minute_str = "minute"

    val base_message = {
      if (!cluster_replicated) {
        if (qs.single_max_severity_duration > 0) {
          qs.severity match {
            case CRITICAL =>
              s"Results in a single point of failure as its not fault-tolerant"
            case WARN =>
              s"Results in a possible single point of failure as its not fault-tolerant"
            case INFO =>
              s"Gives information that might lead to possible single point of failure as its not fault-tolerant"
            case NOTAPROBLEM => s"Healthy"
          }
        }
      } else {
        if (pct1 > ABS_MAJORITY) {
          s"In ${state} state for a total of ${qs.total_severity_duration} " +
            s"${if (qs.total_severity_duration > 1) minutes_str else minute_str} of the ${qs.total_duration} " +
            s"${if (qs.total_duration > 1) minutes_str else minute_str}"
        } else {
          if (pct2 >= SINGLE_MAJORITY) {
            s"In ${state} state continuously for ${qs.single_max_severity_duration} " +
              s"${if (qs.single_max_severity_duration > 1) minutes_str
                else minute_str} out of the ${qs.total_duration} " +
              s"${if (qs.total_duration > 1) minutes_str else minute_str}"
          } else {
            // Nothing is wrong
            s"In ${state} state"
          }
        }
      }
    }

    val rel_message = {
      sub_type match {
        case SubType.PEER_DETECTION      => s"compared to its peers"
        case SubType.THRESHOLD_DETECTION => {
          val threshold = {
            tags.getOrElse("high_water_mark", "100")
          }
          s"as its value is above the high water mark of ${threshold}"
        }
        case SubType.ERROR_DETECTION => s"due to errors encountered"
        case SubType.RESOURCE_EXHAUSTION_DETECTION => {
          val (threshold, eta) = {
            val threshold = tags.getOrElse("high_water_mark", "100")
            val eta = tags.getOrElse("min_eta", Long.MaxValue.toString).toLong
            (threshold, eta)
          }
          if (eta <= MAX_ETA_FOR_RESOURCE_EXHAUSTION) {
            s"indicating possible resource outage within ${eta} minutes"
          } else {
            s"with no possibility of resource outage in the coming ${MAX_ETA_FOR_RESOURCE_EXHAUSTION} minutes"
          }
        }
        case SubType.HEARTBEAT_DETECTION => {
          "as it failed to detect its presence"
        }
        case SubType.SERVICE_DETECTION => {
          s"for ${node_info.target} service"
        }
        case SubType.WHALE_USAGE_DETECTION => {
          node_info.classification match {
            case NWPlgType.AVG_RSP_SIZE =>
              s"possibly leading to starvation of available network bandwidth as the average response size of this ${node_info.target} service indicates whale usage"
          }
        }
        case SubType.CHATTY_USAGE_DETECTION => {
          node_info.classification match {
            case NWPlgType.AVG_RSP_SIZE =>
              s"where the average reponse size indicates chatty usage of this ${node_info.target} service"
          }
        }
        case SubType.POOR_PERFORMANCE_DETECTION => {
          node_info.classification match {
            case NWPlgType.AVG_TTFB =>
              s"resulting in poor performance due to slower response Time-To-First-Byte of this ${node_info.target} service wrt its peers"
            case NWPlgType.AVG_TTLB =>
              s"resulting in poor performance due to slower response Time-To-Last-Byte of this ${node_info.target} service wrt its peers"
            case NWPlgType.MAX_TTFB =>
              s"resulting in poor performance due to sporadic slower response Time-To-First-Byte of this ${node_info.target} service wrt its peers"
            case NWPlgType.MAX_TTLB =>
              s"resulting in poor performance due to sporadic slower response Time-To-Last-Byte of this ${node_info.target} service wrt its peers"
          }
        }

        case SubType.HIGH_PERFORMANCE_DETECTION => {
          node_info.classification match {
            case NWPlgType.AVG_TTFB =>
              s"resulting in better performance due to faster response Time-To-First-Byte of this ${node_info.target} service wrt its peers"
            case NWPlgType.AVG_TTLB =>
              s"resulting in better performance due to faster response Time-To-Last-Byte of this ${node_info.target} service wrt its peers"
            case NWPlgType.MAX_TTFB =>
              s"resulting in better performance due to sporadic faster response Time-To-First-Byte of this ${node_info.target} service wrt its peers"
            case NWPlgType.MAX_TTLB =>
              s"resulting in better performance due to sporadic faster response Time-To-Last-Byte of this ${node_info.target} service wrt its peers"
          }
        }

        case SubType.HOTSPOTTING_DETECTION => {
          node_info.classification match {
            case NWPlgType.SENT_BYTES =>
              s"due to potential hotspotting or whale usage of this ${node_info.target} service wrt its peers"
            case NWPlgType.RECV_BYTES =>
              s"due to potential hotspotting or whale usage of this ${node_info.target} service wrt its peers"
            case NWPlgType.REQUEST_COUNT =>
              s"due to hotspotting of this ${node_info.target} service wrt its peers"
            case NWPlgType.DURATION =>
              s"due to (potential hotspotting/poor performance/whale usage) of this ${node_info.target} service wrt its peers"
          }
        }
        case SubType.UNDER_UTILIZED_DETECETION => {
          node_info.classification match {
            case NWPlgType.SENT_BYTES =>
              s"resulting in potential under-utilization of this ${node_info.target} service wrt its peers"
            case NWPlgType.RECV_BYTES =>
              s"resulting in potential under-utilization of this ${node_info.target} service wrt its peers"
            case NWPlgType.REQUEST_COUNT =>
              s"due to under-utilization of this ${node_info.target} service wrt its peers"
            case NWPlgType.DURATION =>
              s"due to (potential under-utilization/higher performance) of this ${node_info.target} service wrt its peers"
          }
        }
        case SubType.NETWORK_ERRORS_OVER_PEERS_DETECTION => {
          node_info.classification match {
            case NWPlgType.ERROR_COUNT =>
              s"due to more no of errors reported from this ${node_info.target} service wrt its peers"
          }
        }

        case SubType.NETWORK_ERRORS_DETECTION => {
          node_info.classification match {
            case NWPlgType.ERROR_COUNT =>
              s"due to errors reported from this ${node_info.target} service"
          }
        }

        case SubType.NON_PARTICIPATION_DETECTION =>
          s"as this ${node_info.target} service is not getting any traffic within the service cluster"
        case _ => throw new Exception("Unknown SubType used here")
      }
    }

    s"${base_message} ${rel_message}"
  }

  /** Following are the types of Anomaly constants definned
    */
  object ClassType {
    val SYSTEM_METRICS_ANOMALY = "System Metric"
    val NETWORK_ACTIVITY_ANOMALY = "Network Activity"
  }

  object SubType {
    val PEER_DETECTION = "Peer Detection"
    val THRESHOLD_DETECTION = "Threshold Detection"
    val ERROR_DETECTION = "Error Detection"
    val RESOURCE_EXHAUSTION_DETECTION = "Resource Exhaustion"
    val HEARTBEAT_DETECTION = "Heartbeat Detection"
    val SERVICE_DETECTION = "Service Detection"
    val WHALE_USAGE_DETECTION = "Whale Usage"
    val CHATTY_USAGE_DETECTION = "Chatty Usage"
    val POOR_PERFORMANCE_DETECTION = "Poor Performance"
    val HIGH_PERFORMANCE_DETECTION = "High Performance"
    val HOTSPOTTING_DETECTION = "Hotspotting"
    val UNDER_UTILIZED_DETECETION = "Under Utilized"
    val NETWORK_ERRORS_OVER_PEERS_DETECTION = "Network Errors Over Peers"
    val NETWORK_ERRORS_DETECTION = "Network Errors"
    val NON_PARTICIPATION_DETECTION = "Non Participation"
  }

  object NWPlgType {
    val PLUGIN = "nwgraph"
    val AVG_RSP_SIZE = "avg_rsp_size"
    val AVG_TTFB = "avg_ttfb"
    val AVG_TTLB = "avg_ttlb"
    val MAX_TTFB = "max_ttfb"
    val MAX_TTLB = "max_ttlb"
    val ERROR_COUNT = "error_count"
    val REQUEST_COUNT = "request_count"
    val RECV_BYTES = "recv_bytes"
    val SENT_BYTES = "sent_bytes"
    val DURATION = "duration"
  }
}
