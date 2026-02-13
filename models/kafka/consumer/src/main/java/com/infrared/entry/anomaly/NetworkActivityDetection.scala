package com.infrared.entry.anomaly

import breeze.stats._
import com.infrared.entry.Metrics
import com.infrared.entry.Metrics.{DStats, MetricsStats}
import com.infrared.entry.NWObject._
import com.infrared.entry.NWSvcMetrics._
import com.infrared.entry.Stats._
import com.infrared.entry.anomaly.Anomaly._
import com.infrared.entry.infra.Node.NodeMap
import com.infrared.entry.infra.{Cluster, Node}
import com.infrared.util.CassandraDB._
import com.infrared.util.{JacksonWrapper, _}

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.collection.mutable.{HashMap, ListBuffer}
import scala.util._

/**
 * Created by prashun on 29/8/16.
 */
class NetworkActivityDetection(_cluster_replicated: Boolean, _time_range: TimeRange) extends AnomalyTrait {
  private val class_type = ClassType.NETWORK_ACTIVITY_ANOMALY
  private val cluster_replicated: Boolean = _cluster_replicated

  object Mode {
    val LPF = 0
    val BPF = 1
    val HPF = 2
    val ABSOLUTE = 3
  }

  object SVC_STATS_COL {
    val TABLE_NAME = "service_stats_counter"
    val TO_INFO = "to_info"
    val FROM_INFO = "from_info"
    val AVG_RSP_SIZE_STATS = "avg_rsp_size_stats"
    val AVG_TTFB_STATS = "avg_ttfb_stats"
    val AVG_TTLB_STATS = "avg_ttlb_stats"
    val DURATION_STATS = "duration_stats"
    val ERROR_COUNT_STATS = "error_count_stats"
    val MAX_TTFB_STATS = "max_ttfb_stats"
    val MAX_TTLB_STATS = "max_ttlb_stats"
    val RECV_BYTES_STATS = "recv_bytes_stats"
    val REQUEST_COUNT_STATS = "request_count_stats"
    val SENT_BYTES_STATS = "sent_bytes_stats"
    val SVC_INFO = "svc_info"
    val TS = "ts"
  }

  private val time_range: TimeRange = _time_range

  var nw_svc_counters = HashMap[String, List[NWSvcStatsCounter]]()
  var nw_svc_past_counters = HashMap[String, NWSvcStatsCounter]()

  private def getScore(set: Array[Item], pos: Int, peer: Array[(Long, DStats)], mode: Int): Double = {
    val z = set.head
    var score: Double = 0
    val j: Int = math.min(4, pos)
    var k: Int = 1
    var max_score = {
      if (j == 0) 1 else 0
    }
    //We have to get 5 slots here
    for (i <- 1 until j + 2) {
      max_score += i
    }

    for (i <- pos - j until pos + 1) {
      score += k * {
        val ts = set(i).ts
        val value = set(i).value
        val stat = peer.filter(_._1 == ts).map(_._2).head
        val o = {
          mode match {
            case Mode.ABSOLUTE => if (value > 0) 1 else 0
            case Mode.LPF => if (value > (stat.mean + 2 * stat.stddev)) 1 else 0
            case Mode.HPF => if (value < (stat.mean - 2 * stat.stddev)) 1 else 0
            case Mode.BPF => {
              if (value < (stat.mean - 2 * stat.stddev) || value > (stat.mean + 2 * stat.stddev)) 1 else 0
            }
          }
        }
        o
      }
      k += 1
    }
    val fscore = score / max_score
    fscore
  }

  private def getCassandraHandle: CassDB = {
    val addressList = new ListBuffer[CassandraDB.Address]()
    addressList += new Address("127.0.0.1", 9042)
    CassandraDB.getScalaInstance("nuvidata", addressList.toList)
  }


  def setServiceStatsCounter(nwSvcStatsCounters: List[NWSvcStatsCounter]): Unit = {
    val cassdb = getCassandraHandle
    nwSvcStatsCounters.filter(_.is_modified).foreach(x => {
      val query = s"INSERT INTO ${SVC_STATS_COL.TABLE_NAME}(${SVC_STATS_COL.TS}, ${SVC_STATS_COL.TO_INFO}, ${SVC_STATS_COL.SVC_INFO}, " +
        s"${SVC_STATS_COL.AVG_RSP_SIZE_STATS}, ${SVC_STATS_COL.AVG_TTFB_STATS}, ${SVC_STATS_COL.AVG_TTLB_STATS}, ${SVC_STATS_COL.MAX_TTFB_STATS}," +
        s"${SVC_STATS_COL.MAX_TTLB_STATS}, ${SVC_STATS_COL.RECV_BYTES_STATS}, ${SVC_STATS_COL.SENT_BYTES_STATS}, ${SVC_STATS_COL.REQUEST_COUNT_STATS}," +
        s"${SVC_STATS_COL.ERROR_COUNT_STATS}, ${SVC_STATS_COL.DURATION_STATS}) VALUES (" +
        s"${x.ts}, '${JacksonWrapper.serialize(x.to)}', " +
        s"'${JacksonWrapper.serialize(x.svc_info)}'," +
        s"'${JacksonWrapper.serialize(x.avg_rsp_size_stats)}'," +
        s"'${JacksonWrapper.serialize(x.avg_ttfb_stats)}', " +
        s"'${JacksonWrapper.serialize(x.avg_ttlb_stats)}', " +
        s"'${JacksonWrapper.serialize(x.max_ttfb_stats)}', " +
        s"'${JacksonWrapper.serialize(x.max_ttlb_stats)}', " +
        s"'${JacksonWrapper.serialize(x.recv_bytes_stats)}', " +
        s"'${JacksonWrapper.serialize(x.sent_bytes_stats)}', " +
        s"'${JacksonWrapper.serialize(x.request_count_stats)}', " +
        s"'${JacksonWrapper.serialize(x.error_count_stats)}', " +
        s"'${JacksonWrapper.serialize(x.duration_stats)}')"
      val resultSet = cassdb.executeQuery(query)
      cassdb.close()
    })
  }

