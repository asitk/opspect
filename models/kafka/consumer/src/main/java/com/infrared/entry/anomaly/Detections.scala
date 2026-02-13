package com.infrared.entry.anomaly

import com.infrared.entry.Metrics.MetricsStats
import com.infrared.entry.anomaly.Anomaly._
import com.infrared.entry.infra.Cluster
import com.infrared.entry.{Metrics, NWObject}
import com.infrared.util.CassandraDB._
import com.infrared.util.{JacksonWrapper, _}

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.collection.mutable.{HashMap, ListBuffer}

/**
 * Created by prashun on 10/8/16.
 */
object Detections {
  def getPeerDetectionOnDistribution(arows: Array[Metrics.MetricsStats], cluster_replicated: Boolean, time_range: TimeRange): List[AnomalyCollection] = {
    val pd = new PeerDetection(cluster_replicated, time_range)
    pd.workOnDistribution(arows)
  }

  def getThresholdDetectionOnDistribution(arows: Array[Metrics.MetricsStats], cluster_replicated: Boolean, time_range: TimeRange): List[AnomalyCollection] = {
    val td = new ThresholdDetection(cluster_replicated, time_range)
    td.workOnDistribution(arows)
  }

  def getErrorDetectionOnDistribution(arows: Array[Metrics.MetricsStats], cluster_replicated: Boolean, time_range: TimeRange): List[AnomalyCollection] = {
    val td = new ErrorDetection(cluster_replicated, time_range)
    td.workOnDistribution(arows)
  }

  def getResourceExhaustionDetectionOnDistribution(arows: Array[Metrics.MetricsStats], cluster_replicated: Boolean, time_range: TimeRange): List[AnomalyCollection] = {
    val td = new ResourceExhaustionDetection(cluster_replicated, time_range)
    td.workOnDistribution(arows)
  }

  def getHeartbeatDetectionOnDistribution(arows: Array[Metrics.MetricsStats], cluster_map: Cluster.ClusterMap, time_range: TimeRange)
  : List[AnomalyCollection] = {
    val td = new HeartbeatDetection(cluster_map, time_range)
    td.workOnDistribution(arows)
  }

  def getServiceDetectionOnDistribution(arows: Array[Metrics.MetricsStats], cluster_map: Cluster.ClusterMap, time_range: TimeRange)
  : List[AnomalyCollection] = {
    val td = new ServiceDetection(cluster_map, time_range)
    td.workOnDistribution(arows)
  }

  def getNetworkActivityDetectionOnDistribution(arows: Array[Metrics.MetricsStats], cluster_replicated: Boolean, time_range: TimeRange)
  : List[AnomalyCollection] = {
    val td = new NetworkActivityDetection(cluster_replicated, time_range)
    td.workOnDistribution(arows)
  }

  def getNetworkActivityDetectionOnDistribution(nsl: Array[NWObject.NWGraph], cluster_replicated: Boolean, time_range: TimeRange)
  : List[AnomalyCollection] = {
    val td = new NetworkActivityDetection(cluster_replicated, time_range)
    td.workOnDistribution(nsl)
  }

  //This should be called for all the traffic collected every minute
  def writeNetworkActivityByService(arows: Array[Metrics.MetricsStats], cluster_replicated: Boolean, time_range: TimeRange): Unit = {
    val td = new NetworkActivityDetection(cluster_replicated, time_range)
    td.writeNWSvcToKairosDB(arows)
  }

  def writeNetworkActivityByService(nsl: Array[NWObject.NWGraph], cluster_replicated: Boolean, time_range: TimeRange): Unit = {
    val td = new NetworkActivityDetection(cluster_replicated, time_range)
    td.writeNWSvcToKairosDB(nsl)
  }

  def getObservations(conn_type: Int, ac: List[AnomalyCollection]): List[MetricsStats] = {
    val td = new NetworkActivityDetection(false, null)
    td.getObservationsFromAnomalyList(conn_type, ac)
  }

  def processNWConnections(conn_type: Int, arows: Array[MetricsStats], cluster_replicated: Boolean, time_range: TimeRange): List[NWObject.NWGraph] = {
    val td = new NetworkActivityDetection(false, null)
    td.processNWConnections(conn_type, arows, cluster_replicated, time_range)
  }

  def processNWConnections(conn_type: Int, nsl: Array[NWObject.NWGraph], acl: Array[AnomalyCollection], time_range: TimeRange): List[NWObject.NWGraph] = {
    val td = new NetworkActivityDetection(false, null)
    td.processNWConnections(conn_type, nsl, acl, time_range)
  }

  def processServices(customer_id :String, deployment_id : String, cluster_id : String, host_ip : String,
                      acl : Array[AnomalyCollection], time_range : TimeRange) : List[NWObject.ServiceInfo] = {
    val td = new NetworkActivityDetection(false, null)
    td.processServices(customer_id, deployment_id, cluster_id, host_ip, acl, time_range)
  }

