package com.infrared.entry.infra

import java.util.Calendar

import com.infrared.entry.anomaly.Anomaly
import com.infrared.entry.infra.Cluster.ClusterDetailWithoutNodeDetails
import com.infrared.entry.summarystats._
import com.infrared.entry.{NWObject, SummaryState}
import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.util.hashing.{MurmurHash3 => MH3}

/**
 * Created by prashun on 7/6/16.
 */
object Deployment extends Group {

  case class DeploymentMap(customer_id: String, deployment_id: String, name: String, description: String, active: Boolean, last_modified: Long) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  object DEPLOYMENT_MAP_COLS {
    val TABLE_NAME = "deployment_map"
    val CUSTOMER_ID = "customer_id"
    val DEPLOYMENT_ID = "deployment_id"
    val NAME = "name"
    val DESCRIPTION = "description"
    val ACTIVE = "active"
    val TS = "ts"
  }

  def getMaps(customer_id: Option[String], deployment_id: Option[String], active: Option[Boolean], time_range: Option[TimeRange]): List[DeploymentMap] = {
    val cassdb = getCassandraHandle
    val active_filtered = active.getOrElse(false)
    val cust_id_filtered = customer_id.orNull
    val deployment_id_filtered = deployment_id.orNull
    val tr = time_range.orNull

    val query = s"SELECT ${DEPLOYMENT_MAP_COLS.CUSTOMER_ID}, ${DEPLOYMENT_MAP_COLS.DEPLOYMENT_ID}, ${DEPLOYMENT_MAP_COLS.NAME}, ${DEPLOYMENT_MAP_COLS.DESCRIPTION}, ${DEPLOYMENT_MAP_COLS.ACTIVE}, ${DEPLOYMENT_MAP_COLS.TS} FROM ${DEPLOYMENT_MAP_COLS.TABLE_NAME}"
    val resultSet = cassdb.executeQuery(query)
    val deployment_map = {
      if (resultSet != null) {
        val deployment_map = resultSet.asScala
          .filter(tr == null || _.getTimestamp(DEPLOYMENT_MAP_COLS.TS).getTime <= tr.end)
          .filter(cust_id_filtered == _.getString(DEPLOYMENT_MAP_COLS.CUSTOMER_ID) || cust_id_filtered == null)
          .filter(deployment_id_filtered == _.getString(DEPLOYMENT_MAP_COLS.DEPLOYMENT_ID) || deployment_id_filtered == null)
          .groupBy(row => {
            val customer_id = row.getString(DEPLOYMENT_MAP_COLS.CUSTOMER_ID)
            val deployment_id = row.getString(DEPLOYMENT_MAP_COLS.DEPLOYMENT_ID)
            s"${customer_id}::${deployment_id}"
          })
          .mapValues(rows => {
            rows.head
          }).values
          .filter(active_filtered && _.getBool(DEPLOYMENT_MAP_COLS.ACTIVE) || !active_filtered)
          .map(row => {
            new DeploymentMap(row.getString(DEPLOYMENT_MAP_COLS.CUSTOMER_ID), row.getString(DEPLOYMENT_MAP_COLS.DEPLOYMENT_ID),
              row.getString(DEPLOYMENT_MAP_COLS.NAME), row.getString(DEPLOYMENT_MAP_COLS.DESCRIPTION), row.getBool(DEPLOYMENT_MAP_COLS.ACTIVE),
              row.getTimestamp(DEPLOYMENT_MAP_COLS.TS).getTime)
          }).toList
        deployment_map
      } else {
        List()
      }
    }
    cassdb.close()
    deployment_map
  }

  def setMap(item: DeploymentMap): Boolean = {
    val cassdb = getCassandraHandle
    val ts = Calendar.getInstance().getTimeInMillis
    val query = s"INSERT INTO ${DEPLOYMENT_MAP_COLS.TABLE_NAME}(${DEPLOYMENT_MAP_COLS.CUSTOMER_ID}, ${DEPLOYMENT_MAP_COLS.DEPLOYMENT_ID}, ${DEPLOYMENT_MAP_COLS.NAME}, ${DEPLOYMENT_MAP_COLS.DESCRIPTION}, ${DEPLOYMENT_MAP_COLS.ACTIVE}, ${DEPLOYMENT_MAP_COLS.TS}) " +
      s"VALUES ('${item.customer_id}, ${item.deployment_id}', '${item.name}', '${item.description}', ${item.active}, ${ts}) "
    val resultSet = cassdb.executeQuery(query)
    cassdb.close()
    true
  }


