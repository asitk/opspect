package com.infrared.entry.summarystats

import java.util.Calendar

import com.infrared.entry.Metrics
import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.util.hashing.{MurmurHash3 => MH3}

/**
 * Created by prashun on 15/8/16.
 */
class SummaryStatsHourly(_customerid: String, _deploymentid: String) extends SummarizedStats {
  val grainInMinutes: Int = 60
  //minutes
  var customer_id: String = _customerid
  var deployment_id: String = _deploymentid

  def getTimeSlots: List[Long] = {
    var ts1 = getTimeToNearestHour(getLastTimeSlot)

    val timeNow = getTimeToNearestHour(Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).getTimeInMillis)

    if (ts1 == 0L) {
      ts1 = timeNow - 14 * 24 * 3600 * 1000 //14 days behind
    }
    val tsList = ts1.to(timeNow).by(3600 * 1000).toList
    tsList
  }

  object SSHC {
    val TABLE_NAME = "summary_stats_hourly"
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

  def getLastTimeSlot: Long = {
    val query = s"SELECT ${SSHC.TS} FROM ${SSHC.TABLE_NAME} WHERE ${SSHC.CUSTOMER_ID} = '${customer_id}' " +
      s"AND ${SSHC.DEPLOYMENT_ID} = '${deployment_id}'  LIMIT 1"
    val cassdb = getCassandraHandle
    val resultSet = cassdb.executeQuery(query)

    var ts = 0l
    try {
      resultSet.asScala.map(_.getTimestamp(SSHC.TS).getTime).toList.head + 3600 * 1000
    } catch {
      case e: Exception => {
        try {
          val query = Metrics.getLastTimestampQuery(customer_id, deployment_id)
          val resultSet = cassdb.executeQuery(query)
          ts = resultSet.asScala.map(_.getTimestamp(Metrics.METRICS_STATS_COLS.TS).getTime).toList.head
        } catch {
          case e: Exception => {
            Log.getLogger.error(s"${Metrics.METRICS_STATS_COLS.TABLE_NAME} is empty")
            ts = 0L
          }
        }
        cassdb.close()
      }
    }
    cassdb.close()
    ts
  }

  def updateTimeSlot(ssList: SummaryStats): Boolean = {
    val uhash = s"${ssList.customer_id},${ssList.deployment_id},${ssList.cluster_id},${ssList.host_ip},${ssList.host_name}"
    val query = s"INSERT INTO ${SSHC.TABLE_NAME}(${SSHC.TS}, ${SSHC.UHASH}, ${SSHC.MIN_OF_HOUR}, ${SSHC.CUSTOMER_ID}, " +
      s"${SSHC.DEPLOYMENT_ID}, ${SSHC.CLUSTER_ID}, ${SSHC.HOST_IP}, " +
      s"${SSHC.HOST_NAME}, ${SSHC.THERMAL},${SSHC.THERMAL_STATS}, " +
      s"${SSHC.THERMAL_REASON}, ${SSHC.THERMAL_SUMMARY}, ${SSHC.STATS}, ${SSHC.CONNECTIONS}, ${SSHC.SERVICES})" +
      s"VALUES(${ssList.ts}, '${uhash}', ${ssList.moh}, '${ssList.customer_id}', '${ssList.deployment_id}', '${ssList.cluster_id}'," +
      s"'${ssList.host_ip}', '${ssList.host_name}', ${ssList.thermal}, '${ssList.thermal_stats}', " +
      s"'${ssList.thermal_reason}', '${ssList.thermal_summary}', '${ssList.stats}'," +
      s"'${ssList.connections}', '${ssList.services}')"
    val cassdb = getCassandraHandle
    val resultSet = cassdb.executeQuery(query)
    cassdb.close()
    true
  }

  def prepareTimelineQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 60)
    val query = s"SELECT ${SSHC.TS}, ${SSHC.THERMAL} FROM ${SSHC.TABLE_NAME} WHERE ${SSHC.CUSTOMER_ID} = '${customer_id}' " +
      s"AND ${SSHC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSHC.CLUSTER_ID} = '${cluster_id}' AND ${SSHC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSHC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }

  def prepareDetailsQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 60)
    val query = s"SELECT ${SSHC.TS}, ${SSHC.THERMAL}, ${SSHC.THERMAL_STATS} FROM ${SSHC.TABLE_NAME} " +
      s"WHERE ${SSHC.CUSTOMER_ID} = '${customer_id}' AND ${SSHC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSHC.CLUSTER_ID} = '${cluster_id}' AND ${SSHC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSHC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }

  def prepareSnapshotQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 60)
    val query = s"SELECT ${SSHC.TS}, ${SSHC.THERMAL}, ${SSHC.THERMAL_STATS}, ${SSHC.STATS}, " +
      s"${SSHC.THERMAL_SUMMARY}, ${SSHC.THERMAL_REASON} FROM ${SSHC.TABLE_NAME} " +
      s"WHERE ${SSHC.CUSTOMER_ID} = '${customer_id}' AND ${SSHC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSHC.CLUSTER_ID} = '${cluster_id}' AND ${SSHC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSHC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }

  def prepareConnectionQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 60)
    val query = s"SELECT ${SSHC.TS}, ${SSHC.CONNECTIONS} FROM ${SSHC.TABLE_NAME} " +
      s"WHERE ${SSHC.CUSTOMER_ID} = '${customer_id}' AND ${SSHC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSHC.CLUSTER_ID} = '${cluster_id}' AND ${SSHC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSHC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }

  def prepareServiceQuery(cluster_id: String, host_name: String, time_range: TimeRange): String = {
    val tsSet = getTimeRangeAsASetString(time_range, 60)
    val query = s"SELECT ${SSHC.TS}, ${SSHC.SERVICES} FROM ${SSHC.TABLE_NAME} " +
      s"WHERE ${SSHC.CUSTOMER_ID} = '${customer_id}' AND ${SSHC.DEPLOYMENT_ID} = '${deployment_id}' " +
      s"AND ${SSHC.CLUSTER_ID} = '${cluster_id}' AND ${SSHC.HOST_NAME} = '${host_name}' " +
      s"AND ${SSHC.TS} IN (${tsSet}) ALLOW FILTERING"
    query
  }
}