  def writeToDB(detections: List[AnomalyCollection], ttl: Option[Int]): Boolean = {
    if (detections.isEmpty) {
      return false
    }
    // Batch statements in cassandra are used for atomicity and not for volume or performance
    // It also has a size limitation of 5K
    val query_list = {
      var str = List[String]()
      detections.foreach(x => {
        val ts = x.node.ts
        val duration = x.anomaly.qs.total_duration
        Log.getLogger.trace(s"The duration = ${duration}")
        val customer_id = x.node.customer_id
        val deployment_id = x.node.deployment_id
        val cluster_id = x.node.cluster_id
        val host_ip = x.node.host_ip
        val host_name = x.node.host_name
        val is_non_numeric = x.node.is_non_numeric
        val target = x.node.target
        val plugin = x.node.plugin
        val classification = x.node.classification
        val class_type = x.anomaly.cls
        val sub_type = x.anomaly.subType
        val thermal = x.anomaly.qs.severity
        val sc = JacksonWrapper.serialize(x.anomaly)
        val tags = JacksonWrapper.serialize(x.tags)
        val uhash: String = {
          s"${customer_id},${deployment_id},${cluster_id},${host_ip},${target},${plugin},${classification},${class_type},${sub_type},${ts},${duration}"
        }

        val TTL = ttl.getOrElse(86400)
        val tmp_str = s"INSERT INTO ${ASC.TABLE_NAME}(${ASC.TS}, ${ASC.DURATION}, ${ASC.UHASH}, ${ASC.CUSTOMER_ID}, ${ASC.DEPLOYMENT_ID}, " +
          s"${ASC.CLUSTER_ID}, ${ASC.HOST_IP}, ${ASC.HOST_NAME}, " +
          s"${ASC.TARGET}, ${ASC.PLUGIN}, ${ASC.CLASSIFICATION} , ${ASC.IS_NON_NUMERIC}, " +
          s"${ASC.CLASS_TYPE}, ${ASC.SUB_TYPE}, ${ASC.THERMAL}, ${ASC.SC},  ${ASC.TAGS})" +
          s" VALUES(${ts}, ${duration}, '${uhash}', '${customer_id}', '${deployment_id}', '${cluster_id}'," +
          s" '${host_ip}', '${host_name}', '${target}', '${plugin}', '${classification}', ${is_non_numeric}, '${class_type}', '${sub_type}'," +
          s" ${thermal}, '${sc}', '${tags}') USING TTL ${TTL};\n "
        str ::= tmp_str
      })
      str
    }

    var status = true
    query_list.foreach(query => {
      status &= {
        val cassdb = getCassandraHandle
        val st = {
          try {
            val resultSet = cassdb.executeQuery(query)
            true
          } catch {
            case e: Exception => {
              Log.getLogger.error(e.getMessage)
              false
            }
          }
        }
        cassdb.close()
        st
      }
    })
    status
  }


  private def getCassandraHandle: CassDB = {
    val addressList = new ListBuffer[CassandraDB.Address]()
    addressList += new Address("127.0.0.1", 9042)
    CassandraDB.getScalaInstance("nuvidata", addressList.toList)
  }

  object ASC {
    val TABLE_NAME = "anomaly_stats"
    val TS = "ts"
    val DURATION = "duration"
    val UHASH = "uhash"
    val CUSTOMER_ID = "customer_id"
    val DEPLOYMENT_ID = "deployment_id"
    val CLUSTER_ID = "cluster_id"
    val HOST_IP = "host_ip"
    val HOST_NAME = "host_name"
    val TARGET = "target"
    val PLUGIN = "plugin"
    val CLASSIFICATION = "classification"
    val IS_NON_NUMERIC = "is_non_numeric"
    val CLASS_TYPE = "class_type"
    val SUB_TYPE = "sub_type"
    val THERMAL = "thermal"
    val SC = "sc"
    val TAGS = "tags"
  }

