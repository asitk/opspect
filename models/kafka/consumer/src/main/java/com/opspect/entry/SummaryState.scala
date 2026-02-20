package com.opspect.entry

import com.opspect.entry.Metrics._
import com.opspect.entry.anomaly.Anomaly._
import com.opspect.entry.anomaly.Detections
import com.opspect.entry.infra.Cluster
import com.opspect.util.CassandraDB._
import com.opspect.util._

import scala.collection.immutable._
import scala.collection.mutable.{HashMap, ListBuffer}
import scala.util.hashing.{MurmurHash3 => MH3}

/** Created by prashun on 7/6/16.
  */
object SummaryState {
  private def getCassandraHandle: CassDB = {
    val addressList = new ListBuffer[CassandraDB.Address]()
    addressList += new Address("127.0.0.1", 9042)
    CassandraDB.getScalaInstance("nuvidata", addressList.toList)
  }

  def recreateThermal(
      stats: List[Stats],
      cluster_replicated: Boolean,
      time_range: TimeRange
  ): Thermal = {
    var critInfo = HashMap[Int, Int]()
    sevGrades.foreach(x => {
      critInfo += (x.grade -> stats.filter(_.thermal == x.grade).length)
    })

    val quantizedWS = Detections.Generic.getQuantizedSeverity(
      stats.map(_.ws),
      cluster_replicated,
      time_range
    )
    val resultantWS =
      Detections.Generic.getResultantWindowScore(stats.map(_.ws))
    val resultantHeatMap =
      Detections.Generic.getHeatMapWithScoreFromWindowScore(resultantWS)
    val optimizedHeatMap = Detections.Generic.getOptimizedHeatMapWithScore(
      resultantHeatMap,
      time_range
    )
    new Thermal(critInfo.toMap, quantizedWS, optimizedHeatMap)
  }

  private def writeSystemAnomalies(
      customer_id: String,
      deployment_id: String,
      time_range: TimeRange
  ): (List[AnomalyCollection], Array[MetricsStats]) = {
    var detections = List[AnomalyCollection]()
    var nw_arows = Array[MetricsStats]()
    val clm = Cluster.getMaps(
      Some(customer_id),
      Some(deployment_id),
      None,
      Some(true),
      Some(time_range)
    )
    clm.foreach(x => {
      val query = {
        val tr = new TimeRange(time_range.start - PRESET_MILLI, time_range.end)
        getSystemMetricsQuery(customer_id, deployment_id, x.cluster_id, tr, 1)
      }
      var arows = getMetricsQueryResult(query)
      val cluster_id = x.cluster_id
      val cluster_replicated = x.replicated

      if (arows.length > 0) {
        arows = arows.filterNot(_.host_name == "*")
        detections :::= Detections.getPeerDetectionOnDistribution(
          arows,
          cluster_replicated,
          time_range
        )
        detections :::= Detections.getThresholdDetectionOnDistribution(
          arows,
          cluster_replicated,
          time_range
        )
        detections :::= Detections.getErrorDetectionOnDistribution(
          arows,
          cluster_replicated,
          time_range
        )
        detections :::= Detections.getResourceExhaustionDetectionOnDistribution(
          arows,
          cluster_replicated,
          time_range
        )
      }
      detections :::= Detections.getHeartbeatDetectionOnDistribution(
        arows,
        x,
        time_range
      )
      detections :::= Detections.getServiceDetectionOnDistribution(
        arows,
        x,
        time_range
      )
      val nwrows = arows.filter(x => {
        Array("procstat", "nwgraph").contains(x.plugin)
      })
      nw_arows ++= nwrows
    })
    val ttl = Some(7200)
    Detections.writeToDB(detections, ttl)

    (detections, nw_arows)
  }

  private def writeNWConnectionsAndAnomalies(
      customer_id: String,
      deployment_id: String,
      time_range: TimeRange
  ): (List[AnomalyCollection], List[NWObject.NWGraph]) = {
    val query = {
      val tr = new TimeRange(time_range.start - PRESET_MILLI, time_range.end)
      getNetworkMetricsQuery(customer_id, deployment_id, tr, 1)
    }

    var arows = getMetricsQueryResult(query)

    if (arows.length > 0) {
      arows = arows.filterNot(_.host_name == "*")
    }
    writeNWConnectionsAndAnomalies(
      customer_id,
      deployment_id,
      arows,
      time_range
    )
  }