  case class DeploymentDetail(method: String, customer_id: String, deployment_id: String, name: String,
                              description: String, active: Boolean,
                              thermal: Int, thermal_count: Map[Int, Int],
                              clusterDetailsList: List[ClusterDetailWithoutNodeDetails], time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class DeploymentSnapshot(method: String, customer_id: String, deployment_id: String, name: String,
                                description: String, active: Boolean,
                                thermal: Int, thermal_count: Map[Int, Int], stats: List[SummaryState.Stats],
                                thermal_summary: List[Anomaly.HeatMapDetailWithScore],
                                thermal_reason: Anomaly.QuantizedSeverity,
                                time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class DeploymentConnection(method: String, customer_id: String, deployment_id: String, name: String,
                                  description: String, active: Boolean, connections: List[NWObject.NWGraph],
                                  time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class DeploymentService(method: String, customer_id: String, deployment_id: String, name: String,
                               description: String, active: Boolean, services: List[NWObject.ServiceInfo],
                                  time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class DeploymentTimelineMarker(method: String, customer_id: String, deployment_id: String, name: String,
                                      description: String, active: Boolean, markers: List[Marker],
                                      time_range: TimeRange) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }


  def getDetails(customer_id: String, deployment_id: String, time_range: TimeRange): List[DeploymentDetail] = {
    val deploymentMap = Deployment.getMaps(Some(customer_id), Some(deployment_id), None, Some(time_range))
    var cluster_replicated: Boolean = true
    val ddList = deploymentMap.map(dm => {
      val thermal_structs_hourly: List[(Long, Int, String)] = {
        val cassdb = getCassandraHandle
        val ssh = new SummaryStatsHourly(customer_id, deployment_id)
        val query = ssh.prepareDetailsQuery("*", "*", time_range)
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
        val query = ssh.prepareDetailsQuery("*", "*", time_range)
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
      var cldList = List[ClusterDetailWithoutNodeDetails]()
      val clusterMap = Cluster.getMaps(Some(dm.customer_id), Some(dm.deployment_id), None, Some(true), Some(time_range))
      clusterMap.foreach(cm => {
        cluster_replicated &= cm.replicated
        val cld = Cluster.getDetails(cm.customer_id, cm.deployment_id, cm.cluster_id, time_range)
        if (cld.nonEmpty) {

          cldList ::= {
            val x = cld.head
            new ClusterDetailWithoutNodeDetails(x.cluster_id, x.name, x.description, x.role,
              x.active, x.replicated, x.thermal, x.thermal_count, x.nodeDetailsList.length)
          }
        }
      })

      val qtcounts = getThermalStats(thermalStructs, cluster_replicated)
      new DeploymentDetail("get_deployment_detail", dm.customer_id, dm.deployment_id, dm.name, dm.description, dm.active,
        qtcounts._1, qtcounts._2, cldList, time_range)
      } else {
        null
      }
    }).filterNot(_ == null)

    ddList
  }

  def getSnapshot(customer_id: String, deployment_id: String, time_range: TimeRange): List[DeploymentSnapshot] = {
    val deploymentMap = Deployment.getMaps(Some(customer_id), Some(deployment_id), None, Some(time_range))
    var cluster_replicated: Boolean = true
    val dsList = deploymentMap.map(dm => {
      val thermal_structs_hourly: List[(Long, Int, String, String, String, String)] = {
        val cassdb = getCassandraHandle
        val ssh = new SummaryStatsHourly(customer_id, deployment_id)
        val query = ssh.prepareSnapshotQuery("*", "*", time_range)
        val resultSet = cassdb.executeQuery(query)
        val thermalStructs: Array[(Long, Int, String, String, String, String)] = {
          if (resultSet != null) {
            resultSet.asScala.map(row => {
              (row.getTimestamp(ssh.SSHC.TS).getTime, row.getInt(ssh.SSHC.THERMAL),
                row.getString(ssh.SSHC.THERMAL_STATS), row.getString(ssh.SSHC.STATS),
                row.getString(ssh.SSHC.THERMAL_SUMMARY), row.getString(ssh.SSHC.THERMAL_REASON))
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
        val query = ssh.prepareSnapshotQuery("*", "*", time_range)
        val resultSet = cassdb.executeQuery(query)
        val thermalStructs: Array[(Long, Int, String, String, String, String)] = {
          if (resultSet != null) {
            resultSet.asScala.map(row => {
              (row.getTimestamp(ssh.SSMC.TS).getTime, row.getInt(ssh.SSMC.THERMAL),
                row.getString(ssh.SSMC.THERMAL_STATS), row.getString(ssh.SSMC.STATS),
                row.getString(ssh.SSMC.THERMAL_SUMMARY), row.getString(ssh.SSMC.THERMAL_REASON))
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
        }), cluster_replicated)

        new DeploymentSnapshot("get_deployment_snapshot", dm.customer_id, dm.deployment_id, dm.name, dm.description, dm.active,
          qtcounts._1, qtcounts._2, statsList.sortWith(_.contribution.rank < _.contribution.rank),
          thermal_summary, thermal_reason, time_range)
      } else {
        null
      }
    }).filterNot(_ == null)

    dsList
  }

  def getConnection(customer_id: String, deployment_id: String, time_range: TimeRange): List[DeploymentConnection] = {
    val deploymentMap = Deployment.getMaps(Some(customer_id), Some(deployment_id), None, Some(time_range))
    var cluster_replicated: Boolean = true
    val dcList = deploymentMap.map(dm => {
      val connections_structs_hourly: List[(Long, String)] = {
        val cassdb = getCassandraHandle
        val ssh = new SummaryStatsHourly(customer_id, deployment_id)
        val query = ssh.prepareConnectionQuery("*", "*", time_range)
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

      val connection_structs_minute: List[(Long, String)] = {
        val cassdb = getCassandraHandle
        val ssh = new SummaryStatsMinute(customer_id, deployment_id)
        val query = ssh.prepareConnectionQuery("*", "*", time_range)
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
        (connections_structs_hourly ::: connection_structs_minute).toArray
      }

      if (connectionStructs.nonEmpty) {
      var connectionList = List[NWObject.NWGraph]()
      connectionStructs.map(_._2).foreach(x => {
        val connections: List[NWObject.NWGraph] = JacksonWrapper.deserialize[List[NWObject.NWGraph]](x)
        if (connections.nonEmpty) {
          connectionList :::= connections
        }
      })

      new DeploymentConnection("get_deployment_connection", dm.customer_id, dm.deployment_id, dm.name, dm.description, dm.active,
        connectionList, time_range)
      } else {
        null
      }
    }).filterNot(_ == null)

    dcList
  }

  def getService(customer_id: String, deployment_id: String, time_range: TimeRange): List[DeploymentService] = {
    val deploymentMap = Deployment.getMaps(Some(customer_id), Some(deployment_id), None, Some(time_range))
    var cluster_replicated: Boolean = true
    val dsList = deploymentMap.map(dm => {
      val services_structs_hourly: List[(Long, String)] = {
        val cassdb = getCassandraHandle
        val ssh = new SummaryStatsHourly(customer_id, deployment_id)
        val query = ssh.prepareServiceQuery("*", "*", time_range)
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

      val service_structs_minute: List[(Long, String)] = {
        val cassdb = getCassandraHandle
        val ssh = new SummaryStatsMinute(customer_id, deployment_id)
        val query = ssh.prepareServiceQuery("*", "*", time_range)
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
        (services_structs_hourly ::: service_structs_minute).toArray
      }

      if (serviceStructs.nonEmpty) {
        var serviceList = List[NWObject.ServiceInfo]()
        serviceStructs.map(_._2).foreach(x => {
          val services: List[NWObject.ServiceInfo] = JacksonWrapper.deserialize[List[NWObject.ServiceInfo]](x)
          if (services.nonEmpty) {
            serviceList :::= services
          }
        })

        new DeploymentService("get_deployment_service", dm.customer_id, dm.deployment_id, dm.name, dm.description, dm.active,
          serviceList, time_range)
      } else {
        null
      }
    }).filterNot(_ == null)

    dsList
  }

  def getTimelineMarkers(customer_id: String, deployment_id: String, time_range: TimeRange): DeploymentTimelineMarker = {
    val dm = Deployment.getMaps(Some(customer_id), Some(deployment_id), None, Some(time_range))
    if (dm.length == 1) {
      val hour_marker_list = {
        val ssh = new SummaryStatsHourly(customer_id, deployment_id)
        val query = ssh.prepareTimelineQuery("*", "*", time_range)
        val cassdb = getCassandraHandle
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
        val ssh = new SummaryStatsMinute(customer_id, deployment_id)
        val query = ssh.prepareTimelineQuery("*", "*", time_range)
        val cassdb = getCassandraHandle
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

      new DeploymentTimelineMarker("get_deployment_timeline_markers", dm.head.customer_id,
        dm.head.deployment_id, dm.head.name, dm.head.description, dm.head.active, hour_marker_list ::: minute_marker_list, time_range)
    } else {
      throw new Exception(s"Customer_id = ${customer_id} with deployment_id = ${deployment_id} doesnt exist in deployment_map")
      null
    }
  }

}
