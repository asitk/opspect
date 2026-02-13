package com.infrared.entry.infra

import java.util.Calendar

import com.infrared.entry.anomaly.Anomaly
import com.infrared.entry.infra.Node.NodeDetailWithoutTimeRange
import com.infrared.entry.summarystats._
import com.infrared.entry.{NWObject, SummaryState}
import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.collection.JavaConverters._
import scala.collection.immutable._

/**
 * Created by prashun on 7/6/16.
 */
object Cluster extends Group {

  case class ClusterMap(customer_id: String, deployment_id: String, cluster_id: String, name: String, description: String,
                        role: String, active: Boolean, replicated: Boolean, last_modified: Long) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  object CLUSTER_MAP_COLS {
    val TABLE_NAME = "cluster_map"
    val CUSTOMER_ID = "customer_id"
    val DEPLOYMENT_ID = "deployment_id"
    val CLUSTER_ID = "cluster_id"
    val NAME = "name"
    val DESCRIPTION = "description"
    val ROLE = "role"
    val ACTIVE = "active"
    val REPLICATED = "replicated"
    val TS = "ts"
  }

  def getMaps(customer_id: Option[String], deployment_id: Option[String], cluster_id: Option[String],
              active: Option[Boolean], time_range: Option[TimeRange]): List[ClusterMap] = {
    val cassdb = getCassandraHandle
    val cust_id_filtered = customer_id.orNull
    val deployment_id_filtered = deployment_id.orNull
    val cluster_id_filtered = cluster_id.orNull
    val active_filtered = active.getOrElse(false)
    val tr = time_range.orNull

    val query = s"SELECT ${CLUSTER_MAP_COLS.CUSTOMER_ID}, ${CLUSTER_MAP_COLS.DEPLOYMENT_ID}, ${CLUSTER_MAP_COLS.CLUSTER_ID}, ${CLUSTER_MAP_COLS.NAME}, " +
      s"${CLUSTER_MAP_COLS.DESCRIPTION}, ${CLUSTER_MAP_COLS.ROLE}, ${CLUSTER_MAP_COLS.ACTIVE}, ${CLUSTER_MAP_COLS.REPLICATED}, ${CLUSTER_MAP_COLS.TS} FROM ${CLUSTER_MAP_COLS.TABLE_NAME}"
    val resultSet = cassdb.executeQuery(query)
    val cluster_map = {
      if (resultSet != null) {
        val cluster_map = resultSet.asScala
          .filter(tr == null || _.getTimestamp(CLUSTER_MAP_COLS.TS).getTime <= tr.end)
          .filter(cust_id_filtered == _.getString(CLUSTER_MAP_COLS.CUSTOMER_ID) || cust_id_filtered == null)
          .filter(deployment_id_filtered == _.getString(CLUSTER_MAP_COLS.DEPLOYMENT_ID) || deployment_id_filtered == null)
          .filter(cluster_id_filtered == _.getString(CLUSTER_MAP_COLS.CLUSTER_ID) || cluster_id_filtered == null)
          .groupBy(row => {
            val customer_id = row.getString(CLUSTER_MAP_COLS.CUSTOMER_ID)
            val deployment_id = row.getString(CLUSTER_MAP_COLS.DEPLOYMENT_ID)
            val cluster_id = row.getString(CLUSTER_MAP_COLS.CLUSTER_ID)
            s"${customer_id}::${deployment_id}::${cluster_id}"
          })
          .mapValues(rows => {
            rows.head
          })
          .values
          .filter(active_filtered && _.getBool(CLUSTER_MAP_COLS.ACTIVE) || !active_filtered)
          .map(row => {
            new ClusterMap(row.getString(CLUSTER_MAP_COLS.CUSTOMER_ID), row.getString(CLUSTER_MAP_COLS.DEPLOYMENT_ID), row.getString(CLUSTER_MAP_COLS.CLUSTER_ID),
              row.getString(CLUSTER_MAP_COLS.NAME), row.getString(CLUSTER_MAP_COLS.DESCRIPTION), row.getString(CLUSTER_MAP_COLS.ROLE),
              row.getBool(CLUSTER_MAP_COLS.ACTIVE), row.getBool(CLUSTER_MAP_COLS.REPLICATED), row.getTimestamp(CLUSTER_MAP_COLS.TS).getTime)
          }).toList
        cluster_map
      } else {
        List()
      }
    }
    cassdb.close()
    cluster_map
  }