  private def writeNWConnectionsAndAnomalies(
      customer_id: String,
      deployment_id: String,
      arows: Array[MetricsStats],
      time_range: TimeRange
  ): (List[AnomalyCollection], List[NWObject.NWGraph]) = {
    val ttl = Some(7200)
    val nsl = NWObject
      .prepareNWGraphList(customer_id, deployment_id, arows, time_range)
      .toArray
    Detections.writeNetworkActivityByService(nsl, false, time_range)
    val detections = Detections.getNetworkActivityDetectionOnDistribution(
      nsl,
      true,
      time_range
    )
    Detections.writeToDB(detections, ttl)
    val connections = Detections.processNWConnections(
      NWObject.ConnType.NODE,
      nsl,
      detections.toArray,
      time_range
    )
    NWObject.writeToDB(connections, ttl)

    (detections, connections)
  }

  def writeAnomaliesToDB(
      customer_id: String,
      deployment_id: String,
      time_range: TimeRange,
      duration: Int
  ): (List[AnomalyCollection], List[NWObject.NWGraph]) = {
    var detections = List[AnomalyCollection]()
    var connections = List[NWObject.NWGraph]()
    for (i <- time_range.start until time_range.end by duration * 60 * 1000) {
      val tr = new TimeRange(i, i + duration * 60 * 1000)
      val (det, arows) = writeSystemAnomalies(customer_id, deployment_id, tr)
      detections :::= det
      val (d, c) =
        writeNWConnectionsAndAnomalies(customer_id, deployment_id, arows, tr)
      detections :::= d
      connections :::= c
    }
    (detections, connections)
  }

  def getAnomaliesByGrain(
      customer_id: String,
      deployment_id: String,
      cluster_id: Option[String],
      host_ip: Option[String],
      time_range: TimeRange,
      grainInMinutes: Long
  ): (List[AnomalyCollection], List[NWObject.NWGraph]) = {
    var detections = List[AnomalyCollection]()
    val tsList =
      getTimeRangeAsASetString(time_range, 1).split(",").map(_.toLong).toList
    var d = Detections.getFromDB(
      customer_id,
      deployment_id,
      Some(tsList),
      Some(1),
      cluster_id,
      host_ip,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None
    )
    if (d.isEmpty) {
      // Write 1 minute grain of anomalies to the DB
      val duration = 1
      val (d1, _) =
        writeAnomaliesToDB(customer_id, deployment_id, time_range, duration)
      d = d1
      // Refetch it
      // d= Detections.getFromDB(customer_id, deployment_id, Some(tsList), Some(1),
      //  cluster_id, host_ip, None, None, None, None, None, None, None, None)
    }

    if (d.nonEmpty) {
      if (grainInMinutes > 1) {
        d.groupBy(x => {
          val z = x.node
          val y = x.anomaly
          (
            z.customer_id,
            z.deployment_id,
            z.cluster_id,
            z.host_ip,
            z.target,
            z.plugin,
            z.classification,
            y.cls,
            y.subType
          )
        }).foreach(x => {
          var done = false
          var start = time_range.start
          val cluster_replicated = Cluster
            .getMaps(
              Some(x._1._1),
              Some(x._1._2),
              Some(x._1._3),
              Some(true),
              Some(time_range)
            )
            .head
            .replicated

          do {
            val end = start + grainInMinutes * 60 * 1000
            val tr = new TimeRange(start, end)
            start += grainInMinutes * 60 * 1000
            if (start >= time_range.end) done = true
            val detects =
              Detections.Generic.quantizeAnomalies(x._2, cluster_replicated, tr)
            if (detects != null) {
              detections ::= detects
            }
          } while (!done)
        })
      } else {
        detections :::= d
      }
    }
    val connections = {
      val conn = NWObject.getFromDB(
        customer_id,
        deployment_id,
        Some(tsList),
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None
      )
      Detections.processNWConnections(
        NWObject.ConnType.NODE,
        conn.toArray,
        detections.toArray,
        time_range
      )
    }
    val ttl = Some(30 * 86400)
    Log.getLogger.trace(
      "Going to write the quantized anomaly and connections to DB"
    )
    Detections.writeToDB(detections, ttl)
    NWObject.writeToDB(connections, ttl)
    (detections, connections)
  }

