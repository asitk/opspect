package com.opspect.entry

import java.util.Calendar

import com.opspect.util.CassandraDB._
import com.opspect.util._
import org.joda.time._

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/** Created by prashun on 9/6/16.
  */
object Metrics {

  private def getCassandraHandle: CassDB = {
    val addressList = new ListBuffer[CassandraDB.Address]()
    addressList += new Address("127.0.0.1", 9042)
    CassandraDB.getScalaInstance("nuvidata", addressList.toList)
  }

  def mkUhash(m: MetricsStats): String = {
    s"${m.ts},${m.cluster_id},${m.target},${m.host_ip},${m.plugin},${m.classification}"
  }

  case class MetricsStats(
      ts: Long,
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      target: String,
      host_ip: String,
      host_name: String,
      plugin: String,
      classification: String,
      high_water_mark: Double,
      final_score: Double,
      is_non_numeric: Boolean,
      val_stats: String,
      vel_stats: String,
      tags: String,
      val_str: String
  ) extends Serializable {

    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  case class DStats(
      size: Long,
      min_val: Double,
      max_val: Double,
      mean: Double,
      stddev: Double,
      f: Array[Int]
  ) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  def computeScore(fdVal: Array[Int], count: Long): Double = {
    val a = 4
    val b = 2
    val c = 1
    val sumScore = {
      (a * fdVal(0) + b * fdVal(1) + c * fdVal(2)
        + c * fdVal(3) + b * fdVal(4) + a * fdVal(5)) / count
    }
    sumScore
  }

  def isInRange(x: Double, a: Double, b: Double): Boolean = {
    (x >= a && x < b)
  }

  def getMonthYear(ts: Long): Int = {
    val cal = Calendar.getInstance()
    cal.setTimeInMillis(ts)
    val dt = new DateTime(cal.getTime)
    s"${dt.getMonthOfYear}${dt.getYear}".toInt
  }

  def getMinuteOfHour(ts: Long): Int = {
    val cal = Calendar.getInstance()
    cal.setTimeInMillis(ts)
    val dt = new DateTime(cal.getTime)
    s"${dt.getMinuteOfHour}".toInt
  }

  object METRICS_STATS_COLS {
    val TABLE_NAME = "metrics_stats"
    val TS = "ts"
    val UHASH = "uhash"
    val CUSTOMER_ID = "customer_id"
    val DEPLOYMENT_ID = "deployment_id"
    val CLUSTER_ID = "cluster_id"
    val TARGET = "target"
    val HOST_IP = "host_ip"
    val HOST_NAME = "host_name"
    val PLUGIN = "plugin"
    val CLASSIFICATION = "classification"
    val HIGH_WATER_MARK = "high_water_mark"
    val FINAL_SCORE = "final_score"
    val IS_NON_NUMERIC = "is_non_numeric"
    val VAL_STATS = "val_stats"
    val VEL_STATS = "vel_stats"
    val VAL_STR = "val_str"
    val TAGS = "tags"
  }

  def getMetricsQueryResult(query: String): Array[MetricsStats] = {
    val cassdb = getCassandraHandle

    val resultSet = cassdb.executeQuery(query)
    val result: Array[MetricsStats] = {
      if (resultSet != null) {
        val ret = resultSet.asScala
          .map(row => {
            val ts = row.getTimestamp(METRICS_STATS_COLS.TS).getTime
            val customer_id = row.getString(METRICS_STATS_COLS.CUSTOMER_ID)
            val deployment_id = row.getString(METRICS_STATS_COLS.DEPLOYMENT_ID)
            val cluster_id = row.getString(METRICS_STATS_COLS.CLUSTER_ID)
            val target = row.getString(METRICS_STATS_COLS.TARGET)
            val host_ip = row.getString(METRICS_STATS_COLS.HOST_IP)
            val host_name = row.getString(METRICS_STATS_COLS.HOST_NAME)
            val plugin = row.getString(METRICS_STATS_COLS.PLUGIN)
            val classification =
              row.getString(METRICS_STATS_COLS.CLASSIFICATION)
            val high_water_mark =
              row.getDouble(METRICS_STATS_COLS.HIGH_WATER_MARK)
            val final_score = row.getDouble(METRICS_STATS_COLS.FINAL_SCORE)
            val isNonNumeric = row.getBool(METRICS_STATS_COLS.IS_NON_NUMERIC)
            val valStats = row.getString(METRICS_STATS_COLS.VAL_STATS)
            val velStats = row.getString(METRICS_STATS_COLS.VEL_STATS)
            val tags = row.getString(METRICS_STATS_COLS.TAGS)
            val valStr = row.getString(METRICS_STATS_COLS.VAL_STR)
            new MetricsStats(
              ts,
              customer_id,
              deployment_id,
              cluster_id,
              target,
              host_ip,
              host_name,
              plugin,
              classification,
              high_water_mark,
              final_score,
              isNonNumeric,
              valStats,
              velStats,
              tags,
              valStr
            )
          })
        ret.toArray
      } else {
        Array()
      }
    }
    Log.getLogger.trace(s"Returned ${result.length} entries")
    cassdb.close()
    result
  }

  def writeToKairosDB(ms: Array[MetricsStats]): Unit = {
    if (ms.length > 0) {
      var final_str = ""
      ms.foreach(y => {
        val tags = JacksonWrapper.deserialize[Map[String, String]](y.tags)
        var str: String = new String("put ")
        str += y.plugin + "." + y.classification + " "
        str += y.ts + " "
        str += y.val_str + " "
        str += s"${METRICS_STATS_COLS.CUSTOMER_ID}=" + y.customer_id + " "
        str += s"${METRICS_STATS_COLS.DEPLOYMENT_ID}=" + y.deployment_id + " "
        str += s"${METRICS_STATS_COLS.CLUSTER_ID}=" + y.cluster_id + " "
        str += s"${METRICS_STATS_COLS.TARGET}=" + y.target + " "
        str += s"${METRICS_STATS_COLS.HOST_NAME}=" + y.host_name + " "
        str += s"${METRICS_STATS_COLS.HOST_IP}=" + y.host_ip + " "
        for ((k, v) <- tags) {
          str += k + "=" + v + " "
        }

        final_str += str + "\n"
      })

      val t = new TCPClient()
      // This is to add to the KairosDB server
      Log.getLogger.debug(s"Writing ${ms.length} entries to KairosDB")
      t.Send(final_str, "localhost", 4141)
    }
  }

  def getSystemMetricsQuery(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      time_range: TimeRange,
      grain: Int
  ): String = {
    val query = {
      val tsStr = getTimeRangeAsASetString(time_range, grain)
      s"SELECT ${METRICS_STATS_COLS.CUSTOMER_ID}, ${METRICS_STATS_COLS.DEPLOYMENT_ID}, ${METRICS_STATS_COLS.CLUSTER_ID}, " +
        s"${METRICS_STATS_COLS.TARGET}, ${METRICS_STATS_COLS.HOST_IP}, ${METRICS_STATS_COLS.HOST_NAME}, " +
        s"${METRICS_STATS_COLS.PLUGIN}, ${METRICS_STATS_COLS.CLASSIFICATION}, ${METRICS_STATS_COLS.HIGH_WATER_MARK}, " +
        s"${METRICS_STATS_COLS.FINAL_SCORE}, ${METRICS_STATS_COLS.IS_NON_NUMERIC}, ${METRICS_STATS_COLS.VAL_STATS}, " +
        s"${METRICS_STATS_COLS.VEL_STATS}, ${METRICS_STATS_COLS.TAGS},  ${METRICS_STATS_COLS.VAL_STR}, " +
        s"${METRICS_STATS_COLS.TS} FROM ${METRICS_STATS_COLS.TABLE_NAME} " +
        s"WHERE ${METRICS_STATS_COLS.CUSTOMER_ID} = '${customer_id}'  AND ${METRICS_STATS_COLS.DEPLOYMENT_ID} = '${deployment_id}' " +
        s"AND ${METRICS_STATS_COLS.CLUSTER_ID} = '${cluster_id}' AND ${METRICS_STATS_COLS.TS} IN (${tsStr}) ALLOW FILTERING"
    }
    query
  }

  def getNetworkMetricsQuery(
      customer_id: String,
      deployment_id: String,
      time_range: TimeRange,
      grain: Int
  ): String = {
    val query = {
      val tsStr = getTimeRangeAsASetString(time_range, grain)
      s"SELECT ${METRICS_STATS_COLS.CUSTOMER_ID}, ${METRICS_STATS_COLS.DEPLOYMENT_ID}, ${METRICS_STATS_COLS.CLUSTER_ID}, " +
        s"${METRICS_STATS_COLS.TARGET}, ${METRICS_STATS_COLS.HOST_IP}, ${METRICS_STATS_COLS.HOST_NAME}, ${METRICS_STATS_COLS.PLUGIN}, " +
        s"${METRICS_STATS_COLS.CLASSIFICATION}, ${METRICS_STATS_COLS.HIGH_WATER_MARK}, ${METRICS_STATS_COLS.FINAL_SCORE}, " +
        s"${METRICS_STATS_COLS.IS_NON_NUMERIC}, ${METRICS_STATS_COLS.VAL_STATS}, ${METRICS_STATS_COLS.VEL_STATS}, " +
        s"${METRICS_STATS_COLS.TAGS},  ${METRICS_STATS_COLS.VAL_STR}, ${METRICS_STATS_COLS.TS} FROM ${METRICS_STATS_COLS.TABLE_NAME} " +
        s"WHERE ${METRICS_STATS_COLS.CUSTOMER_ID} = '${customer_id}'  AND ${METRICS_STATS_COLS.DEPLOYMENT_ID} = '${deployment_id}' " +
        s"AND ${METRICS_STATS_COLS.PLUGIN} = 'nwgraph' AND ${METRICS_STATS_COLS.TS} IN (${tsStr}) ALLOW FILTERING"
    }
    query
  }

  def getServiceMetricsQuery(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      time_range: TimeRange,
      grain: Int
  ): String = {
    val query = {
      val tsStr = getTimeRangeAsASetString(time_range, 1)
      s"SELECT ${METRICS_STATS_COLS.CUSTOMER_ID}, ${METRICS_STATS_COLS.DEPLOYMENT_ID}, ${METRICS_STATS_COLS.CLUSTER_ID}, " +
        s"${METRICS_STATS_COLS.TARGET}, ${METRICS_STATS_COLS.HOST_IP}, ${METRICS_STATS_COLS.HOST_NAME}, " +
        s"${METRICS_STATS_COLS.PLUGIN}, ${METRICS_STATS_COLS.CLASSIFICATION}, ${METRICS_STATS_COLS.HIGH_WATER_MARK}, " +
        s"${METRICS_STATS_COLS.FINAL_SCORE}, ${METRICS_STATS_COLS.IS_NON_NUMERIC}, ${METRICS_STATS_COLS.VAL_STATS}, " +
        s"${METRICS_STATS_COLS.VEL_STATS}, ${METRICS_STATS_COLS.TAGS},  ${METRICS_STATS_COLS.VAL_STR}, " +
        s"${METRICS_STATS_COLS.TS} FROM ${METRICS_STATS_COLS.TABLE_NAME} " +
        s"WHERE ${METRICS_STATS_COLS.CUSTOMER_ID} = '${customer_id}' AND ${METRICS_STATS_COLS.DEPLOYMENT_ID} = '${deployment_id}' " +
        s"AND ${METRICS_STATS_COLS.CLUSTER_ID} = '${cluster_id}' AND ${METRICS_STATS_COLS.HOST_NAME} = '*' " +
        s"and ${METRICS_STATS_COLS.PLUGIN} = 'procstat' " +
        s"and ${METRICS_STATS_COLS.TS} IN (${tsStr}) ALLOW FILTERING"
    }
    query
  }

  def getLastTimestampQuery(
      customer_id: String,
      deployment_id: String
  ): String = {
    val query =
      s"SELECT ${METRICS_STATS_COLS.TS} FROM ${METRICS_STATS_COLS.TABLE_NAME} " +
        s"WHERE ${METRICS_STATS_COLS.CUSTOMER_ID} = '${customer_id}' AND ${METRICS_STATS_COLS.DEPLOYMENT_ID} = '${deployment_id}' LIMIT 1"
    query
  }

}