  def setMap(item: ClusterMap): Boolean = {
    val cassdb = getCassandraHandle
    val ts = Calendar.getInstance().getTimeInMillis
    val query = s"INSERT INTO ${CLUSTER_MAP_COLS.TABLE_NAME}(${CLUSTER_MAP_COLS.CUSTOMER_ID}, ${CLUSTER_MAP_COLS.DEPLOYMENT_ID}, ${CLUSTER_MAP_COLS.CLUSTER_ID}, ${CLUSTER_MAP_COLS.NAME}, " +
      s"${CLUSTER_MAP_COLS.DESCRIPTION}, ${CLUSTER_MAP_COLS.ROLE}, ${CLUSTER_MAP_COLS.ACTIVE},${CLUSTER_MAP_COLS.REPLICATED}, ${CLUSTER_MAP_COLS.TS}) " +
      s"VALUES ('${item.customer_id}, ${item.deployment_id}', '${item.cluster_id}', '${item.name}', '${item.description}', " +
      s" ${item.role}, ${item.active}, ${item.replicated}, ${ts}) "
    val resultSet = cassdb.executeQuery(query)
    cassdb.close()
    true
  }


  def getTimelineMarkers(customer_id: String, deployment_id: String, cluster_id: String, time_range: TimeRange): ClusterTimelineMarker = {
    val clusterMap = Cluster.getMaps(Some(customer_id), Some(deployment_id), Some(cluster_id), Some(true), Some(time_range))
    if (clusterMap.length == 1) {
      val hour_marker_list = {
        val cassdb = getCassandraHandle
        val ssh = new SummaryStatsHourly(customer_id, deployment_id)
        val query = ssh.prepareTimelineQuery(cluster_id, "*", time_range)
        val resultSet = cassdb.executeQuery(query)
        val markerList: List[Marker] = {
          if (resultSet != null) {
            resultSet.asScala.map(row => {
              val start: Long = row.getTimestamp(ssh.SSHC.TS).getTime
              val end: Long = start + 3600 * 1000
              new Marker(start, end, row.getInt(ssh.SSHC.THERMAL))
            }).toList
          } else {
            List()
          }
        }.sortWith(_.start < _.start)
        cassdb.close()
        markerList
      }

      val minute_marker_list = {
        val cassdb = getCassandraHandle
        val ssh = new SummaryStatsMinute(customer_id, deployment_id)
        val query = ssh.prepareTimelineQuery(cluster_id, "*", time_range)
        val resultSet = cassdb.executeQuery(query)
        val markerList: List[Marker] = {
          if (resultSet != null) {
            resultSet.asScala.map(row => {
              val start: Long = row.getTimestamp(ssh.SSMC.TS).getTime
              val end: Long = start + 60 * 1000
              new Marker(start, end, row.getInt(ssh.SSMC.THERMAL))
            }).toList
          } else {
            List()
          }
        }.sortWith(_.start < _.start)
        cassdb.close()
        markerList
      }

      new ClusterTimelineMarker("get_cluster_timeline_markers", clusterMap.head.customer_id,
        clusterMap.head.deployment_id, clusterMap.head.cluster_id,
        clusterMap.head.name, clusterMap.head.description, clusterMap.head.active,
        hour_marker_list ::: minute_marker_list, time_range)

    } else {
      throw new Exception(s"Customer_id = ${customer_id} with deployment_id = ${deployment_id}  with cluster_id = ${cluster_id} doesnt exist in cluster_map")
      null
    }

  }