  def persistStatsCounters(): Unit = {
    setServiceStatsCounter(nw_svc_past_counters.toList.map(_._2))
  }

  def updateStats(x: Array[Double], y: DStats): DStats = {
    val doublePoi: Vector[Double] = for (a <- x.toVector) yield a
    val stats: MeanAndVariance = breeze.stats.meanAndVariance(doublePoi)
    val count = y.size + stats.count
    val mean = (y.mean + stats.mean) / count
    val min_val = math.min(x.sortWith(_ < _).head, y.min_val)
    val max_val = math.max(x.sortWith(_ > _).head, y.max_val)
    val stddev = (y.stddev + stats.stdDev) / count
    val newStats = new MeanAndVariance(mean, stddev * stddev, count)
    var fdVal = new Array[Int](6)
    val _ts = findFPMap(x, newStats)
    _ts.foreach(x => {
      val i = _ts.indexOf(x)
      fdVal(i) += x.length + y.f(i)
    })
    new DStats(count, min_val, max_val, newStats.mean, newStats.stdDev, fdVal)
  }

  def updateCounter(x: Array[NWGraph]): Unit = {
    val z = x.head
    val emptyDStats = new DStats(0l, 0, 0, 0, 0, new Array[Int](6))
    val from = new Node.NodeMap("*", "*", "*", "255.255.255.255", "*", "unknown", true, z.ts)
    val key = s"${z.to.customer_id}, ${z.to.deployment_id}, ${z.to.cluster_id}, ${z.to.host_ip}, ${z.svc_info.port}"
    val counter = {
      //Check if its in the HashTable
      var c = nw_svc_past_counters.getOrElse(key, null)
      if (c == null) {
        c = getServiceStatsCounters(z.to)
      }
      if (c == null) {
        c = new NWSvcStatsCounter(z.ts, z.to, z.svc_info,
          emptyDStats, emptyDStats, emptyDStats, emptyDStats, emptyDStats,
          emptyDStats, emptyDStats, emptyDStats, emptyDStats, emptyDStats, true)
      }
      c
    }
    val avg_rsp_size_stats = updateStats(x.map(_.avg_rsp_size.toDouble), counter.avg_rsp_size_stats)
    val avg_ttfb_stats = updateStats(x.map(_.avg_ttfb.toDouble), counter.avg_ttfb_stats)
    val avg_ttlb_stats = updateStats(x.map(_.avg_ttlb.toDouble), counter.avg_ttlb_stats)
    val max_ttfb_stats = updateStats(x.map(_.max_ttfb.toDouble), counter.max_ttfb_stats)
    val max_ttlb_stats = updateStats(x.map(_.max_ttlb.toDouble), counter.max_ttlb_stats)
    val recv_bytes_stats = updateStats(x.map(_.recv_bytes.toDouble), counter.recv_bytes_stats)
    val sent_bytes_stats = updateStats(x.map(_.sent_bytes.toDouble), counter.sent_bytes_stats)
    val request_count_stats = updateStats(x.map(_.request_count.toDouble), counter.request_count_stats)
    val error_count_stats = updateStats(x.map(_.error_count.toDouble), counter.error_count_stats)
    val duration_stats = updateStats(x.map(_.duration.toDouble), counter.duration_stats)

    if (nw_svc_past_counters.contains(key)) {
      nw_svc_past_counters.remove(key)
    }

    nw_svc_past_counters += (key -> new NWSvcStatsCounter(z.ts, z.to, z.svc_info,
      avg_rsp_size_stats, avg_ttfb_stats, avg_ttlb_stats, max_ttfb_stats, max_ttlb_stats,
      recv_bytes_stats, sent_bytes_stats, request_count_stats, error_count_stats, duration_stats, true))
  }