  def processAnomaliesFromDB(
      customer_id: String,
      deployment_id: String,
      cluster_id: Option[String],
      host_ip: Option[String],
      time_range: TimeRange
  ): Info = {

    val duration = (time_range.end - time_range.start) / (60 * 1000)
    val (detections, connections) = getAnomaliesByGrain(
      customer_id,
      deployment_id,
      cluster_id,
      host_ip,
      time_range,
      duration
    )
    processAnomaliesFromDB(
      customer_id,
      deployment_id,
      cluster_id,
      host_ip,
      time_range,
      detections
    )
  }

  def processAnomaliesFromDB(
      customer_id: String,
      deployment_id: String,
      cluster_id: Option[String],
      host_ip: Option[String],
      time_range: TimeRange,
      detections: List[AnomalyCollection]
  ): Info = {
    var critInfo = HashMap[Int, Int]()
    sevGrades.foreach(x => {
      critInfo.getOrElseUpdate(x.grade, 0)
    })

    var heatMapList = List[Map[Long, Int]]()
    var windowScoreList = List[Map[Int, Array[WindowScore]]]()
    var statsList = List[Stats]()

    detections
      .groupBy(x => {
        val y = x.node
        (
          y.customer_id,
          y.deployment_id,
          y.cluster_id,
          y.host_ip,
          y.target,
          y.plugin,
          y.classification,
          x.anomaly.cls,
          x.anomaly.subType
        )
      })
      .values
      .foreach(x => {
        val d = x.head.node

        x.foreach(x1 => {
          if (x1 != null && x1.anomaly != null) {
            val y = x1.anomaly
            heatMapList ::= y.hm
            windowScoreList ::= y.ws

            statsList ::= new Stats(
              d.customer_id,
              d.deployment_id,
              d.cluster_id,
              d.host_ip,
              d.host_name,
              d.plugin,
              d.target,
              d.classification,
              y.qs.severity,
              y.cls,
              y.subType,
              null,
              y.qs,
              y.ws,
              x1.tags
            )
            val critcount: Int = critInfo.getOrElseUpdate(y.qs.severity, 0)
            critInfo.remove(y.qs.severity)
            critInfo += (y.qs.severity -> (critcount + 1))
          }
        })
      })

    val services = {
      val cid = if (cluster_id.nonEmpty) cluster_id.get else "*"
      val hip = if (host_ip.nonEmpty) host_ip.get else "255.255.255.255"
      Detections.processServices(
        customer_id,
        deployment_id,
        cid,
        hip,
        detections.toArray,
        time_range
      )
    }

    val clm = Cluster.getMaps(
      Some(customer_id),
      Some(deployment_id),
      cluster_id,
      Some(true),
      Some(time_range)
    )
    val cluster_replicated = clm.head.replicated
    // val resultantHeatMap = Detections.Generic.getResultantHeatMap(heatMapList, time_range)
    val resultantWS =
      Detections.Generic.getResultantWindowScore(windowScoreList)
    val resultantHeatMap =
      Detections.Generic.getHeatMapWithScoreFromWindowScore(resultantWS)
    val optimizedHeatMap = Detections.Generic.getOptimizedHeatMapWithScore(
      resultantHeatMap,
      time_range
    )
    Log.getLogger.debug(s"The resultantHeatMap = ${resultantHeatMap}")
    val quantizedWS = Detections.Generic.getQuantizedSeverity(
      windowScoreList,
      cluster_replicated,
      time_range
    )
    Log.getLogger.debug(s"The quantizedWS = ${quantizedWS}")
    var thermal = new Thermal(critInfo.toMap, quantizedWS, optimizedHeatMap)
    val rankedStatsList = Predictions.rankDependencies(
      thermal.summary.severity,
      statsList,
      time_range
    )
    thermal = recreateThermal(rankedStatsList, cluster_replicated, time_range)
    new Info(List(), services, rankedStatsList, thermal)
  }