  def getFromDB(customer_id: String, deployment_id: String, ts: Option[List[Long]], duration: Option[Long],
                cluster_id: Option[String], host_ip: Option[String], host_name: Option[String], target: Option[String],
                plugin: Option[String], classification: Option[String], class_type: Option[String],
                sub_type: Option[String], thermal: Option[Int], uhash: Option[List[String]]): List[AnomalyCollection] = {
    var detections = List[AnomalyCollection]()
    val query = {
      var str = s"SELECT ${ASC.TS}, ${ASC.DURATION}, ${ASC.CUSTOMER_ID}, ${ASC.DEPLOYMENT_ID}, ${ASC.CLUSTER_ID}, " +
        s"${ASC.HOST_IP}, ${ASC.HOST_NAME}, ${ASC.TARGET}, ${ASC.PLUGIN}," +
        s"${ASC.CLASSIFICATION}, ${ASC.CLASS_TYPE}, ${ASC.SUB_TYPE}, ${ASC.THERMAL}, ${ASC.IS_NON_NUMERIC}, " +
        s"${ASC.SC}, ${ASC.TAGS} FROM ${ASC.TABLE_NAME} " +
        s"WHERE ${ASC.CUSTOMER_ID} = '${customer_id}' AND ${ASC.DEPLOYMENT_ID} = '${deployment_id}'"
      if (duration.nonEmpty) str += s" AND ${ASC.DURATION} = ${duration.get}"
      if (cluster_id.nonEmpty) str += s" AND ${ASC.CLUSTER_ID} = '${cluster_id.get}'"
      if (host_ip.nonEmpty) str += s" AND ${ASC.HOST_IP} = '${host_ip.get}'"
      if (host_name.nonEmpty) str += s" AND ${ASC.HOST_NAME} = '${host_name.get}'"
      if (target.nonEmpty) str += s" AND ${ASC.TARGET} = '${target.get}'"
      if (plugin.nonEmpty) str += s" AND ${ASC.PLUGIN} = '${plugin.get}'"
      if (classification.nonEmpty) str += s" AND ${ASC.CLASSIFICATION} = '${classification.get}'"
      if (class_type.nonEmpty) str += s" AND ${ASC.CLASS_TYPE} = '${class_type.get}'"
      if (sub_type.nonEmpty) str += s" AND ${ASC.SUB_TYPE} = '${sub_type.get}'"
      if (thermal.nonEmpty) str += s" AND ${ASC.THERMAL} = ${thermal.get}"
      if (ts.nonEmpty) {
        str += s" AND ${ASC.TS} IN ("
        val tsList = ts.get
        tsList.foreach(x => {
          str += s"${x},"
        })
        str += s"${tsList.last})"
      }

      if (uhash.nonEmpty) {
        str += s" AND ${ASC.UHASH} IN ("
        uhash.get.foreach(x => {
          str += s"'${x}',"
        })
        str += s"'${uhash.get.last}')"
      }
      str += " ALLOW FILTERING;"
      str
    }

    val cassdb = getCassandraHandle
    val resultSet = cassdb.executeQuery(query)
    resultSet.asScala.foreach(x => {
      val ts = x.getTimestamp(ASC.TS).getTime
      val duration = x.getInt(ASC.DURATION)
      val customer_id = x.getString(ASC.CUSTOMER_ID)
      val deployment_id = x.getString(ASC.DEPLOYMENT_ID)
      val cluster_id = x.getString(ASC.CLUSTER_ID)
      val host_ip = x.getString(ASC.HOST_IP)
      val host_name = x.getString(ASC.HOST_NAME)
      val target = x.getString(ASC.TARGET)
      val plugin = x.getString(ASC.PLUGIN)
      val is_non_numeric = x.getBool(ASC.IS_NON_NUMERIC)
      val classification = x.getString(ASC.CLASSIFICATION)
      val class_type = x.getString(ASC.CLASS_TYPE)
      val sub_type = x.getString(ASC.SUB_TYPE)
      val thermal = x.getInt(ASC.THERMAL)
      val sc_str = x.getString(ASC.SC)
      val tagstr = x.getString(ASC.TAGS)
      val ms = new MetricsStats(ts, customer_id, deployment_id, cluster_id, target, host_ip, host_name, plugin,
        classification, 0, 0, is_non_numeric, "{}", "{}", "{}", "")
      val _sc = JacksonWrapper.deserialize[SeverityClassification](sc_str)
      Log.getLogger.debug(s"The _sc = ${_sc}")
      val tags = JacksonWrapper.deserialize[Map[String, String]](tagstr)
      val sc = new SeverityClassification(class_type, sub_type, _sc.qs, _sc.ws, _sc.hm)
      detections ::= new AnomalyCollection(ms, sc, tags)
    })
    cassdb.close()
    detections
  }

  object Generic extends AnomalyTrait {
    val class_type = "Generic"
    val sub_type = "Generic"

    def quantizeAnomalies(detections: List[AnomalyCollection], cluster_replicated: Boolean, time_range: TimeRange): AnomalyCollection = {
      var critInfo = HashMap[Int, Int]()
      sevGrades.foreach(x => {
        critInfo.getOrElseUpdate(x.grade, 0)
      })

      var windowScoreList = List[Map[Int,Array[WindowScore]]]()
      var hm = HashMap[Long,Int]()
      detections.filter(x => {
        (x.node.ts >= time_range.start && x.node.ts <= time_range.end)
      })
        .sortWith(_.node.ts < _.node.ts).foreach(x => {
        x.anomaly.hm.foreach(x => {
          Log.getLogger.debug(s"The value = ${x}")
          val tpl = s"${x}".replace("(", "").replace(")", "").split(",")
          val k = tpl.head.toLong
          val v = tpl.last.toInt
          hm += (k -> v)
        })
        windowScoreList ::= x.anomaly.ws
      })
      val ws = Generic.getResultantWindowScore(windowScoreList)
      val qs = Generic.getQuantizedSeverity(windowScoreList, cluster_replicated, time_range)
      if (qs.severity != sevGrades.last.grade) {
        val z = detections.head
        val message = Generic.getMessage(z.node, z.anomaly.subType, qs, cluster_replicated)
        var tags = HashMap[String, String]()
        tags += ("message" -> message)
        val sc = new SeverityClassification(z.anomaly.cls, z.anomaly.subType, qs, ws, hm.toMap)
        new AnomalyCollection(z.node, sc, tags.toMap)
      } else {
        null
      }
    }
  }

}