  case class ClusterDetailWithoutNodeDetails(cluster_id: String,
                                             name: String,
                                             description: String,
                                             role: String,
                                             active: Boolean,
                                             replicated: Boolean,
                                             thermal: Int, thermal_count: Map[Int, Int],
                                             node_count: Int) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class ClusterDetail(method: String, customer_id: String, deployment_id: String, cluster_id: String,
                           name: String,
                           description: String,
                           role: String,
                           active: Boolean,
                           replicated: Boolean,
                           thermal: Int, thermal_count: Map[Int, Int],
                           nodeDetailsList: List[Node.NodeDetailWithoutTimeRange],
                           time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class ClusterSnapshot(method: String, customer_id: String, deployment_id: String, cluster_id: String,
                             name: String,
                             description: String, active: Boolean,
                             thermal: Int, thermal_count: Map[Int, Int],
                             stats: List[SummaryState.Stats],
                             thermal_summary: List[Anomaly.HeatMapDetailWithScore],
                             thermal_reason: Anomaly.QuantizedSeverity,
                             time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class ClusterConnection(method: String, customer_id: String, deployment_id: String, cluster_id: String,
                               name: String,
                               description: String, active: Boolean,
                               connections: List[NWObject.NWGraph],
                               time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class ClusterService(method: String, customer_id: String, deployment_id: String, cluster_id: String,
                               name: String,
                               description: String, active: Boolean,
                               services : List[NWObject.ServiceInfo],
                               time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class ClusterTimelineMarker(method: String, customer_id: String, deployment_id: String, cluster_id: String, name: String,
                                   description: String, active: Boolean, markers: List[Marker],
                                   time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }


  def getDetails(customer_id: String, deployment_id: String, cluster_id: String, time_range: TimeRange): List[ClusterDetail] = {
    val deploymentMap = Deployment.getMaps(Some(customer_id), Some(deployment_id), None, Some(time_range))
    var cldList = List[ClusterDetail]()
    deploymentMap.foreach(dm => {
      val clusterMap = Cluster.getMaps(Some(dm.customer_id), Some(dm.deployment_id), None, Some(true), Some(time_range))
      cldList = clusterMap.map(cm => {
        val thermal_structs_hourly: List[(Long, Int, String)] = {
          val cassdb = getCassandraHandle
          val ssh = new SummaryStatsHourly(customer_id, deployment_id)
          val query = ssh.prepareDetailsQuery(cluster_id, "*", time_range)
          val resultSet = cassdb.executeQuery(query)
          val thermalStructs: Array[(Long, Int, String)] = {
            if (resultSet != null) {
              resultSet.asScala.map(row => {
                (row.getTimestamp(ssh.SSHC.TS).getTime, row.getInt(ssh.SSHC.THERMAL), row.getString(ssh.SSHC.THERMAL_STATS))
              }).toArray
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
          val query = ssh.prepareDetailsQuery(cluster_id, "*", time_range)
          val resultSet = cassdb.executeQuery(query)
          val thermalStructs: Array[(Long, Int, String)] = {
            if (resultSet != null) {
              resultSet.asScala.map(row => {
                (row.getTimestamp(ssh.SSMC.TS).getTime, row.getInt(ssh.SSMC.THERMAL), row.getString(ssh.SSMC.THERMAL_STATS))
              }).toArray
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
          val qtcounts = getThermalStats(thermalStructs, cm.replicated)

          val nodeMap = Node.getMaps(Some(cm.customer_id), Some(cm.deployment_id), Some(cm.cluster_id),
            None, Some(true), Some(time_range))
          var ndList = List[Node.NodeDetailWithoutTimeRange]()
          nodeMap.foreach(nm => {
            val nd = Node.getDetails(nm.customer_id, nm.deployment_id, nm.cluster_id, nm.host_ip, time_range)
            if (nd.nonEmpty) {
              ndList ::= {
                val x = nd.head
                new NodeDetailWithoutTimeRange(x.host_name, x.host_ip, x.scope, x.active, x.thermal, x.thermal_count)
              }
            }
          })
          new ClusterDetail("get_cluster_detail", cm.customer_id, cm.deployment_id, cm.cluster_id,
            cm.name, cm.description, cm.role, cm.active, cm.replicated, qtcounts._1, qtcounts._2, ndList, time_range)
        } else {
          null
        }
      }).filterNot(_ == null)
    })
    cldList
  }

  def getSnapshot(customer_id: String, deployment_id: String, cluster_id: String, time_range: TimeRange): List[ClusterSnapshot] = {
    val deploymentMap = Deployment.getMaps(Some(customer_id), Some(deployment_id), None, Some(time_range))
    var clsList = List[ClusterSnapshot]()
    deploymentMap.foreach(dm => {
      val clusterMap = Cluster.getMaps(Some(dm.customer_id), Some(dm.deployment_id), None, Some(true), Some(time_range))
      clsList = clusterMap.map(cm => {
        val thermal_structs_hourly: List[(Long, Int, String, String, String, String)] = {
          val cassdb = getCassandraHandle
          val ssh = new SummaryStatsHourly(customer_id, deployment_id)
          val query = ssh.prepareSnapshotQuery(cluster_id, "*", time_range)
          val resultSet = cassdb.executeQuery(query)
          val thermalStructs: Array[(Long, Int, String, String, String, String)] = {
            if (resultSet != null) {
              resultSet.asScala.map(row => {
                (row.getTimestamp(ssh.SSHC.TS).getTime, row.getInt(ssh.SSHC.THERMAL), row.getString(ssh.SSHC.THERMAL_STATS),
                  row.getString(ssh.SSHC.STATS), row.getString(ssh.SSHC.THERMAL_SUMMARY),
                  row.getString(ssh.SSHC.THERMAL_REASON))
              }).toArray
            } else {
              Array()
            }
          }
          cassdb.close()
          thermalStructs.toList
        }

        val thermal_structs_minute: List[(Long, Int, String, String, String, String)] = {
          val cassdb = getCassandraHandle
          val ssh = new SummaryStatsMinute(customer_id, deployment_id)
          val query = ssh.prepareSnapshotQuery(cluster_id, "*", time_range)
          val resultSet = cassdb.executeQuery(query)
          val thermalStructs: Array[(Long, Int, String, String, String, String)] = {
            if (resultSet != null) {
              resultSet.asScala.map(row => {
                (row.getTimestamp(ssh.SSMC.TS).getTime, row.getInt(ssh.SSMC.THERMAL), row.getString(ssh.SSMC.THERMAL_STATS),
                  row.getString(ssh.SSMC.STATS), row.getString(ssh.SSMC.THERMAL_SUMMARY),
                  row.getString(ssh.SSMC.THERMAL_REASON))
              }).toArray
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
          thermalStructs.map(_._4).foreach(x => {
            val stats: List[SummaryState.Stats] = JacksonWrapper.deserialize[List[SummaryState.Stats]](x)
            if (stats.nonEmpty) {
              statsList :::= stats
            }
          })

          val thermal_summary = JacksonWrapper.deserialize[List[Anomaly.HeatMapDetailWithScore]](thermalStructs.head._5)
          val thermal_reason = JacksonWrapper.deserialize[Anomaly.QuantizedSeverity](thermalStructs.head._6)

          val qtcounts = getThermalStats(thermalStructs.map(x => {
            (x._1, x._2, x._3)
          }), cm.replicated)

          new ClusterSnapshot("get_cluster_snapshot", cm.customer_id, cm.deployment_id, cm.cluster_id, cm.name, cm.description, cm.active,
            qtcounts._1, qtcounts._2, statsList.sortWith(_.contribution.rank < _.contribution.rank),
            thermal_summary, thermal_reason, time_range)
        } else {
          null
        }
      }).filterNot(_ == null)
    })
    clsList
  }

  def getConnection(customer_id: String, deployment_id: String, cluster_id: String, time_range: TimeRange): List[ClusterConnection] = {
    val deploymentMap = Deployment.getMaps(Some(customer_id), Some(deployment_id), None, Some(time_range))
    var clsList = List[ClusterConnection]()
    deploymentMap.foreach(dm => {
      val clusterMap = Cluster.getMaps(Some(dm.customer_id), Some(dm.deployment_id), None, Some(true), Some(time_range))
      clsList = clusterMap.map(cm => {
        val connections_structs_hourly: List[(Long, String)] = {
          val cassdb = getCassandraHandle
          val ssh = new SummaryStatsHourly(customer_id, deployment_id)
          val query = ssh.prepareConnectionQuery(cluster_id, "*", time_range)
          val resultSet = cassdb.executeQuery(query)
          val connectionStructs: Array[(Long, String)] = {
            if (resultSet != null) {
              resultSet.asScala.map(row => {
                (row.getTimestamp(ssh.SSHC.TS).getTime, row.getString(ssh.SSHC.CONNECTIONS))
              }).toArray
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
          val query = ssh.prepareConnectionQuery(cluster_id, "*", time_range)
          val resultSet = cassdb.executeQuery(query)
          val connectionStructs: Array[(Long, String)] = {
            if (resultSet != null) {
              resultSet.asScala.map(row => {
                (row.getTimestamp(ssh.SSMC.TS).getTime, row.getString(ssh.SSMC.CONNECTIONS))
              }).toArray
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
          var connectionList = List[NWObject.NWGraph]()
          connectionStructs.map(_._2).foreach(x => {
            val connections: List[NWObject.NWGraph] = JacksonWrapper.deserialize[List[NWObject.NWGraph]](x)
            if (connections.nonEmpty) {
              connectionList :::= connections
            }
          })

          new ClusterConnection("get_cluster_connection", cm.customer_id, cm.deployment_id, cm.cluster_id, cm.name, cm.description, cm.active,
            connectionList, time_range)
        } else {
          null
        }
      }).filterNot(_ == null)
    })
    clsList
  }

  def getService(customer_id: String, deployment_id: String, cluster_id: String, time_range: TimeRange): List[ClusterService] = {
    val deploymentMap = Deployment.getMaps(Some(customer_id), Some(deployment_id), None, Some(time_range))
    var clsList = List[ClusterService]()
    deploymentMap.foreach(dm => {
      val clusterMap = Cluster.getMaps(Some(dm.customer_id), Some(dm.deployment_id), None, Some(true), Some(time_range))
      clsList = clusterMap.map(cm => {
        val services_structs_hourly: List[(Long, String)] = {
          val cassdb = getCassandraHandle
          val ssh = new SummaryStatsHourly(customer_id, deployment_id)
          val query = ssh.prepareServiceQuery(cluster_id, "*", time_range)
          val resultSet = cassdb.executeQuery(query)
          val serviceStructs: Array[(Long, String)] = {
            if (resultSet != null) {
              resultSet.asScala.map(row => {
                (row.getTimestamp(ssh.SSHC.TS).getTime, row.getString(ssh.SSHC.SERVICES))
              }).toArray
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
          val query = ssh.prepareServiceQuery(cluster_id, "*", time_range)
          val resultSet = cassdb.executeQuery(query)
          val serviceStructs: Array[(Long, String)] = {
            if (resultSet != null) {
              resultSet.asScala.map(row => {
                (row.getTimestamp(ssh.SSMC.TS).getTime, row.getString(ssh.SSMC.SERVICES))
              }).toArray
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
          var serviceList = List[NWObject.ServiceInfo]()
          serviceStructs.map(_._2).foreach(x => {
            val services: List[NWObject.ServiceInfo] = JacksonWrapper.deserialize[List[NWObject.ServiceInfo]](x)
            if (services.nonEmpty) {
              serviceList :::= services
            }
          })

          new ClusterService("get_cluster_service", cm.customer_id, cm.deployment_id, cm.cluster_id, cm.name, cm.description, cm.active,
            serviceList, time_range)
        } else {
          null
        }
      }).filterNot(_ == null)
    })
    clsList
  }
}
