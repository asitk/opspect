package com.opspect.entry.infra

import java.util.Calendar

import com.opspect.entry.anomaly.Anomaly
import com.opspect.entry.summarystats._
import com.opspect.entry.{NWObject, SummaryState}
import com.opspect.util.CassandraDB._
import com.opspect.util._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.util.hashing.{MurmurHash3 => MH3}

/** Created by prashun on 7/6/16.
  */
object Node extends Group {
  case class NodeMap(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_ip: String,
      host_name: String,
      scope: String,
      active: Boolean,
      last_modified: Long
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  object NODE_MAP_COLS {
    val TABLE_NAME = "node_map"
    val CUSTOMER_ID = "customer_id"
    val DEPLOYMENT_ID = "deployment_id"
    val CLUSTER_ID = "cluster_id"
    val HOST_IP = "host_ip"
    val HOST_NAME = "host_name"
    val SCOPE = "scope"
    val ACTIVE = "active"
    val TS = "ts"
  }

  def getMaps(
      customer_id: Option[String],
      deployment_id: Option[String],
      cluster_id: Option[String],
      host_ip: Option[String],
      active: Option[Boolean],
      time_range: Option[TimeRange]
  ): List[NodeMap] = {
    val cassdb = getCassandraHandle
    val cust_id_filtered = customer_id.orNull
    val deployment_id_filtered = deployment_id.orNull
    val cluster_id_filtered = cluster_id.orNull
    val host_ip_filtered = host_ip.orNull
    val active_filtered = active.getOrElse(false)
    val tr = time_range.orNull

    val query =
      s"SELECT ${NODE_MAP_COLS.CUSTOMER_ID}, ${NODE_MAP_COLS.DEPLOYMENT_ID}, ${NODE_MAP_COLS.CLUSTER_ID}, " +
        s"${NODE_MAP_COLS.HOST_IP}, ${NODE_MAP_COLS.HOST_NAME}, ${NODE_MAP_COLS.HOST_IP}, ${NODE_MAP_COLS.SCOPE}, " +
        s"${NODE_MAP_COLS.ACTIVE}, ${NODE_MAP_COLS.TS}  FROM ${NODE_MAP_COLS.TABLE_NAME}"
    val resultSet = cassdb.executeQuery(query)
    val node_map = {
      if (resultSet != null) {
        val node_map = resultSet.asScala
          .filter(
            tr == null || _.getTimestamp(NODE_MAP_COLS.TS).getTime <= tr.end
          )
          .filter(
            cust_id_filtered == _.getString(
              NODE_MAP_COLS.CUSTOMER_ID
            ) || cust_id_filtered == null
          )
          .filter(
            deployment_id_filtered == _.getString(
              NODE_MAP_COLS.DEPLOYMENT_ID
            ) || deployment_id_filtered == null
          )
          .filter(
            cluster_id_filtered == _.getString(
              NODE_MAP_COLS.CLUSTER_ID
            ) || cluster_id_filtered == null
          )
          .filter(
            host_ip_filtered == _.getString(
              NODE_MAP_COLS.HOST_IP
            ) || host_ip_filtered == null
          )
          .groupBy(row => {
            val customer_id = row.getString(NODE_MAP_COLS.CUSTOMER_ID)
            val deployment_id = row.getString(NODE_MAP_COLS.DEPLOYMENT_ID)
            val cluster_id = row.getString(NODE_MAP_COLS.CLUSTER_ID)
            val host_ip = row.getString(NODE_MAP_COLS.HOST_IP)

            s"${customer_id}::${deployment_id}::${cluster_id}::${host_ip}"
          })
          .mapValues(rows => {
            rows.head
          })
          .values
          .filter(
            active_filtered && _.getBool(
              NODE_MAP_COLS.ACTIVE
            ) || !active_filtered
          )
          .map(row => {
            new NodeMap(
              row.getString(NODE_MAP_COLS.CUSTOMER_ID),
              row.getString(NODE_MAP_COLS.DEPLOYMENT_ID),
              row.getString(NODE_MAP_COLS.CLUSTER_ID),
              row.getString(NODE_MAP_COLS.HOST_IP),
              row.getString(NODE_MAP_COLS.HOST_NAME),
              row.getString(NODE_MAP_COLS.SCOPE),
              row.getBool(NODE_MAP_COLS.ACTIVE),
              row.getTimestamp(NODE_MAP_COLS.TS).getTime
            )
          })
          .toList

        node_map
      } else {
        List()
      }
    }
    cassdb.close()
    node_map
  }

  def setMap(item: NodeMap): Boolean = {
    val cassdb = getCassandraHandle
    val ts = Calendar.getInstance().getTimeInMillis
    val query =
      s"INSERT INTO ${NODE_MAP_COLS.TABLE_NAME}(${NODE_MAP_COLS.CUSTOMER_ID}, ${NODE_MAP_COLS.DEPLOYMENT_ID}," +
        s"${NODE_MAP_COLS.CLUSTER_ID}, ${NODE_MAP_COLS.HOST_IP}, ${NODE_MAP_COLS.HOST_NAME}, ${NODE_MAP_COLS.SCOPE}, " +
        s"${NODE_MAP_COLS.ACTIVE},${NODE_MAP_COLS.TS}) " +
        s"VALUES ('${item.customer_id}, ${item.deployment_id}', '${item.cluster_id}', '${item.host_ip}', " +
        s"'${item.host_name}', ${item.scope}, ${item.active}, ${ts}) "
    val resultSet = cassdb.executeQuery(query)
    cassdb.close()
    true
  }

  def getTimelineMarkers(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_ip: String,
      time_range: TimeRange
  ): NodeTimelineMarker = {
    val nodeMap = Node.getMaps(
      Some(customer_id),
      Some(deployment_id),
      Some(cluster_id),
      Some(host_ip),
      Some(true),
      Some(time_range)
    )
    if (nodeMap.length == 1) {
      val hour_marker_list: List[Marker] = {
        val ssh = new SummaryStatsHourly(customer_id, deployment_id)
        val query = ssh.prepareTimelineQuery(
          cluster_id,
          nodeMap.head.host_name,
          time_range
        )
        val cassdb = getCassandraHandle
        val resultSet = cassdb.executeQuery(query)
        val markerList: List[Marker] = {
          if (resultSet != null) {
            resultSet.asScala
              .map(row => {
                val start: Long = row.getTimestamp(ssh.SSHC.TS).getTime
                val end: Long = start + 3600 * 1000
                new Marker(start, end, row.getInt(ssh.SSHC.THERMAL))
              })
              .toList
          } else {
            List()
          }
        }.sortWith(_.start < _.start)
        cassdb.close()
        markerList
      }
      val minute_marker_list: List[Marker] = {
        val ssh = new SummaryStatsMinute(customer_id, deployment_id)
        val query = ssh.prepareTimelineQuery(
          cluster_id,
          nodeMap.head.host_name,
          time_range
        )
        val cassdb = getCassandraHandle
        val resultSet = cassdb.executeQuery(query)
        val markerList: List[Marker] = {
          if (resultSet != null) {
            resultSet.asScala
              .map(row => {
                val start: Long = row.getTimestamp(ssh.SSMC.TS).getTime
                val end: Long = start + 60 * 1000
                new Marker(start, end, row.getInt(ssh.SSMC.THERMAL))
              })
              .toList
          } else {
            List()
          }
        }.sortWith(_.start < _.start)
        cassdb.close()
        markerList
      }
      new NodeTimelineMarker(
        "get_node_timeline_markers",
        customer_id,
        deployment_id,
        cluster_id,
        host_ip,
        nodeMap.head.host_name,
        nodeMap.head.scope,
        nodeMap.head.active,
        hour_marker_list ::: minute_marker_list,
        time_range
      )
    } else {
      throw new Exception(
        s"Customer_id = ${customer_id} with deployment_id = ${deployment_id}  with cluster_id = ${cluster_id} " +
          s"with host_ip = ${host_ip} doesnt exist in node_map"
      )
      null
    }

  }

  case class NodeDetailWithoutTimeRange(
      host_name: String,
      host_ip: String,
      scope: String,
      active: Boolean,
      thermal: Int,
      thermal_count: Map[Int, Int]
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }
  case class NodeDetail(
      method: String,
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_name: String,
      host_ip: String,
      scope: String,
      active: Boolean,
      thermal: Int,
      thermal_count: Map[Int, Int],
      time_range: TimeRange
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class NodeSnapshot(
      method: String,
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_name: String,
      host_ip: String,
      scope: String,
      active: Boolean,
      thermal: Int,
      thermal_count: Map[Int, Int],
      stats: List[SummaryState.Stats],
      thermal_summary: List[Anomaly.HeatMapDetailWithScore],
      thermal_reason: Anomaly.QuantizedSeverity,
      time_range: TimeRange
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class NodeConnection(
      method: String,
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_name: String,
      host_ip: String,
      scope: String,
      active: Boolean,
      connections: List[NWObject.NWGraph],
      time_range: TimeRange
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }
  case class NodeService(
      method: String,
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_name: String,
      host_ip: String,
      scope: String,
      active: Boolean,
      services: List[NWObject.ServiceInfo],
      time_range: TimeRange
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }
  case class NodeTimelineMarker(
      method: String,
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_ip: String,
      host_name: String,
      scope: String,
      active: Boolean,
      markers: List[Marker],
      time_range: TimeRange
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  def getDetails(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_ip: String,
      time_range: TimeRange
  ): List[NodeDetail] = {
    val deploymentMap = Deployment.getMaps(
      Some(customer_id),
      Some(deployment_id),
      None,
      Some(time_range)
    )
    var ni = List[NodeDetail]()
    deploymentMap.foreach(dm => {
      val clusterMap = Cluster.getMaps(
        Some(dm.customer_id),
        Some(dm.deployment_id),
        Some(cluster_id),
        Some(true),
        Some(time_range)
      )
      clusterMap.foreach(cm => {
        val cluster_replicated = cm.replicated
        val nodeMap = Node.getMaps(
          Some(dm.customer_id),
          Some(dm.deployment_id),
          Some(cluster_id),
          Some(host_ip),
          Some(true),
          Some(time_range)
        )
        ni = nodeMap.map(nm => {
          val thermal_structs_hourly: List[(Long, Int, String)] = {
            val cassdb = getCassandraHandle
            val ssh = new SummaryStatsHourly(customer_id, deployment_id)
            val query =
              ssh.prepareDetailsQuery(nm.cluster_id, nm.host_name, time_range)
            val resultSet = cassdb.executeQuery(query)
            val thermalStructs: Array[(Long, Int, String)] = {
              if (resultSet != null) {
                resultSet.asScala
                  .map(row => {
                    (
                      row.getTimestamp(ssh.SSHC.TS).getTime,
                      row.getInt(ssh.SSHC.THERMAL),
                      row.getString(ssh.SSHC.THERMAL_STATS)
                    )
                  })
                  .toArray
              } else {
                Array()
              }
            }
            cassdb.close()
            thermalStructs.toList
          }

          val thermal_structs_minute: List[(Long, Int, String)] = {
            val cassdb = getCassandraHandle
            val ssh = new SummaryStatsMinute(customer_id, deployment_id)
            val query =
              ssh.prepareDetailsQuery(nm.cluster_id, nm.host_name, time_range)
            val resultSet = cassdb.executeQuery(query)
            val thermalStructs: Array[(Long, Int, String)] = {
              if (resultSet != null) {
                resultSet.asScala
                  .map(row => {
                    (
                      row.getTimestamp(ssh.SSMC.TS).getTime,
                      row.getInt(ssh.SSMC.THERMAL),
                      row.getString(ssh.SSMC.THERMAL_STATS)
                    )
                  })
                  .toArray
              } else {
                Array()
              }
            }
            cassdb.close()
            thermalStructs.toList
          }
          val qtcounts = getThermalStats(
            (thermal_structs_hourly ::: thermal_structs_minute).toArray,
            cluster_replicated
          )
          new NodeDetail(
            "get_node_detail",
            nm.customer_id,
            nm.deployment_id,
            nm.cluster_id,
            nm.host_name,
            nm.host_ip,
            nm.scope,
            nm.active,
            qtcounts._1,
            qtcounts._2,
            time_range
          )
        })
      })
    })

    ni
  }

  def getSnapshot(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_ip: String,
      time_range: TimeRange
  ): List[NodeSnapshot] = {
    val deploymentMap = Deployment.getMaps(
      Some(customer_id),
      Some(deployment_id),
      None,
      Some(time_range)
    )
    var nd = List[NodeSnapshot]()
    deploymentMap.foreach(dm => {
      val clusterMap = Cluster.getMaps(
        Some(dm.customer_id),
        Some(dm.deployment_id),
        Some(cluster_id),
        Some(true),
        Some(time_range)
      )
      clusterMap.foreach(cm => {
        val cluster_replicated = cm.replicated
        val nodeMap = Node.getMaps(
          Some(dm.customer_id),
          Some(dm.deployment_id),
          Some(cluster_id),
          Some(host_ip),
          Some(true),
          Some(time_range)
        )
        nd = nodeMap
          .map(nm => {
            val thermal_structs_hourly
                : List[(Long, Int, String, String, String, String)] = {
              val cassdb = getCassandraHandle
              val ssh = new SummaryStatsHourly(customer_id, deployment_id)
              val query = ssh
                .prepareSnapshotQuery(nm.cluster_id, nm.host_name, time_range)
              val resultSet = cassdb.executeQuery(query)
              val thermalStructs
                  : Array[(Long, Int, String, String, String, String)] = {
                if (resultSet != null) {
                  resultSet.asScala
                    .map(row => {
                      (
                        row.getTimestamp(ssh.SSHC.TS).getTime,
                        row.getInt(ssh.SSHC.THERMAL),
                        row.getString(ssh.SSHC.THERMAL_STATS),
                        row.getString(ssh.SSHC.STATS),
                        row.getString(ssh.SSHC.THERMAL_SUMMARY),
                        row.getString(ssh.SSHC.THERMAL_REASON)
                      )
                    })
                    .toArray
                } else {
                  Array()
                }
              }
              cassdb.close()
              thermalStructs.toList
            }

            val thermal_structs_minute
                : List[(Long, Int, String, String, String, String)] = {
              val cassdb = getCassandraHandle
              val ssh = new SummaryStatsMinute(customer_id, deployment_id)
              val query = ssh
                .prepareSnapshotQuery(nm.cluster_id, nm.host_name, time_range)
              val resultSet = cassdb.executeQuery(query)
              val thermalStructs
                  : Array[(Long, Int, String, String, String, String)] = {
                if (resultSet != null) {
                  resultSet.asScala
                    .map(row => {
                      (
                        row.getTimestamp(ssh.SSMC.TS).getTime,
                        row.getInt(ssh.SSMC.THERMAL),
                        row.getString(ssh.SSMC.THERMAL_STATS),
                        row.getString(ssh.SSMC.STATS),
                        row.getString(ssh.SSMC.THERMAL_SUMMARY),
                        row.getString(ssh.SSMC.THERMAL_REASON)
                      )
                    })
                    .toArray
                } else {
                  Array()
                }
              }
              cassdb.close()
              thermalStructs.toList
            }

            val thermalStructs = {
              (thermal_structs_hourly ::: thermal_structs_minute).toArray
            }

            if (thermalStructs.nonEmpty) {

              var statsList = List[SummaryState.Stats]()
              thermalStructs
                .map(_._4)
                .foreach(x => {
                  val stats: List[SummaryState.Stats] =
                    JacksonWrapper.deserialize[List[SummaryState.Stats]](x)
                  if (stats.nonEmpty) {
                    statsList :::= stats
                  }
                })

              val thermal_summary = JacksonWrapper
                .deserialize[List[Anomaly.HeatMapDetailWithScore]](
                  thermalStructs.head._5
                )
              val thermal_reason = JacksonWrapper
                .deserialize[Anomaly.QuantizedSeverity](thermalStructs.head._6)

              val qtcounts = getThermalStats(
                thermalStructs.map(x => {
                  (x._1, x._2, x._3)
                }),
                cluster_replicated
              )

              new NodeSnapshot(
                "get_node_snapshot",
                nm.customer_id,
                nm.deployment_id,
                nm.cluster_id,
                nm.host_name,
                nm.host_ip,
                nm.scope,
                nm.active,
                qtcounts._1,
                qtcounts._2,
                statsList.sortWith(_.contribution.rank < _.contribution.rank),
                thermal_summary,
                thermal_reason,
                time_range
              )
            } else {
              null
            }
          })
          .filterNot(_ == null)
      })
    })

    nd
  }

  def getConnection(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_ip: String,
      time_range: TimeRange
  ): List[NodeConnection] = {
    val deploymentMap = Deployment.getMaps(
      Some(customer_id),
      Some(deployment_id),
      None,
      Some(time_range)
    )
    var nd = List[NodeConnection]()
    deploymentMap.foreach(dm => {
      val clusterMap = Cluster.getMaps(
        Some(dm.customer_id),
        Some(dm.deployment_id),
        Some(cluster_id),
        Some(true),
        Some(time_range)
      )
      clusterMap.foreach(cm => {
        val cluster_replicated = cm.replicated
        val nodeMap = Node.getMaps(
          Some(dm.customer_id),
          Some(dm.deployment_id),
          Some(cluster_id),
          Some(host_ip),
          Some(true),
          Some(time_range)
        )
        nd = nodeMap
          .map(nm => {
            val connections_structs_hourly: List[(Long, String)] = {
              val cassdb = getCassandraHandle
              val ssh = new SummaryStatsHourly(customer_id, deployment_id)
              val query = ssh
                .prepareConnectionQuery(nm.cluster_id, nm.host_name, time_range)
              val resultSet = cassdb.executeQuery(query)
              val connectionStructs: Array[(Long, String)] = {
                if (resultSet != null) {
                  resultSet.asScala
                    .map(row => {
                      (
                        row.getTimestamp(ssh.SSHC.TS).getTime,
                        row.getString(ssh.SSHC.CONNECTIONS)
                      )
                    })
                    .toArray
                } else {
                  Array()
                }
              }
              cassdb.close()
              connectionStructs.toList
            }

            val connections_structs_minute: List[(Long, String)] = {
              val cassdb = getCassandraHandle
              val ssh = new SummaryStatsMinute(customer_id, deployment_id)
              val query = ssh
                .prepareConnectionQuery(nm.cluster_id, nm.host_name, time_range)
              val resultSet = cassdb.executeQuery(query)
              val connectionStructs: Array[(Long, String)] = {
                if (resultSet != null) {
                  resultSet.asScala
                    .map(row => {
                      (
                        row.getTimestamp(ssh.SSMC.TS).getTime,
                        row.getString(ssh.SSMC.CONNECTIONS)
                      )
                    })
                    .toArray
                } else {
                  Array()
                }
              }
              cassdb.close()
              connectionStructs.toList
            }

            val connectionStructs = {
              (connections_structs_hourly ::: connections_structs_minute).toArray
            }

            if (connectionStructs.nonEmpty) {
              var connList = List[NWObject.NWGraph]()
              connectionStructs
                .map(_._2)
                .foreach(x => {
                  val connections: List[NWObject.NWGraph] =
                    JacksonWrapper.deserialize[List[NWObject.NWGraph]](x)
                  if (connections.nonEmpty) {
                    connList :::= connections
                  }
                })

              new NodeConnection(
                "get_node_connection",
                nm.customer_id,
                nm.deployment_id,
                nm.cluster_id,
                nm.host_name,
                nm.host_ip,
                nm.scope,
                nm.active,
                connList,
                time_range
              )
            } else {
              null
            }
          })
          .filterNot(_ == null)
      })
    })

    nd
  }

  def getService(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      host_ip: String,
      time_range: TimeRange
  ): List[NodeService] = {
    val deploymentMap = Deployment.getMaps(
      Some(customer_id),
      Some(deployment_id),
      None,
      Some(time_range)
    )
    var nd = List[NodeService]()
    deploymentMap.foreach(dm => {
      val clusterMap = Cluster.getMaps(
        Some(dm.customer_id),
        Some(dm.deployment_id),
        Some(cluster_id),
        Some(true),
        Some(time_range)
      )
      clusterMap.foreach(cm => {
        val cluster_replicated = cm.replicated
        val nodeMap = Node.getMaps(
          Some(dm.customer_id),
          Some(dm.deployment_id),
          Some(cluster_id),
          Some(host_ip),
          Some(true),
          Some(time_range)
        )
        nd = nodeMap
          .map(nm => {
            val services_structs_hourly: List[(Long, String)] = {
              val cassdb = getCassandraHandle
              val ssh = new SummaryStatsHourly(customer_id, deployment_id)
              val query =
                ssh.prepareServiceQuery(nm.cluster_id, nm.host_name, time_range)
              val resultSet = cassdb.executeQuery(query)
              val serviceStructs: Array[(Long, String)] = {
                if (resultSet != null) {
                  resultSet.asScala
                    .map(row => {
                      (
                        row.getTimestamp(ssh.SSHC.TS).getTime,
                        row.getString(ssh.SSHC.SERVICES)
                      )
                    })
                    .toArray
                } else {
                  Array()
                }
              }
              cassdb.close()
              serviceStructs.toList
            }

            val services_structs_minute: List[(Long, String)] = {
              val cassdb = getCassandraHandle
              val ssh = new SummaryStatsMinute(customer_id, deployment_id)
              val query =
                ssh.prepareServiceQuery(nm.cluster_id, nm.host_name, time_range)
              val resultSet = cassdb.executeQuery(query)
              val serviceStructs: Array[(Long, String)] = {
                if (resultSet != null) {
                  resultSet.asScala
                    .map(row => {
                      (
                        row.getTimestamp(ssh.SSMC.TS).getTime,
                        row.getString(ssh.SSMC.SERVICES)
                      )
                    })
                    .toArray
                } else {
                  Array()
                }
              }
              cassdb.close()
              serviceStructs.toList
            }

            val serviceStructs = {
              (services_structs_hourly ::: services_structs_minute).toArray
            }

            if (serviceStructs.nonEmpty) {
              var servicesList = List[NWObject.ServiceInfo]()
              serviceStructs
                .map(_._2)
                .foreach(x => {
                  val services: List[NWObject.ServiceInfo] =
                    JacksonWrapper.deserialize[List[NWObject.ServiceInfo]](x)
                  if (services.nonEmpty) {
                    servicesList :::= services
                  }
                })

              new NodeService(
                "get_node_service",
                nm.customer_id,
                nm.deployment_id,
                nm.cluster_id,
                nm.host_name,
                nm.host_ip,
                nm.scope,
                nm.active,
                servicesList,
                time_range
              )
            } else {
              null
            }
          })
          .filterNot(_ == null)
      })
    })

    nd
  }
}