  def getNodeSnapshot(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_ip: String,
      time_range: TimeRange,
      detections: List[AnomalyCollection],
      connections: List[NWObject.NWGraph]
  ): Info = {
    // Since we already have the data just need to filter
    val detects = detections.filter(x => {
      (x.node.customer_id == customer_id && x.node.deployment_id == deployment_id
      && x.node.cluster_id == cluster_id && x.node.host_ip == host_ip)
    })
    val info_tmp = processAnomaliesFromDB(
      customer_id,
      deployment_id,
      Some(cluster_id),
      Some(host_ip),
      time_range,
      detects
    )
    val connects = {
      val conn = connections.filter(x => {
        (x.to.customer_id == customer_id && x.to.deployment_id == deployment_id &&
        x.to.cluster_id == cluster_id && x.to.host_ip == host_ip)
      })
      Detections.processNWConnections(
        NWObject.ConnType.NODE,
        conn.toArray,
        detects.toArray,
        time_range
      )
    }
    val info =
      new Info(connects, info_tmp.services, info_tmp.stats, info_tmp.thermal)
    info
  }

  def getClusterSnapshot(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      time_range: TimeRange,
      detections: List[AnomalyCollection],
      connections: List[NWObject.NWGraph]
  ): Info = {
    // Since we already have the data just need to filter
    val detects = detections.filter(x => {
      (x.node.customer_id == customer_id && x.node.deployment_id == deployment_id
      && x.node.cluster_id == cluster_id)
    })
    val info_tmp = processAnomaliesFromDB(
      customer_id,
      deployment_id,
      Some(cluster_id),
      None,
      time_range,
      detects
    )
    val connects = {
      val conn = connections.filter(x => {
        (x.to.customer_id == customer_id && x.to.deployment_id == deployment_id &&
        x.to.cluster_id == cluster_id)
      })
      Detections.processNWConnections(
        NWObject.ConnType.CLUSTER,
        conn.toArray,
        detects.toArray,
        time_range
      )
    }
    val info =
      new Info(connects, info_tmp.services, info_tmp.stats, info_tmp.thermal)
    info
  }

  def getDeploymentSnapshot(
      customer_id: String,
      deployment_id: String,
      time_range: TimeRange,
      detections: List[AnomalyCollection],
      connections: List[NWObject.NWGraph]
  ): Info = {
    // Since we already have the data just need to filter
    val detects = detections.filter(x => {
      (x.node.customer_id == customer_id && x.node.deployment_id == deployment_id)

    })
    val info_tmp = processAnomaliesFromDB(
      customer_id,
      deployment_id,
      None,
      None,
      time_range,
      detects
    )
    val connects = {
      val conn = connections.filter(x => {
        (x.to.customer_id == customer_id && x.to.deployment_id == deployment_id)
      })
      Detections.processNWConnections(
        NWObject.ConnType.CLUSTER,
        conn.toArray,
        detects.toArray,
        time_range
      )
    }
    val info =
      new Info(connects, info_tmp.services, info_tmp.stats, info_tmp.thermal)
    info
  }

  case class Thermal(
      critInfo: Map[Int, Int],
      summary: QuantizedSeverity,
      detail: List[HeatMapDetailWithScore]
  ) extends Serializable {
    override def toString(): String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class Contribution(rank: Int, share: Double) extends Serializable {
    override def toString(): String = {
      JacksonWrapper.serialize(this)
    }
  }
  case class Stats(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_ip: String,
      host_name: String,
      plugin: String,
      target: String,
      classification: String,
      thermal: Int,
      anomaly_class: String,
      anomaly_type: String,
      contribution: Contribution,
      thermal_reason: QuantizedSeverity,
      ws: Map[Int, Array[WindowScore]],
      tags: Map[String, String]
  ) extends Serializable {
    override def toString(): String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class Info(
      connections: List[NWObject.NWGraph],
      services: List[NWObject.ServiceInfo],
      stats: List[Stats],
      thermal: Thermal
  ) extends Serializable {
    override def toString(): String = {
      JacksonWrapper.serialize(this)
    }
  }

}
