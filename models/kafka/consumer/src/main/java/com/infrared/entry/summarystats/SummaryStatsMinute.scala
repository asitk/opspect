package com.infrared.entry.summarystats

/**
 * Created by prashun on 15/8/16.
 */

import java.util.Calendar

import com.infrared.entry.Metrics._
import com.infrared.entry.anomaly.Detections
import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.util.hashing.{MurmurHash3 => MH3}

/**
 * Created by prashun on 15/8/16.
 */
class SummaryStatsMinute(_customerid: String, _deploymentid: String) extends SummarizedStats {
  val grain: Int = 1
  //minutes
  var customer_id: String = _customerid
  var deployment_id: String = _deploymentid

  def getTimeSlots: List[Long] = {

    val timeNow = {
      val timestamp = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).getTimeInMillis
      val tsInSeconds: Long = timestamp / 1000
      val tsInMinutes: Long = tsInSeconds / 60
      val tsInMillis: Long = tsInMinutes * 60 * 1000
      tsInMillis
    }
    var last = getLastTimeSlot
    if (getTimeToNearestHour(last) != getTimeToNearestHour(timeNow)) {
      last = getTimeToNearestHour(timeNow)
    }

    val tsList = last.to(timeNow).by(60 * 1000).toList
    tsList
  }

  object SSMC {
    val TABLE_NAME = "summary_stats_minute"
    val CUSTOMER_ID = "customer_id"
    val DEPLOYMENT_ID = "deployment_id"
    val CLUSTER_ID = "cluster_id"
    val HOST_IP = "host_ip"
    val HOST_NAME = "host_name"
    val TS = "ts"
    val UHASH = "uhash"
    val CONNECTIONS = "connections"
    val SERVICES = "services"
    val MIN_OF_HOUR = "min_of_hour"
    val STATS = "stats"
    val THERMAL = "thermal"
    val THERMAL_REASON = "thermal_reason"
    val THERMAL_STATS = "thermal_stats"
    val THERMAL_SUMMARY = "thermal_summary"
  }
  def updateNWSvcActivities(customer_id: String, deployment_id: String, time_range: TimeRange): Unit = {
    val query = {
      getNetworkMetricsQuery(customer_id, deployment_id, time_range, 1)
    }
    val ret = getMetricsQueryResult(query)
    if (!ret.isEmpty) {
      Detections.writeNetworkActivityByService(ret, false, time_range)
    }
  }

  def getLastTimeSlot: Long = {
    val cassdb = getCassandraHandle
    val ts = {
      try {
        val query = s"SELECT ${SSMC.TS} FROM ${SSMC.TABLE_NAME} WHERE ${SSMC.CUSTOMER_ID} = '${customer_id}' AND ${SSMC.DEPLOYMENT_ID} = '${deployment_id}'" +
          s"AND ${SSMC.MIN_OF_HOUR} IN (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, " +
          s"29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59);"
        val resultSet = cassdb.executeQuery(query)
        resultSet.asScala.map(_.getTimestamp("ts").getTime).toList.sortWith(_ > _).head
      } catch {
        case e: Exception => {
          Log.getLogger.error("summary_stats_minute is empty")
          0L
        }
      }
    }
    cassdb.close()
    ts
  }

  def updateTimeSlot(ssList: SummaryStats): Boolean = {
    val uhash = s"${ssList.customer_id},${ssList.deployment_id},${ssList.cluster_id},${ssList.host_ip},${ssList.host_name}"
    val query = s"INSERT INTO ${SSMC.TABLE_NAME}(${SSMC.TS}, ${SSMC.UHASH}, ${SSMC.MIN_OF_HOUR}, ${SSMC.CUSTOMER_ID}, " +
      s"${SSMC.DEPLOYMENT_ID}, ${SSMC.CLUSTER_ID}, ${SSMC.HOST_IP}, ${SSMC.HOST_NAME}, ${SSMC.THERMAL}, " +
      s"${SSMC.THERMAL_STATS}, ${SSMC.THERMAL_REASON}, ${SSMC.THERMAL_SUMMARY}, ${SSMC.STATS}, ${SSMC.CONNECTIONS}, ${SSMC.SERVICES})" +
      s"VALUES(${ssList.ts}, '${uhash}', ${ssList.moh}, '${ssList.customer_id}', '${ssList.deployment_id}', '${ssList.cluster_id}', '${ssList.host_ip}', '${ssList.host_name}'," +
      s"${ssList.thermal}, '${ssList.thermal_stats}', '${ssList.thermal_reason}','${ssList.thermal_summary}', '${ssList.stats}'," +
      s"'${ssList.connections}', '${ssList.services}') USING TTL 3600;"
    val cassdb = getCassandraHandle
    val resultSet = cassdb.executeQuery(query)
    cassdb.close()
    true
  }

  def prepareTimelineQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 1)
    val query = s"SELECT ${SSMC.TS}, ${SSMC.THERMAL} FROM ${SSMC.TABLE_NAME} WHERE " +
      s"${SSMC.CUSTOMER_ID} = '${customer_id}' AND ${SSMC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSMC.CLUSTER_ID} = '${cluster_id}' AND ${SSMC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSMC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }

  def prepareDetailsQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 1)
    val query = s"SELECT ${SSMC.TS}, ${SSMC.THERMAL}, ${SSMC.THERMAL_STATS} FROM ${SSMC.TABLE_NAME} " +
      s"WHERE ${SSMC.CUSTOMER_ID} = '${customer_id}' AND ${SSMC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSMC.CLUSTER_ID} = '${cluster_id}' AND ${SSMC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSMC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }

  def prepareSnapshotQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 1)
    val query = s"SELECT ${SSMC.TS}, ${SSMC.THERMAL}, ${SSMC.THERMAL_STATS}, " +
      s"${SSMC.STATS}, ${SSMC.THERMAL_SUMMARY}, ${SSMC.THERMAL_REASON} FROM ${SSMC.TABLE_NAME} " +
      s"WHERE ${SSMC.CUSTOMER_ID} = '${customer_id}' AND ${SSMC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSMC.CLUSTER_ID} = '${cluster_id}' AND ${SSMC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSMC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }

  def prepareConnectionQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 1)
    val query = s"SELECT ${SSMC.TS}, ${SSMC.CONNECTIONS} FROM ${SSMC.TABLE_NAME} " +
      s"WHERE ${SSMC.CUSTOMER_ID} = '${customer_id}' AND ${SSMC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSMC.CLUSTER_ID} = '${cluster_id}' AND ${SSMC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSMC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }

  def prepareServiceQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 1)
    val query = s"SELECT ${SSMC.TS}, ${SSMC.SERVICES} FROM ${SSMC.TABLE_NAME} " +
      s"WHERE ${SSMC.CUSTOMER_ID} = '${customer_id}' AND ${SSMC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSMC.CLUSTER_ID} = '${cluster_id}' AND ${SSMC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSMC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }
}