  def getServiceStatsCounters(to: Node.NodeMap): NWSvcStatsCounter = {
    val cassdb = getCassandraHandle
    var counter: NWSvcStatsCounter = null
    val query = s"SELECT ${SVC_STATS_COL.TS}, ${SVC_STATS_COL.TO_INFO}, ${SVC_STATS_COL.SVC_INFO}, " +
      s"${SVC_STATS_COL.AVG_RSP_SIZE_STATS}, ${SVC_STATS_COL.AVG_TTFB_STATS}, ${SVC_STATS_COL.AVG_TTLB_STATS}, ${SVC_STATS_COL.MAX_TTFB_STATS}," +
      s"${SVC_STATS_COL.MAX_TTLB_STATS}, ${SVC_STATS_COL.RECV_BYTES_STATS}, ${SVC_STATS_COL.SENT_BYTES_STATS},${SVC_STATS_COL.REQUEST_COUNT_STATS}," +
      s"${SVC_STATS_COL.ERROR_COUNT_STATS}, ${SVC_STATS_COL.DURATION_STATS} FROM ${SVC_STATS_COL.TABLE_NAME} WHERE " +
      s"${SVC_STATS_COL.TO_INFO} = '${JacksonWrapper.serialize(to)}' "

    val resultSet = cassdb.executeQuery(query)
    if (resultSet != null) {
      val ctr = resultSet.asScala
        .map(row => {
          val ts = row.getTimestamp(SVC_STATS_COL.TS).getTime
          val to = JacksonWrapper.deserialize[Node.NodeMap](row.getString(SVC_STATS_COL.TO_INFO))
          val svc_info = JacksonWrapper.deserialize[NWSvcInfo](row.getString(SVC_STATS_COL.SVC_INFO))
          val avg_rsp_size_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.AVG_RSP_SIZE_STATS))
          val avg_ttfb_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.AVG_TTFB_STATS))
          val avg_ttlb_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.AVG_TTLB_STATS))
          val max_ttfb_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.MAX_TTFB_STATS))
          val max_ttlb_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.MAX_TTLB_STATS))
          val recv_bytes_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.RECV_BYTES_STATS))
          val sent_bytes_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.SENT_BYTES_STATS))
          val request_count_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.REQUEST_COUNT_STATS))
          val error_count_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.ERROR_COUNT_STATS))
          val duration_stats = JacksonWrapper.deserialize[Metrics.DStats](row.getString(SVC_STATS_COL.DURATION_STATS))
          var costly_requests = List[RequestStat]()
          var err_requests = List[RequestStat]()
          new NWSvcStatsCounter(ts, to, svc_info, avg_rsp_size_stats, avg_ttfb_stats, avg_ttlb_stats,
            max_ttfb_stats, max_ttlb_stats, recv_bytes_stats, sent_bytes_stats, request_count_stats,
            error_count_stats, duration_stats, false)
        })
      if (ctr != null && ctr.nonEmpty) {
        counter = ctr.head
      }
    }
    cassdb.close()
    counter
  }


  def populateNWSvcStatsCounter(x: Array[NWGraph]): NWSvcStatsCounter = {
    val avg_rsp_size = findDStats(x.map(_.avg_rsp_size).map(_.toDouble))
    val avg_ttfb = findDStats(x.map(_.avg_ttfb).map(_.toDouble))
    val avg_ttlb = findDStats(x.map(_.avg_ttlb).map(_.toDouble))
    val max_ttfb = findDStats(x.map(_.max_ttfb).map(_.toDouble))
    val max_ttlb = findDStats(x.map(_.max_ttlb).map(_.toDouble))
    val recv_bytes = findDStats(x.map(_.recv_bytes).map(_.toDouble))
    val sent_bytes = findDStats(x.map(_.sent_bytes).map(_.toDouble))
    val req_count = findDStats(x.map(_.request_count).map(_.toDouble))
    val err_count = findDStats(x.map(_.error_count) map (_.toDouble))
    val duration = findDStats(x.map(_.duration) map (_.toDouble))
    val from = new NodeMap("*", "*", "*", "255.255.255.255", "*", "unknown", true, x.head.ts)
    new NWSvcStatsCounter(x.head.ts, x.head.to, x.head.svc_info,
      avg_rsp_size, avg_ttfb, avg_ttlb, max_ttfb, max_ttlb,
      recv_bytes, sent_bytes, req_count, err_count, duration, true)
  }

  private[this] case class Slot(ts: Long, present: Boolean)

  private def getSlotScore(set: Array[Slot], pos: Int): Double = {
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

  def reportMissingHearbeat(nsl: Array[NWGraph], nw_svc: List[NWSvcDetail]): List[AnomalyCollection] = {
    SINGLE_MAJORITY = 0.5d
    ABS_MAJORITY = 0.75d
    var detections = List[AnomalyCollection]()
    val sub_type = SubType.NON_PARTICIPATION_DETECTION
    val svc_map = HashMap[(String, String), NodeMap]()
    //First see if any service is completely missing in a node for the time specified

    nw_svc.foreach(x => {
      val customer_id = x.customer_id
      val deployment_id = x.deployment_id
      x.nw_svc_info_list.foreach(x => {
        val cluster_id = x.cluster_id
        val nm = Node.getMaps(Some(customer_id), Some(deployment_id), Some(cluster_id), None, Some(true), Some(time_range))
        nm.foreach(y => {
          svc_map += ((y.host_ip, x.name) -> y)
        })

        nsl.filter(y => {
          (y.to.customer_id == customer_id && y.to.deployment_id == deployment_id && y.to.cluster_id == cluster_id &&
            y.svc_info == x)
        }).foreach(x => {
          val key = (x.to.host_ip, x.svc_info.name)
          if (svc_map.contains(key)) {
            svc_map.remove(key)
          }
        })
      })
    })


    //Now group it by each instance of a service
    nsl.groupBy(x => {
      (x.to, x.svc_info)
    }).values
      .foreach(x => {
        val key = (x.head.to.host_ip, x.head.svc_info.name)
        svc_map.remove(key)
        var windowScore = HashMap[Long,WindowScore]()
        var heatMap = HashMap[Long,Int]()
        var tshm = HashMap[Long,Boolean]()
        val v = x.sortWith(_.ts < _.ts)
        v.foreach(x => {
          tshm += (x.ts -> true)
        })
        for (i <- time_range.start until time_range.end by GRAIN * 60 * 1000) {
          if (!tshm.contains(i)) {
            tshm += (i -> false)
          }
        }

        val u = tshm.toList.sortWith(_._1 < _._1).map(x => {
          new Slot(x._1, x._2)
        }).toArray

        //Sliding window of 5 backwards on the array
        for (i <- u.indices) {
          val score = {
            if (simulate) getSimulatedScore else getSlotScore(u, i)
          }
          val severity = getSeverity(score)

          if (u(i).ts >= time_range.start && u(i).ts < time_range.end) {
            heatMap += (u(i).ts -> severity)
            windowScore += (u(i).ts -> new WindowScore(score, u(i).ts, GRAIN, severity))
          }
        }

        val ws = getWSFromWindowScore(windowScore.toMap)

        val qs = {
          val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
          if (qs.severity == sevGrades.head.grade) {
            new QuantizedSeverity(sevGrades(1).grade, qs.total_severity_duration, qs.single_max_severity_duration, qs.total_duration)
          } else {
            qs
          }
        }

        if (qs.severity != sevGrades.last.grade) {
          val z = v.head
          val ms = new MetricsStats(z.ts, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
            NWPlgType.PLUGIN, NWPlgType.REQUEST_COUNT, 0, 0, false, "{}", "{}", "{}", "")
          val message = getMessage(ms, sub_type, qs, cluster_replicated)
          var tags = HashMap[String, String]()
          tags += ("message" -> message)
          if (simulate) tags += ("simulate" -> "true")
          val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap.toMap)
          detections ::= new AnomalyCollection(ms, sc, tags.toMap)
        }
      })

    //Report the rest of the values in svc_map as cold as well
    svc_map.toList.foreach(x => {
      val (host_ip, svc_name) = x._1
      val nm = x._2
      var windowScore = HashMap[Long,WindowScore]()
      var heatMap = HashMap[Long,Int]()
      val score = 1
      val severity = getSeverity(score)
      for (i <- time_range.start until time_range.end by GRAIN * 60 * 1000) {
        heatMap += (i -> severity)
        windowScore += (i -> new WindowScore(score, i, GRAIN, severity))
      }

      val ws = getWSFromWindowScore(windowScore.toMap)

      val qs = {
        val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
        if (qs.severity == sevGrades.head.grade) {
          new QuantizedSeverity(sevGrades(1).grade, qs.total_severity_duration, qs.single_max_severity_duration, qs.total_duration)
        } else {
          qs
        }
      }
      val z = nm
      val ms = new MetricsStats(time_range.start, z.customer_id, z.deployment_id, z.cluster_id, svc_name, z.host_ip, z.host_name,
        NWPlgType.PLUGIN, NWPlgType.REQUEST_COUNT, 0, 0, false, "{}", "{}", "{}", "")

      val message = getMessage(ms, sub_type, qs, cluster_replicated)
      var tags = HashMap[String, String]()
      tags += ("message" -> message)
      if (simulate) tags += ("simulate" -> "true")
      val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap.toMap)
      detections ::= new AnomalyCollection(ms, sc, tags.toMap)
    })

    detections
  }

  def workOnDistribution(arows: Array[Metrics.MetricsStats]): List[AnomalyCollection] = {
    val customer_id = arows.head.customer_id
    val deployment_id = arows.head.deployment_id
    val nsl = prepareNWGraphList(customer_id, deployment_id, arows, time_range).toArray
    workOnDistribution(nsl)
  }

  def workOnDistribution(nsl: Array[NWGraph]): List[AnomalyCollection] = {
    val t1 = System.currentTimeMillis()
    Log.getLogger.trace(s"Start of ${class_type}")
    var detections = List[AnomalyCollection]()
    //nw_svc_counters = getServiceStatsCounters(arows.head.customer_id, arows.head.deployment_id)

    //Find the list of IP in cluster that do not stats associated with them
    detections :::= reportMissingHearbeat(nsl, nw_svc)

    SINGLE_MAJORITY = 0.25d
    ABS_MAJORITY = 0.5d
    //Group by dst cluster
    nsl.groupBy(x => {
      (x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.port, x.ts)
    }).values.foreach(x => {
      val key = s"${x.head.to.customer_id},${x.head.to.deployment_id},${x.head.to.cluster_id},255.255.255.255,${x.head.svc_info.port}"
      var z = nw_svc_counters.getOrElse(key, List[NWSvcStatsCounter]())
      if (nw_svc_counters.contains(key)) {
        nw_svc_counters.remove(key)
      }
      z ::= populateNWSvcStatsCounter(x)
      nw_svc_counters += (key -> z)
    })

    //Group by service instance
    nsl.groupBy(x => {
      (x.to, x.svc_info)
    }).values
      .foreach(v => {
        val cluster_replicate = {
          val z = v.head.to
          Cluster.getMaps(Some(z.customer_id), Some(z.deployment_id), Some(z.cluster_id), Some(true), Some(time_range)).head.replicated
        }
        val x = v.sortWith(_.ts < _.ts)
        val key = s"${x.head.to.customer_id},${x.head.to.deployment_id},${x.head.to.cluster_id},255.255.255.255,${x.head.svc_info.port}"
        val peer = nw_svc_counters.getOrElse(key, List()).sortWith(_.ts < _.ts).toArray
        val z = x.head
        detections :::= {
          val modes = Array(Mode.LPF, Mode.HPF)
          val d = modes.map(mode => {
            //avg_rsp_size LPF
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.avg_rsp_size.toDouble)
              }),
                peer.map(x => (x.ts, x.avg_rsp_size_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.WHALE_USAGE_DETECTION
                  case Mode.HPF => SubType.CHATTY_USAGE_DETECTION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.AVG_RSP_SIZE, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.avg_rsp_size.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")

              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }

        detections :::= {
          val modes = Array(Mode.LPF, Mode.HPF)
          val d = modes.map(mode => {
            //avg_ttfb
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.avg_ttfb.toDouble)
              }),
                peer.map(x => (x.ts, x.avg_ttfb_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.POOR_PERFORMANCE_DETECTION
                  case Mode.HPF => SubType.HIGH_PERFORMANCE_DETECTION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.AVG_TTFB, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.avg_ttfb.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")
              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }

        detections :::= {
          val modes = Array(Mode.LPF, Mode.HPF)
          val d = modes.map(mode => {
            //avg_ttlb
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.avg_ttlb.toDouble)
              }),
                peer.map(x => (x.ts, x.avg_ttlb_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.POOR_PERFORMANCE_DETECTION
                  case Mode.HPF => SubType.HIGH_PERFORMANCE_DETECTION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.AVG_TTLB, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.avg_ttlb.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")
              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }

        detections :::= {
          val modes = Array(Mode.LPF, Mode.HPF)
          val d = modes.map(mode => {
            //max_ttfb
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.max_ttfb.toDouble)
              }),
                peer.map(x => (x.ts, x.max_ttfb_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.POOR_PERFORMANCE_DETECTION
                  case Mode.HPF => SubType.HIGH_PERFORMANCE_DETECTION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.MAX_TTFB, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.max_ttfb.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")
              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }

        detections :::= {
          val modes = Array(Mode.LPF, Mode.HPF)
          val d = modes.map(mode => {
            //max_ttlb
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.max_ttlb.toDouble)
              }),
                peer.map(x => (x.ts, x.max_ttlb_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.POOR_PERFORMANCE_DETECTION
                  case Mode.HPF => SubType.HIGH_PERFORMANCE_DETECTION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.MAX_TTLB, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.max_ttlb.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")
              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }

        detections :::= {
          val modes = Array(Mode.LPF, Mode.HPF)
          val d = modes.map(mode => {
            //recv_bytes
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.recv_bytes.toDouble)
              }),
                peer.map(x => (x.ts, x.recv_bytes_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.HOTSPOTTING_DETECTION
                  case Mode.HPF => SubType.UNDER_UTILIZED_DETECETION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.RECV_BYTES, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.recv_bytes.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")
              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }

        detections :::= {
          val modes = Array(Mode.LPF, Mode.HPF)
          val d = modes.map(mode => {
            //sent_bytes
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.sent_bytes.toDouble)
              }),
                peer.map(x => (x.ts, x.sent_bytes_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.HOTSPOTTING_DETECTION
                  case Mode.HPF => SubType.UNDER_UTILIZED_DETECETION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.SENT_BYTES, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.sent_bytes.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")
              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }

        detections :::= {
          val modes = Array(Mode.LPF, Mode.HPF)
          val d = modes.map(mode => {
            //req_count
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.request_count.toDouble)
              }),
                peer.map(x => (x.ts, x.request_count_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.HOTSPOTTING_DETECTION
                  case Mode.HPF => SubType.UNDER_UTILIZED_DETECETION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.REQUEST_COUNT, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.request_count.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")
              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }

        detections :::= {
          val modes = Array(Mode.LPF, Mode.ABSOLUTE)
          val d = modes.map(mode => {
            //err_count
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.error_count.toDouble)
              }),
                peer.map(x => (x.ts, x.error_count_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.NETWORK_ERRORS_OVER_PEERS_DETECTION
                  case Mode.ABSOLUTE => SubType.NETWORK_ERRORS_DETECTION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.ERROR_COUNT, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.error_count.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")
              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }

        detections :::= {
          val modes = Array(Mode.LPF, Mode.HPF)
          val d = modes.map(mode => {
            //duration
            var detections = List[AnomalyCollection]()
            val (windowScore, heatMap) = {
              getWindowScoreAndHeatMap(x.map(a => {
                new Item(a.ts, a.duration.toDouble)
              }),
                peer.map(x => (x.ts, x.duration_stats)), mode)
            }
            val ws = getWSFromWindowScore(windowScore)
            val qs = getQuantizedSeverity(List(ws), cluster_replicated, time_range)
            if (qs.severity != sevGrades.last.grade) {
              val sub_type = {
                mode match {
                  case Mode.LPF => SubType.HOTSPOTTING_DETECTION
                  case Mode.HPF => SubType.UNDER_UTILIZED_DETECETION
                }
              }
              var tags = HashMap[String, String]()
              val ms = new MetricsStats(time_range.start, z.to.customer_id, z.to.deployment_id, z.to.cluster_id, z.svc_info.name, z.to.host_ip, z.to.host_name,
                NWPlgType.PLUGIN, NWPlgType.DURATION, 0, 0, false, "{}", "{}", JacksonWrapper.serialize(tags), z.duration.toString)
              val message = getMessage(ms, sub_type, qs, cluster_replicated)
              tags += ("message" -> message)
              if (simulate) tags += ("simulate" -> "true")
              val sc = new SeverityClassification(class_type, sub_type, qs, ws, heatMap)
              detections ::= new AnomalyCollection(ms, sc, tags.toMap)
            }
            detections
          })
          d.filter(_.nonEmpty).map(_.head).toList
        }
        updateCounter(x)
      })

    persistStatsCounters()
    val diff = System.currentTimeMillis() - t1
    Log.getLogger.trace(s"End of ${class_type} - took ${diff} ms")
    detections
  }

  private[this] case class Item(ts: Long, value: Double)

  def getWindowScoreAndHeatMap(x: Array[Item], y: Array[(Long, DStats)], mode: Int):
  (Map[Long, WindowScore], Map[Long, Int]) = {
    var windowScore = HashMap[Long,WindowScore]()
    var heatMap = HashMap[Long,Int]()
    for (i <- x.indices) {
      val score = {
        if (simulate && Random.nextBoolean()) Random.nextDouble() else getScore(x, i, y, mode)
      }
      val severity = getSeverity(score)

      if (x(i).ts >= time_range.start && x(i).ts < time_range.end) {
        heatMap += (x(i).ts -> severity)
        windowScore += (x(i).ts -> new WindowScore(score, x(i).ts, GRAIN, severity))
      }
    }
    (windowScore.toMap, heatMap.toMap)
  }

  protected[anomaly] def writeNWSvcToKairosDB(arows: Array[Metrics.MetricsStats]): Unit = {
    val customer_id = arows.head.customer_id
    val deployment_id = arows.head.deployment_id
    val nsl = prepareNWGraphList(customer_id, deployment_id, arows, time_range).toArray
    writeNWSvcToKairosDB(nsl)
  }

  protected[anomaly] def writeNWSvcToKairosDB(nsl: Array[NWGraph]): Unit = {
    //Group by dst cluster
    nsl.groupBy(x => {
      (x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.port, x.ts)
    }).values.foreach(x => {
      val z = populateNWSvcStatsCounter(x)
      val to = new Node.NodeMap(z.to.customer_id, z.to.deployment_id, z.to.cluster_id, "255.255.255.255", "*", z.to.scope, z.to.active, z.to.last_modified)
      val from = new Node.NodeMap("*", "*", "*", "255.255.255.255", "*", "unknown", z.to.active, z.to.last_modified)
      val y = new NWGraph(to, from, z.svc_info, z.avg_rsp_size_stats.mean.toLong, z.avg_ttfb_stats.mean.toLong, z.avg_ttlb_stats.mean.toLong,
        z.max_ttfb_stats.mean.toLong, z.max_ttlb_stats.mean.toLong, z.error_count_stats.mean.toLong, z.request_count_stats.mean.toLong,
        z.sent_bytes_stats.mean.toLong, z.recv_bytes_stats.mean.toLong, Array(), Array(), z.ts, z.duration_stats.mean.toLong)
      //This is for the instances as well as the cluster service as a whole
      var a = x.toList
      a ::= y
      prepareForWrite(a)
    })
  }

  private def prepareForWrite(nsl: List[NWGraph]): Unit = {
    var final_str = ""

    var ms = Array[Metrics.MetricsStats]()
    nsl.foreach(x => {
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.AVG_RSP_SIZE, 0, 0, false, "{}", "{}", "{}", x.avg_rsp_size.toString)
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.AVG_TTFB, 0, 0, false, "{}", "{}", "{}", x.avg_ttfb.toString)
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.AVG_TTLB, 0, 0, false, "{}", "{}", "{}", x.avg_ttlb.toString)
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.MAX_TTFB, 0, 0, false, "{}", "{}", "{}", x.max_ttfb.toString)
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.MAX_TTLB, 0, 0, false, "{}", "{}", "{}", x.max_ttlb.toString)
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.ERROR_COUNT, 0, 0, false, "{}", "{}", "{}", x.error_count.toString)
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.REQUEST_COUNT, 0, 0, false, "{}", "{}", "{}", x.request_count.toString)
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.SENT_BYTES, 0, 0, false, "{}", "{}", "{}", x.sent_bytes.toString)
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.RECV_BYTES, 0, 0, false, "{}", "{}", "{}", x.recv_bytes.toString)
      ms +:= new MetricsStats(x.ts, x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.svc_info.name,
        x.to.host_ip, x.to.host_name, NWPlgType.PLUGIN, NWPlgType.DURATION, 0, 0, false, "{}", "{}", "{}", x.duration.toString)
    })
    Metrics.writeToKairosDB(ms)
  }

  def getObservationsFromAnomalyList(conn_type: Int, detections: List[AnomalyCollection]): List[MetricsStats] = {
    var host_ip = "255.255.255.255"
    var host_name = "*"
    val d = {
      conn_type match {
        case ConnType.NODE => {
          //TODO : What if there was no filter
          detections.filter(_.node.plugin == NWPlgType.PLUGIN)
            .groupBy(x => {
              val z = x.node
              host_ip = z.host_ip
              host_name = z.host_name
              (z.customer_id, z.deployment_id, z.cluster_id, z.host_ip, z.target)
            })
        }
        case ConnType.CLUSTER => {
          detections.filter(_.node.plugin == NWPlgType.PLUGIN)
            .groupBy(x => {
              val z = x.node
              host_ip = "255.255.255.255"
              host_name = "*"
              (z.customer_id, z.deployment_id, z.cluster_id, z.target)
            })
        }
        case ConnType.DEPLOYMENT => {
          detections.filter(_.node.plugin == NWPlgType.PLUGIN)
            .groupBy(x => {
              val z = x.node
              host_ip = "255.255.255.255"
              host_name = "*"
              (z.customer_id, z.deployment_id, z.cluster_id, z.target)
            })
        }
      }
    }
    val observable_ms = d.map(x => {
      val z = x._2.head.node
      var observation_map = HashMap[String, String]()
      x._2.foreach(x => {
        val key = x.anomaly.subType
        val count = observation_map.getOrElse(key, "0").toInt + 1
        if (observation_map.contains(key)) {
          observation_map.remove(key)
        }
        observation_map += (key -> count.toString)
      })
      val ms = new MetricsStats(z.ts, z.customer_id, z.deployment_id, z.cluster_id, z.target, host_ip, host_name, z.plugin, "*", 0,
        0, true, "{}", "{}", JacksonWrapper.serialize(observation_map), "")
      ms
    })
    observable_ms.toList
  }

  def processNWConnections(connType: Int, plugin_level_list: Array[MetricsStats],
                           cluster_replicated: Boolean, time_range: TimeRange): List[NWGraph] = {
    val customer_id = plugin_level_list.head.customer_id
    val deployment_id = plugin_level_list.head.deployment_id
    val nsl = prepareNWGraphList(customer_id, deployment_id, plugin_level_list, time_range).toArray
    processNWConnections(connType, nsl, Array[AnomalyCollection](), time_range)
  }

  def processServices(customer_id: String, deployment_id: String, cluster_id: String, host_ip: String,
                      anomalies: Array[AnomalyCollection], time_range: TimeRange): List[ServiceInfo] = {
    /* Find out the services associated with the connType */
    val connType = {
      if (cluster_id == "*" && host_ip == "255.255.255.255") ConnType.DEPLOYMENT
      else if (cluster_id != "*" && host_ip == "255.255.255.255") ConnType.CLUSTER
      else if (cluster_id != "*" && host_ip != "255.255.255.255") ConnType.NODE
      else ConnType.NODE
    }

    val svc_list: List[NWSvcInfo] = getNWServices(customer_id, deployment_id, time_range)
      .map(_.nw_svc_info_list).head.filter(x => {
      connType match {
        case ConnType.DEPLOYMENT => true
        case ConnType.CLUSTER => x.cluster_id == cluster_id
        case ConnType.NODE => x.cluster_id == cluster_id
      }
    })

    val observations = getObservationsFromAnomalyList(connType, anomalies.toList)
    val services = svc_list.map(x => {
      val obs = {
        val o = observations.filter(y => {
          (y.customer_id == customer_id && y.deployment_id == deployment_id &&
            y.cluster_id == x.cluster_id && y.host_ip == host_ip && y.target == x.name)
        })

        if (o.nonEmpty) {
          JacksonWrapper.deserialize[Map[String,String]](o.head.tags)
        } else {
          HashMap[String, String]()
        }
      }
      new ServiceInfo(x, obs.toMap)
    })

    services
  }

  def processNWConnections(connType: Int, hl: Array[NWGraph],
                           anomalies: Array[AnomalyCollection], time_range: TimeRange): List[NWGraph] = {
    val t1 = System.currentTimeMillis()
    var hlsummary = HashMap[NWBinding,NWGraph]()
    Log.getLogger.trace(s"Start of processNWConnections")

    /* Group the NWGraph based on the connType */
    if (hl.length > 0) {
      val flist = {
        val hll = {
          connType match {
            case ConnType.CLUSTER => {
              hl.groupBy(x => {
                val to = x.to.copy(x.to.customer_id, x.to.deployment_id, x.to.cluster_id, "255.255.255.255", "*", x.to.scope, x.to.active, 0l)
                val from = x.from.copy(x.from.customer_id, x.from.deployment_id, x.from.cluster_id, "255.255.255.255", "*", x.from.scope, x.from.active, 0l)
                new NWBinding(to, from, x.svc_info)
              })
            }
            case ConnType.DEPLOYMENT => {
              hl.groupBy(x => {
                val to = x.to.copy(x.to.customer_id, x.to.deployment_id, "*", "255.255.255.255", "*", x.to.scope, x.to.active, 0l)
                val from = x.from.copy(x.from.customer_id, x.from.deployment_id, "*", "255.255.255.255", "*", x.from.scope, x.from.active, 0l)
                new NWBinding(to, from, x.svc_info)
              })
            }
            case _ => {
              hl.groupBy(x => {
                val to = x.to.copy(x.to.customer_id, x.to.deployment_id, x.to.cluster_id, x.to.host_ip, x.to.host_name, x.to.scope, x.to.active, 0l)
                //From should always be from a cluster and not individual IP
                val from = x.from.copy(x.from.customer_id, x.from.deployment_id, x.from.cluster_id, "*", "255.255.255.255", x.from.scope, x.from.active, 0l)
                //val from = x.from.copy(x.from.customer_id, x.from.deployment_id, x.from.cluster_id, x.from.host_ip, x.from.host_name, x.from.scope, x.from.active, 0l)
                new NWBinding(to, from, x.svc_info)
              })
            }
          }
        }
        hll
      }


      flist.foreach(x => {
        var costliestReqStats = Array[RequestStat]()
        var topmostErrReqStats = Array[RequestStat]()
        var avgRspSize, avgTTFB, avgTTLB, duration: Long = 0
        var recvBytes, sentBytes, reqCount, errCount: Long = 0
        x._2.foreach(x => {
          avgRspSize += x.avg_rsp_size * x.duration
          reqCount += x.request_count
          avgTTFB += x.avg_ttfb * x.duration
          avgTTLB += x.avg_ttlb * x.duration
          errCount += x.error_count
          duration += x.duration
          recvBytes += x.recv_bytes
          sentBytes += x.sent_bytes
          costliestReqStats = costliestReqStats ++ x.costliest_request_stats
          topmostErrReqStats = topmostErrReqStats ++ x.topmost_error_stats
        })
        avgRspSize /= duration
        avgTTFB /= duration
        avgTTLB /= duration
        val crs = costliestReqStats.sortWith(_.ttfb > _.ttfb).toList
        val tes = topmostErrReqStats.sortWith(_.ttfb > _.ttfb).toList
        costliestReqStats = Array()
        topmostErrReqStats = Array()
        val max_items = 3
        var j = 0
        for (i <- crs.indices) {
          if (j < max_items && crs(i).req.length > 0) {
            val (index, found) = findAppropriateIndex(costliestReqStats, crs(i).req)
            if (found) {
              val reqStat: RequestStat = new RequestStat(costliestReqStats(index).req, costliestReqStats(index).count + crs(i).count,
                (costliestReqStats(index).ttfb * costliestReqStats(index).count + crs(i).ttfb * crs(i).count) / (costliestReqStats(index).count + crs(i).count),
                (costliestReqStats(index).ttlb * costliestReqStats(index).count + crs(i).ttlb * crs(i).count) / (costliestReqStats(index).count + crs(i).count))
              costliestReqStats(index) = reqStat
            } else {
              costliestReqStats :+= crs(i)
              j += 1
            }
          }
        }

        j = 0
        for (i <- 0 until math.min(tes.length, 3)) {
          if (tes(i).count > 0 && j < max_items && tes(i).req.length > 0) {
            val (index, found) = findAppropriateIndex(topmostErrReqStats, tes(i).req)
            if (found) {
              val reqStat = new RequestStat(topmostErrReqStats(index).req, topmostErrReqStats(index).count + tes(i).count,
                (topmostErrReqStats(index).ttfb * topmostErrReqStats(index).count + tes(i).ttfb * tes(i).count) / (topmostErrReqStats(index).count + tes(i).count),
                (topmostErrReqStats(index).ttlb * topmostErrReqStats(index).count + tes(i).ttlb * tes(i).count) / (topmostErrReqStats(index).count + tes(i).count))
              topmostErrReqStats(index) = reqStat
            } else {
              topmostErrReqStats :+= tes(i)
              j += 1
            }
          }
        }
        val y = new NWGraph(x._1.to, x._1.from, x._1.scvInfo, avgRspSize, avgTTFB, avgTTLB,
          crs.head.ttfb, crs.head.ttlb, errCount, reqCount, sentBytes, recvBytes, topmostErrReqStats,
          costliestReqStats, x._2.head.ts, duration)
        hlsummary += (x._1 -> y)
      })
    }
    val diff = System.currentTimeMillis() - t1
    Log.getLogger.trace(s"End of processNWConnections - time taken = ${diff} ms")

    hlsummary.toList.map(_._2)
  }

}
