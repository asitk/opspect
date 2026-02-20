package com.opspect.entry

import com.opspect.entry.Metrics._
import com.opspect.entry.Tokenize._
import com.opspect.entry.infra.Service.ServiceMap
import com.opspect.entry.infra.{Cluster, Node, Service}
import com.opspect.util.CassandraDB._
import com.opspect.util._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.collection.mutable.{HashMap, ListBuffer}

/** Created by prashun on 21/7/16.
  */
object NWObject {
  private def getCassandraHandle: CassDB = {
    val addressList = new ListBuffer[CassandraDB.Address]()
    addressList += new Address("127.0.0.1", 9042)
    CassandraDB.getScalaInstance("nuvidata", addressList.toList)
  }

  case class NWSvcDetail(
      method: String,
      customer_id: String,
      deployment_id: String,
      nw_svc_info_list: List[NWSvcInfo],
      time_range: TimeRange
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class NWSvcInfo(
      cluster_id: String,
      name: String,
      svc: Int,
      ipver: Int,
      proto: Int,
      port: Int,
      interface: String
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class ServiceInfo(svc_info: NWSvcInfo, observations: Map[String, String])
      extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  object ConnType {
    val NODE: Int = 0
    val CLUSTER: Int = 1
    val DEPLOYMENT: Int = 2
  }

  case class RequestStat(req: String, count: Long, ttfb: Long, ttlb: Long)
      extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class NWGraph(
      to: Node.NodeMap,
      from: Node.NodeMap,
      svc_info: NWSvcInfo,
      avg_rsp_size: Long,
      avg_ttfb: Long,
      avg_ttlb: Long,
      max_ttfb: Long,
      max_ttlb: Long,
      error_count: Long,
      request_count: Long,
      sent_bytes: Long,
      recv_bytes: Long,
      topmost_error_stats: Array[RequestStat],
      costliest_request_stats: Array[RequestStat],
      ts: Long,
      duration: Long
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class NWBinding(to: Node.NodeMap, from: Node.NodeMap, scvInfo: NWSvcInfo)
      extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  def findValue(
      arows: Array[Metrics.MetricsStats],
      classification: String
  ): String = {
    arows.filter(_.classification == classification).head.val_str
  }

  def populateNodeMapFromValue(
      x: Array[Metrics.MetricsStats],
      is_src_ip: Boolean
  ): Node.NodeMap = {
    val nm = {
      val ip = {
        val type_of_ip = if (is_src_ip) "src_ip" else "dst_ip"
        x.filter(_.classification == type_of_ip).head.val_str
      }
      if (ip != x.head.host_ip) {
        val nm = Node.getMaps(
          Some(x.head.customer_id),
          Some(x.head.deployment_id),
          None,
          Some(ip),
          None,
          None
        )
        if (nm.isEmpty) {
          new Node.NodeMap("*", "*", "*", ip, "*", "unknown", true, x.head.ts)
        } else {
          nm.head
        }
      } else {
        new Node.NodeMap(
          x.head.customer_id,
          x.head.deployment_id,
          x.head.cluster_id,
          ip,
          x.head.host_name,
          "unknown",
          true,
          0
        )
      }
    }
    new Node.NodeMap(
      nm.customer_id,
      nm.deployment_id,
      nm.cluster_id,
      nm.host_ip,
      nm.host_name,
      nm.scope,
      nm.active,
      0L
    )
  }

  def populateNWSvcByTS(
      x: Array[Metrics.MetricsStats],
      nw_svc: List[NWSvcDetail]
  ): NWGraph = {
    val ts = x.head.ts
    val src_ip = findValue(x, "src_ip")
    val from = populateNodeMapFromValue(x, true)
    val dst_ip = findValue(x, "dst_ip")
    val to = populateNodeMapFromValue(x, false)
    val dst_port = findValue(x, "dst_port").toInt
    val svc_info = {
      val si = nw_svc
        .map(x => {
          val svc_info_list = x.nw_svc_info_list.filter(x => {
            (x.port == dst_port && x.cluster_id == to.cluster_id)
          })
          if (svc_info_list.isEmpty) {
            return null
          } else {
            svc_info_list.head
          }
        })
        .filter(_ != null)
        .head
      si
    }

    val avg_rsp_size = findValue(x, "avg_rsp_size").toDouble.toLong
    val avg_ttfb = findValue(x, "avg_ttfb").toDouble.toLong
    val avg_ttlb = findValue(x, "avg_ttlb").toDouble.toLong
    val max_ttfb = findValue(x, "max_ttfb").toDouble.toLong
    val max_ttlb = findValue(x, "max_ttlb").toDouble.toLong
    val recv_bytes = findValue(x, "recv_bytes").toDouble.toLong
    val sent_bytes = findValue(x, "sent_bytes").toDouble.toLong
    val req_count = findValue(x, "req_count").toDouble.toLong
    val err_count = findValue(x, "err_count").toDouble.toLong
    val costliest_request_str = findValue(x, "costliest_request_str")
    val topmost_error_request_count =
      findValue(x, "topmost_error_request_count").toDouble.toLong
    val topmost_error_request_str = findValue(x, "topmost_error_request_str")
    val topmost_error_request_ttfb =
      findValue(x, "topmost_error_request_ttfb").toDouble.toLong
    val costly_request = {
      val cr = new RequestStat(costliest_request_str, 1, max_ttfb, max_ttlb)
      Array(cr)
    }
    val err_request = {
      val er = new RequestStat(
        topmost_error_request_str,
        topmost_error_request_count,
        topmost_error_request_ttfb,
        topmost_error_request_ttfb
      )
      Array(er)
    }
    val duration = findValue(x, "duration").toDouble.toLong
    val observations = Map[String, String]()
    new NWGraph(
      to,
      from,
      svc_info,
      avg_rsp_size,
      avg_ttfb,
      avg_ttlb,
      max_ttfb,
      max_ttlb,
      err_count,
      req_count,
      recv_bytes,
      sent_bytes,
      err_request,
      costly_request,
      x.head.ts,
      duration
    )
  }

  def findAppropriateIndex(
      reqStat: Array[RequestStat],
      req: String
  ): (Int, Boolean) = {
    val reqStatMatched = reqStat.filter(x => {
      val (temp, status) = findStringTemplate(x.req, req)
      status
    })

    val (index, status) = {
      if (!reqStatMatched.isEmpty) {
        (reqStat.indexOf(reqStatMatched.head), true)
      } else {
        (-1, false)
      }
    }
    (index, status)
  }

  def getNWServices(
      customer_id: String,
      deployment_id: String,
      time_range: TimeRange
  ): List[NWSvcDetail] = {
    val nw_svc_details = {
      val key = s"svc_${customer_id},${deployment_id}"
      if (MyCache.get(key) == null) {
        val clusterMap = Cluster.getMaps(
          Some(customer_id),
          Some(deployment_id),
          None,
          Some(true),
          Some(time_range)
        )
        val cassdb = getCassandraHandle
        var arows = Array[MetricsStats]()
        clusterMap.foreach(cm => {
          val query = getServiceMetricsQuery(
            cm.customer_id,
            cm.deployment_id,
            cm.cluster_id,
            time_range,
            1
          )
          arows ++= getMetricsQueryResult(query)
        })
        getNWServices(customer_id, deployment_id, arows, time_range)
      } else {
        getServiceListFromDB(customer_id, deployment_id, time_range)
      }
    }
    nw_svc_details
  }

  def getServiceListFromDB(
      customer_id: String,
      deployment_id: String,
      time_range: TimeRange
  ): List[NWSvcDetail] = {
    val smList = Service.getMaps(
      Some(customer_id),
      Some(deployment_id),
      None,
      None,
      None,
      None,
      None,
      None,
      None
    )

    val nsd = List(
      new NWSvcDetail(
        "get_nw_svc_detail",
        customer_id,
        deployment_id,
        smList.map(x => {
          new NWSvcInfo(
            x.cluster_id,
            x.name,
            x.svc,
            x.ipver,
            x.proto,
            x.port,
            x.interface
          )
        }),
        time_range
      )
    )
    nsd
  }

  def getNWServices(
      customer_id: String,
      deployment_id: String,
      _arows: Array[MetricsStats],
      time_range: TimeRange
  ): List[NWSvcDetail] = {
    // Replace this with the cache implementation which sorts out the issue
    val key = s"svc_${customer_id},${deployment_id}"
    if (MyCache.get(key) == null) {
      val arows = _arows.filter(_.plugin == "procstat")
      val filterList = Array[String](
        "service_name",
        "service_type",
        "ipver",
        "port",
        "proto",
        "interface"
      )
      var nwSvcList = List[HashMap[String, Any]]()
      arows
        .filter(x => {
          filterList.contains(x.classification)
        })
        .groupBy(x => {
          (x.customer_id, x.deployment_id, x.cluster_id, x.target)
        })
        .values
        .foreach(x => {
          var hm = HashMap[String, Any]()
          val cluster_id = x.head.cluster_id
          val key = "cluster_id"
          hm += (key -> cluster_id)
          x.foreach(x => {
            hm += (x.classification -> x.val_str)
          })

          nwSvcList ::= hm
        })

      nwSvcList.foreach(x => {
        val cluster_id =
          x.getOrElse("cluster_id", "unknown").toString.stripMargin('\"')
        val name = x.getOrElse("service_name", "unknown").toString
        val svc = x.getOrElse("service_type", -1).toString.toDouble.toInt
        val ipver = x.getOrElse("ipver", -1).toString.toDouble.toInt
        val proto = x.getOrElse("proto", -1).toString.toDouble.toInt
        val port = x.getOrElse("port", -1).toString.toDouble.toInt
        val interface = x.getOrElse("interface", "").toString
        // Write to DB as well
        val sm = new ServiceMap(
          customer_id,
          deployment_id,
          cluster_id,
          name,
          svc,
          ipver,
          proto,
          port,
          interface,
          System.currentTimeMillis()
        )
        Service.setMap(sm)
      })
      MyCache.put(key, "bla")
    }
    getServiceListFromDB(customer_id, deployment_id, time_range)
  }

  var nw_svc = List[NWSvcDetail]()

  def prepareNWGraphList(
      customer_id: String,
      deployment_id: String,
      arows: Array[Metrics.MetricsStats],
      time_range: TimeRange
  ): List[NWGraph] = {
    nw_svc = getNWServices(customer_id, deployment_id, arows, time_range)
    if (!arows.exists(_.plugin == "nwgraph")) {
      return List[NWGraph]()
    }

    val nsl: Array[NWGraph] = {
      val _nsl = arows
        .filter(_.plugin == "nwgraph")
        .groupBy(x => {
          (x.customer_id, x.deployment_id, x.target)
        })
        .values
        .map(x => {
          try {
            populateNWSvcByTS(x, nw_svc)
          } catch {
            case e: Exception => {
              // Might crash as cassandra has not managed to get the full data yet so skip to next cycle
              Log.getLogger.trace("Not enough of nwgraph params in this cycle")
              null
            }
          }
        })
        .filter(_ != null)
        .toArray

      // Stats will be reported in some cases from both the client as well as server resulting in duplicate
      // entries, which should be removed
      var hm = HashMap[String, NWGraph]()
      if (!_nsl.isEmpty) {
        _nsl.foreach(x => {
          val key =
            s"${x.from.host_ip},${x.to.host_ip},${x.svc_info.port},${x.ts}"
          if (!hm.contains(key)) {
            hm += (key -> x)
          }
        })
      }
      hm.toArray.map(_._2)
    }
    nsl.toList
  }

  object CONN_STATS_COL {
    val TO_CUSTOMER_ID = "to_customer_id"
    val TO_DEPLOYMENT_ID = "to_deployment_id"
    val TO_CLUSTER_ID = "to_cluster_id"
    val TO_HOST_IP = "to_host_ip"
    val TO_HOST_NAME = "to_host_name"
    val TO_SCOPE = "to_scope"
    val TO_ACTIVE = "to_active"
    val TO_LAST_MODIFIED = "to_last_modified"
    val FROM_CUSTOMER_ID = "from_customer_id"
    val FROM_DEPLOYMENT_ID = "from_deployment_id"
    val FROM_CLUSTER_ID = "from_cluster_id"
    val FROM_HOST_IP = "from_host_ip"
    val FROM_HOST_NAME = "from_host_name"
    val FROM_SCOPE = "from_scope"
    val FROM_ACTIVE = "from_active"
    val FROM_LAST_MODIFIED = "from_last_modified"
    val SVC_INFO_CLUSTER_ID = "svc_info_cluster_id"
    val SVC_INFO_NAME = "svc_info_name"
    val SVC_INFO_SVC = "svc_info_svc"
    val SVC_INFO_IPVER = "svc_info_ipver"
    val SVC_INFO_PORT = "svc_info_port"
    val SVC_INFO_PROTO = "svc_info_proto"
    val SVC_INFO_INTERFACE = "svc_info_interface"
    val AVG_RSP_SIZE = "avg_rsp_size"
    val AVG_TTFB = "avg_ttfb"
    val AVG_TTLB = "avg_ttlb"
    val MAX_TTFB = "max_ttfb"
    val MAX_TTLB = "max_ttlb"
    val ERROR_COUNT = "error_count"
    val REQUEST_COUNT = "request_count"
    val SENT_BYTES = "sent_bytes"
    val RECV_BYTES = "recv_bytes"
    val TOPMOST_ERROR_STATS = "topmost_error_stats"
    val COSTLIEST_REQUEST_STATS = "costliest_request_stats"
    val TS = "ts"
    val DURATION = "duration"
    val UHASH = "uhash"
    val TABLE_NAME = "connection_stats"
  }

  def getFromDB(
      to_customer_id: String,
      to_deployment_id: String,
      tsList: Option[List[Long]],
      to_cluster_id: Option[String],
      to_host_ip: Option[String],
      from_customer_id: Option[String],
      from_deployment_id: Option[String],
      from_cluster_id: Option[String],
      from_host_ip: Option[String],
      svc_info_name: Option[String],
      svc_info_proto: Option[Int],
      svc_info_port: Option[Int],
      uhashList: Option[List[String]]
  ): List[NWGraph] = {
    var connections = List[NWGraph]()
    val query = {
      var str = s"SELECT ${CONN_STATS_COL.TO_CUSTOMER_ID}, ${CONN_STATS_COL.TO_DEPLOYMENT_ID}, " +
        s"${CONN_STATS_COL.TO_CLUSTER_ID}, ${CONN_STATS_COL.TO_HOST_IP}, ${CONN_STATS_COL.TO_HOST_NAME}, " +
        s"${CONN_STATS_COL.TO_SCOPE}, ${CONN_STATS_COL.TO_ACTIVE}, ${CONN_STATS_COL.TO_LAST_MODIFIED}, " +
        s"${CONN_STATS_COL.FROM_CUSTOMER_ID}, ${CONN_STATS_COL.FROM_DEPLOYMENT_ID}, ${CONN_STATS_COL.FROM_CLUSTER_ID}, " +
        s"${CONN_STATS_COL.FROM_HOST_IP}, ${CONN_STATS_COL.FROM_HOST_NAME}, ${CONN_STATS_COL.FROM_SCOPE}, " +
        s"${CONN_STATS_COL.FROM_ACTIVE}, ${CONN_STATS_COL.FROM_LAST_MODIFIED}, ${CONN_STATS_COL.SVC_INFO_CLUSTER_ID}, " +
        s"${CONN_STATS_COL.SVC_INFO_NAME}, ${CONN_STATS_COL.SVC_INFO_SVC}, ${CONN_STATS_COL.SVC_INFO_IPVER}, " +
        s"${CONN_STATS_COL.SVC_INFO_PROTO}, ${CONN_STATS_COL.SVC_INFO_PORT}, ${CONN_STATS_COL.SVC_INFO_INTERFACE}," +
        s"${CONN_STATS_COL.AVG_RSP_SIZE}, ${CONN_STATS_COL.AVG_TTFB}, ${CONN_STATS_COL.AVG_TTLB}, " +
        s"${CONN_STATS_COL.MAX_TTFB}, ${CONN_STATS_COL.MAX_TTLB}, ${CONN_STATS_COL.ERROR_COUNT}, " +
        s"${CONN_STATS_COL.REQUEST_COUNT}, ${CONN_STATS_COL.SENT_BYTES}, " +
        s"${CONN_STATS_COL.RECV_BYTES}, ${CONN_STATS_COL.TOPMOST_ERROR_STATS}, ${CONN_STATS_COL.COSTLIEST_REQUEST_STATS}, " +
        s"${CONN_STATS_COL.TS}, ${CONN_STATS_COL.DURATION} FROM ${CONN_STATS_COL.TABLE_NAME} " +
        s"WHERE ${CONN_STATS_COL.TO_CUSTOMER_ID} = '${to_customer_id}' AND ${CONN_STATS_COL.TO_DEPLOYMENT_ID} = '${to_deployment_id}'"
      if (to_cluster_id.nonEmpty)
        str += s" AND ${CONN_STATS_COL.TO_CLUSTER_ID} = '${to_cluster_id.get}'"
      if (to_host_ip.nonEmpty)
        str += s" AND ${CONN_STATS_COL.TO_HOST_IP} = '${to_host_ip.get}'"
      if (from_customer_id.nonEmpty)
        str += s" AND ${CONN_STATS_COL.FROM_CUSTOMER_ID} = '${from_customer_id.get}'"
      if (from_deployment_id.nonEmpty)
        str += s" AND ${CONN_STATS_COL.FROM_DEPLOYMENT_ID} = '${from_deployment_id.get}'"
      if (from_cluster_id.nonEmpty)
        str += s" AND ${CONN_STATS_COL.FROM_CLUSTER_ID} = '${from_cluster_id.get}'"
      if (from_host_ip.nonEmpty)
        str += s" AND ${CONN_STATS_COL.FROM_HOST_IP} = '${from_host_ip.get}'"
      if (svc_info_name.nonEmpty)
        str += s" AND ${CONN_STATS_COL.SVC_INFO_NAME} = '${svc_info_name.get}'"
      if (svc_info_proto.nonEmpty)
        str += s" AND ${CONN_STATS_COL.SVC_INFO_PROTO} = ${svc_info_proto.get}"
      if (svc_info_port.nonEmpty)
        str += s" AND ${CONN_STATS_COL.SVC_INFO_PORT} = ${svc_info_port.get}"
      if (tsList.nonEmpty) {
        str += s" AND ${CONN_STATS_COL.TS} IN ("
        tsList.get.foreach(x => {
          str += s"${x},"
        })
        str += s"${tsList.get.last})"
      }
      if (uhashList.nonEmpty) {
        str += s" AND ${CONN_STATS_COL.UHASH} IN ("
        uhashList.get.foreach(x => {
          str += s"'${x}',"
        })
        str += s"'${uhashList.get.last}')"
      }
      str += " ALLOW FILTERING;"
      str
    }

    val cassdb = getCassandraHandle
    val resultSet = cassdb.executeQuery(query)
    resultSet.asScala.foreach(x => {
      val to_customer_id = x.getString(CONN_STATS_COL.TO_CUSTOMER_ID)
      val to_deployment_id = x.getString(CONN_STATS_COL.TO_DEPLOYMENT_ID)
      val to_cluster_id = x.getString(CONN_STATS_COL.TO_CLUSTER_ID)
      val to_host_ip = x.getString(CONN_STATS_COL.TO_HOST_IP)
      val to_host_name = x.getString(CONN_STATS_COL.TO_HOST_NAME)
      val to_scope = x.getString(CONN_STATS_COL.TO_SCOPE)
      val to_active = x.getBool(CONN_STATS_COL.TO_ACTIVE)
      val to_last_modified = 0
      val to_node = {
        new Node.NodeMap(
          to_customer_id,
          to_deployment_id,
          to_cluster_id,
          to_host_ip,
          to_host_name,
          to_scope,
          to_active,
          to_last_modified
        )
      }

      val from_customer_id = x.getString(CONN_STATS_COL.FROM_CUSTOMER_ID)
      val from_deployment_id = x.getString(CONN_STATS_COL.FROM_DEPLOYMENT_ID)
      val from_cluster_id = x.getString(CONN_STATS_COL.FROM_CLUSTER_ID)
      val from_host_ip = x.getString(CONN_STATS_COL.FROM_HOST_IP)
      val from_host_name = x.getString(CONN_STATS_COL.FROM_HOST_NAME)
      val from_scope = x.getString(CONN_STATS_COL.FROM_SCOPE)
      val from_active = x.getBool(CONN_STATS_COL.FROM_ACTIVE)
      val from_last_modified = 0
      val from_node = {
        new Node.NodeMap(
          from_customer_id,
          from_deployment_id,
          from_cluster_id,
          from_host_ip,
          from_host_name,
          from_scope,
          from_active,
          from_last_modified
        )
      }

      val svc_info_cluster_id = x.getString(CONN_STATS_COL.SVC_INFO_CLUSTER_ID)
      val svc_info_name = x.getString(CONN_STATS_COL.SVC_INFO_NAME)
      val svc_info_svc = x.getInt(CONN_STATS_COL.SVC_INFO_SVC)
      val svc_info_ipver = x.getInt(CONN_STATS_COL.SVC_INFO_IPVER)
      val svc_info_port = x.getInt(CONN_STATS_COL.SVC_INFO_PORT)
      val svc_info_proto = x.getInt(CONN_STATS_COL.SVC_INFO_PROTO)
      val svc_info_interface = x.getString(CONN_STATS_COL.SVC_INFO_INTERFACE)
      val svc_info = {
        new NWSvcInfo(
          svc_info_cluster_id,
          svc_info_name,
          svc_info_svc,
          svc_info_ipver,
          svc_info_proto,
          svc_info_port,
          svc_info_interface
        )
      }

      val avg_rsp_size = x.getInt(CONN_STATS_COL.AVG_RSP_SIZE)
      val avg_ttfb = x.getInt(CONN_STATS_COL.AVG_TTFB).toLong
      val avg_ttlb = x.getInt(CONN_STATS_COL.AVG_TTLB).toLong
      val max_ttfb = x.getInt(CONN_STATS_COL.MAX_TTFB).toLong
      val max_ttlb = x.getInt(CONN_STATS_COL.MAX_TTLB).toLong
      val error_count = x.getInt(CONN_STATS_COL.ERROR_COUNT)
      val request_count = x.getInt(CONN_STATS_COL.REQUEST_COUNT)
      val sent_bytes = x.getInt(CONN_STATS_COL.SENT_BYTES).toLong
      val recv_bytes = x.getInt(CONN_STATS_COL.RECV_BYTES).toLong
      val topmost_error_stats = JacksonWrapper
        .deserialize[List[RequestStat]](
          x.getString(CONN_STATS_COL.TOPMOST_ERROR_STATS)
        )
        .toArray
      val costliest_request_stats = JacksonWrapper
        .deserialize[List[RequestStat]](
          x.getString(CONN_STATS_COL.COSTLIEST_REQUEST_STATS)
        )
        .toArray
      val ts = x.getTimestamp(CONN_STATS_COL.TS).getTime
      val duration = x.getLong(CONN_STATS_COL.DURATION)

      connections ::= {
        new NWGraph(
          to_node,
          from_node,
          svc_info,
          avg_rsp_size,
          avg_ttfb,
          avg_ttlb,
          max_ttfb,
          max_ttlb,
          error_count,
          request_count,
          sent_bytes,
          recv_bytes,
          topmost_error_stats,
          costliest_request_stats,
          ts,
          duration
        )
      }
    })
    cassdb.close()
    connections
  }

  def writeToDB(nls: List[NWGraph], ttl: Option[Int]): Boolean = {
    if (nls.isEmpty) {
      return false
    }
    // Batch statements in cassandra are for atomicity and not for volume or performance reasons
    // They also have limit in that the size of the statement cannot exceed 5K overall

    val query_list = {
      var str = List[String]()
      nls.foreach(x => {
        val to_customer_id = x.to.customer_id
        val to_deployment_id = x.to.deployment_id
        val to_cluster_id = x.to.cluster_id
        val to_host_ip = x.to.host_ip
        val to_host_name = x.to.host_name
        val to_scope = x.to.scope
        val to_active = x.to.active
        val to_last_modified = x.to.last_modified

        val from_customer_id = x.from.customer_id
        val from_deployment_id = x.from.deployment_id
        val from_cluster_id = x.from.cluster_id
        val from_host_ip = x.from.host_ip
        val from_host_name = x.from.host_name
        val from_scope = x.from.scope
        val from_active = x.from.active
        val from_last_modified = x.from.last_modified

        val svc_info_cluster_id = x.svc_info.cluster_id
        val svc_info_name = x.svc_info.name
        val svc_info_svc = x.svc_info.svc
        val svc_info_ipver = x.svc_info.ipver
        val svc_info_port = x.svc_info.port
        val svc_info_proto = x.svc_info.proto
        val svc_info_interface = x.svc_info.interface

        val avg_rsp_size = x.avg_rsp_size
        val avg_ttfb = x.avg_ttfb
        val avg_ttlb = x.avg_ttlb
        val max_ttfb = x.max_ttfb
        val max_ttlb = x.max_ttlb
        val error_count = x.error_count
        val request_count = x.request_count
        val sent_bytes = x.sent_bytes
        val recv_bytes = x.recv_bytes
        val topmost_error_stats =
          JacksonWrapper.serialize(x.topmost_error_stats)
        val costliest_request_stats =
          JacksonWrapper.serialize(x.costliest_request_stats)
        val ts = x.ts
        val duration = x.duration
        Log.getLogger.trace(s"The duration = ${duration}")
        val uhash = {
          s"${JacksonWrapper.serialize(x.to)},${JacksonWrapper.serialize(x.svc_info)}"
        }

        val TTL = ttl.getOrElse(86400)

        val tmp_str = s"INSERT INTO ${CONN_STATS_COL.TABLE_NAME}(${CONN_STATS_COL.TO_CUSTOMER_ID}, ${CONN_STATS_COL.TO_DEPLOYMENT_ID}, " +
          s"${CONN_STATS_COL.TO_CLUSTER_ID}, ${CONN_STATS_COL.TO_HOST_IP}, ${CONN_STATS_COL.TO_HOST_NAME}, " +
          s"${CONN_STATS_COL.TO_SCOPE}, ${CONN_STATS_COL.TO_ACTIVE}, ${CONN_STATS_COL.TO_LAST_MODIFIED}, " +
          s"${CONN_STATS_COL.FROM_CUSTOMER_ID}, ${CONN_STATS_COL.FROM_DEPLOYMENT_ID}, ${CONN_STATS_COL.FROM_CLUSTER_ID}, " +
          s"${CONN_STATS_COL.FROM_HOST_IP}, ${CONN_STATS_COL.FROM_HOST_NAME}, ${CONN_STATS_COL.FROM_SCOPE}, " +
          s"${CONN_STATS_COL.FROM_ACTIVE}, ${CONN_STATS_COL.FROM_LAST_MODIFIED}, ${CONN_STATS_COL.SVC_INFO_CLUSTER_ID}, " +
          s"${CONN_STATS_COL.SVC_INFO_NAME}, ${CONN_STATS_COL.SVC_INFO_SVC}, ${CONN_STATS_COL.SVC_INFO_PROTO}, " +
          s"${CONN_STATS_COL.SVC_INFO_PORT}, ${CONN_STATS_COL.SVC_INFO_IPVER}, ${CONN_STATS_COL.SVC_INFO_INTERFACE}, " +
          s"${CONN_STATS_COL.AVG_RSP_SIZE}, ${CONN_STATS_COL.AVG_TTFB}, ${CONN_STATS_COL.AVG_TTLB}, ${CONN_STATS_COL.MAX_TTFB}, " +
          s"${CONN_STATS_COL.MAX_TTLB}, ${CONN_STATS_COL.ERROR_COUNT}, ${CONN_STATS_COL.REQUEST_COUNT}, " +
          s"${CONN_STATS_COL.SENT_BYTES}, ${CONN_STATS_COL.RECV_BYTES}, ${CONN_STATS_COL.TOPMOST_ERROR_STATS}, " +
          s"${CONN_STATS_COL.COSTLIEST_REQUEST_STATS}, ${CONN_STATS_COL.TS}, " +
          s"${CONN_STATS_COL.DURATION}, ${CONN_STATS_COL.UHASH})" +
          s" VALUES ('${to_customer_id}', '${to_deployment_id}', '${to_cluster_id}', '${to_host_ip}', " +
          s"'${to_host_name}', '${to_scope}', ${to_active}, ${to_last_modified}, '${from_customer_id}', '${from_deployment_id}', " +
          s"'${from_cluster_id}', '${from_host_ip}', '${from_host_name}', '${from_scope}', ${from_active}, ${from_last_modified}, " +
          s"'${svc_info_cluster_id}', '${svc_info_name}', ${svc_info_svc}, ${svc_info_proto}, ${svc_info_port}, ${svc_info_ipver}," +
          s"'${svc_info_interface}', ${avg_rsp_size}, ${avg_ttfb}, ${avg_ttlb}, ${max_ttfb}, ${max_ttlb}, ${error_count}, " +
          s"${request_count}, ${sent_bytes}, ${recv_bytes}, '${topmost_error_stats}', '${costliest_request_stats}', " +
          s"${ts}, ${duration}, '${uhash}') USING TTL ${TTL};\n"
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
}
