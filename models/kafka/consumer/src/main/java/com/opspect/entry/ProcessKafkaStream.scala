package com.opspect.entry

/** Created by prashun on 23/5/16.
  */

import breeze.stats.MeanAndVariance
import com.datastax.spark.connector._
import com.opspect.entry.Metrics.{MetricsStats, _}
import com.opspect.entry.rawrecord._
import com.opspect.injest.StreamingHighLevelConsumer
import com.opspect.util._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.{StreamingContext, Time, _}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.immutable._
import scala.collection.mutable.HashMap

object ProcessKafkaStream extends App {

  private var sc: SparkContext = null
  private var sqlContext: org.apache.spark.sql.SQLContext = null

  private def getSparkContext(): SparkContext = {
    sc
  }

  private def getSqlContext: SQLContext = {
    if (sqlContext == null) {
      sqlContext = new SQLContext(sc)
    }
    sqlContext
  }

  private def processRDD(r: RDD[RawRecord]): Unit = {
    r.foreach { rawrecordRDD =>
      {} // TODO rawrecordRDD.write()
    }
  }

  class StreamDerivativesState(val prev: HashMap[String, RawRecord] = null)
      extends Serializable {
    override def toString(): String = {
      prev.toString()
    }
  }

  object StreamDerivatesFunc {
    def updateFunc(
        ts: Time,
        key: String,
        value: Option[RawRecord],
        state: State[StreamDerivativesState]
    ): Option[(String, RawRecord)] = {
      val newRawRecord = value.orNull
      var rr = newRawRecord
      var rmap: HashMap[String, RawRecord] = null
      if (state.exists() == true) {
        val st = state.getOption.orNull
        if (st != null) {
          rmap = st.prev
        }
      }

      if (rmap == null) {
        rmap = HashMap[String, RawRecord]()
      }
      val currentRawRecord = rmap.getOrElse(key, null)
      if (currentRawRecord != null) {
        rmap.remove(key)
      }

      rmap += (key -> newRawRecord)
      val newState: StreamDerivativesState = new StreamDerivativesState(rmap)
      state.update(newState)

      if (currentRawRecord != null && newRawRecord != null) {
        var depth = (currentRawRecord.tags.getOrElse("delta_n", "0")).toInt
        if (depth != 0) {
          rr.tags.remove("delta_n")
        }

        val (n, hm) = currentRawRecord.getDeltaValue(newRawRecord, depth)
        // depth += 1
        rr.tags += (s"delta_${n}_value" -> hm
          .getOrElse(s"delta_${n}_value", 0)
          .toString)
        rr.tags += (s"delta_${n}_time" -> hm
          .getOrElse(s"delta_${n}_time", 0)
          .toString)
        rr.tags += ("delta_n" -> depth.toString)
      }
      Some(key, rr)
    }
  }

  private def avgTags(
      tags: Array[HashMap[String, String]]
  ): Map[String, String] = {
    var hm = HashMap[String, String]()
    tags.foreach(x => {
      x.toList.foreach(x => {
        val key = x._1
        val is_numeric = {
          try {
            x._2.toDouble
            true
          } catch {
            case e: Exception => false
          }
        }
        val prev = hm.getOrElse(key, "0")
        if (prev == "0") {
          hm.remove(key)
        }

        val new_val = {
          if (is_numeric) {
            (prev.toDouble + x._2.toDouble).toString
          } else {
            x._2
          }
        }

        hm += (key -> new_val)
      })
    })

    hm.keys.foreach(x => {
      val c = hm.getOrElse(x, "")
      val is_numeric = {
        try {
          c.toDouble
          true
        } catch {
          case e: Exception => false
        }
      }
      if (is_numeric) {
        val d = c.toDouble / tags.length
        hm.remove(x)
        hm += (x -> d.toString)
      }
    })
    hm.toMap
  }

  private def computeCluster(rl: DStream[RawRecord]): Unit = {
    // First split by category and instance
    var prev_ts = 0L

    rl.foreachRDD(rdd => {
      if (rdd.count() > 0) {
        Log.getLogger.debug(s"RDD Count = ${rdd.count}")
        val rdd1 = rdd.groupBy(x => {
          // Unfortunately this results in a shuffle
          s"plugin=${x.plugin},classification=${x.classification},target=${x.target},cluster_id=${x.cluster_id},deployment_id=${x.deployment_id},customer_id=${x.customer_id},ts=${x.ts}"
        })
        val rdd2 = rdd1.collect()
        var loop_done = false

        rdd2.foreach(r => {
          val rdd3 = r._2
          val customer_id = r._2.last.customer_id
          val deployment_id = r._2.last.deployment_id
          val cluster_id = r._2.last.cluster_id
          val target = r._2.last.target
          val host_ip = "255.255.255.255"
          val host_name = "*"
          val plugin = r._2.last.plugin
          val classification = r._2.last.classification
          val ts = r._2.last.ts.toLong
          val tags = avgTags(r._2.map(_.tags).toArray)
          val val_str = r._2.last.value

          if (prev_ts != 0L) {
            if ((ts - prev_ts) > 60 * 1000) {
              Log.getLogger.warn(
                s"Falling behind in processing = ${ts - prev_ts} ms"
              )
            }
            if (!loop_done) {
              prev_ts = ts
              loop_done = true
            }
          }

          val hm_peer = Stats.findPeerMean(r._2.map(_.valtodouble()).toArray)
          val peer: MeanAndVariance = hm_peer._1
          val doublePoi: Vector[Double] =
            for (x <- r._2.map(_.valtodouble()).toVector) yield x
          val count = doublePoi.length

          var is_water_mark_present: Boolean = false
          var high_water_mark: Double = 0d

          try {
            high_water_mark = tags.get("high_water_mark").toString.toDouble
            is_water_mark_present = true
          } catch {
            case e: Exception => {}
          }

          val fdVal = new Array[Int](7)
          hm_peer._2.foreach(x => {
            val i = hm_peer._2.indexOf(x)
            fdVal(i) = x.length
          })

          fdVal(6) = r._2
            .map(_.valtodouble())
            .count(is_water_mark_present && _ >= high_water_mark)

          val sumScore = Metrics.computeScore(fdVal, count)
          var finalScore: Double = math.max(sumScore, fdVal(6) / count)
          val fdVal1 = new Array[Int](6)
          val velStats = new Metrics.DStats(0, 0, 0, 0, 0, fdVal1)
          val valStats = new Metrics.DStats(
            count,
            doublePoi.min,
            doublePoi.max,
            peer.mean,
            peer.stdDev,
            fdVal
          )
          val peerMetric = new MetricsStats(
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
            finalScore,
            r._2.last.is_non_numeric(),
            JacksonWrapper.serialize(valStats),
            JacksonWrapper.serialize(velStats),
            JacksonWrapper.serialize(tags),
            val_str
          )
          val _tmp = sc.parallelize(rdd3.toList)
          var metric_arr = List[Metrics.MetricsStats]()
          metric_arr ::= peerMetric
          metric_arr :::= computeInstance(_tmp, peerMetric)
          persistData(metric_arr)
        })
      } else {
        Log.getLogger.debug("Empty RDD")
      }
    })

  }

  private def persistData(msl: List[Metrics.MetricsStats]): Unit = {
    val sc = getSparkContext()

    Log.getLogger.debug(s"No of metrics to be persisted = ${msl.length}")
    try {
      val collection = sc.parallelize(msl.map(x => {
        (
          x.ts,
          Metrics.mkUhash(x),
          x.customer_id,
          x.deployment_id,
          x.cluster_id,
          x.target,
          x.host_ip,
          x.host_name,
          x.plugin,
          x.classification,
          x.high_water_mark,
          x.final_score,
          x.is_non_numeric,
          x.val_stats,
          x.vel_stats,
          x.tags,
          x.val_str
        )
      }))
      try {
        val table: String = METRICS_STATS_COLS.TABLE_NAME
        val keyspace: String = "nuvidata"

        collection.saveToCassandra(
          keyspace,
          table,
          SomeColumns(
            METRICS_STATS_COLS.TS as METRICS_STATS_COLS.TS,
            METRICS_STATS_COLS.UHASH as METRICS_STATS_COLS.UHASH,
            METRICS_STATS_COLS.CUSTOMER_ID as METRICS_STATS_COLS.CUSTOMER_ID,
            METRICS_STATS_COLS.DEPLOYMENT_ID as METRICS_STATS_COLS.DEPLOYMENT_ID,
            METRICS_STATS_COLS.CLUSTER_ID as METRICS_STATS_COLS.CLUSTER_ID,
            METRICS_STATS_COLS.TARGET as METRICS_STATS_COLS.TARGET,
            METRICS_STATS_COLS.HOST_IP as METRICS_STATS_COLS.HOST_IP,
            METRICS_STATS_COLS.HOST_NAME as METRICS_STATS_COLS.HOST_NAME,
            METRICS_STATS_COLS.PLUGIN as METRICS_STATS_COLS.PLUGIN,
            METRICS_STATS_COLS.CLASSIFICATION as METRICS_STATS_COLS.CLASSIFICATION,
            METRICS_STATS_COLS.HIGH_WATER_MARK as METRICS_STATS_COLS.HIGH_WATER_MARK,
            METRICS_STATS_COLS.FINAL_SCORE as METRICS_STATS_COLS.FINAL_SCORE,
            METRICS_STATS_COLS.IS_NON_NUMERIC as METRICS_STATS_COLS.IS_NON_NUMERIC,
            METRICS_STATS_COLS.VAL_STATS as METRICS_STATS_COLS.VAL_STATS,
            METRICS_STATS_COLS.VEL_STATS as METRICS_STATS_COLS.VEL_STATS,
            METRICS_STATS_COLS.TAGS as METRICS_STATS_COLS.TAGS,
            METRICS_STATS_COLS.VAL_STR as METRICS_STATS_COLS.VAL_STR
          )
        )
        Log.getLogger.debug(
          s"Finished cassandra write with length = ${msl.length}"
        )

        val fmsl = msl
          .filterNot(_.plugin == "nwgraph")
          .filterNot(_.is_non_numeric)
          .toArray
        if (fmsl.nonEmpty) {
          Metrics.writeToKairosDB(fmsl)
          Log.getLogger.debug(
            s"Finished kairosdb write with length = ${fmsl.length}"
          )
        }
      } catch {
        case e: Exception => Log.getLogger.error(e.getMessage)
      }
    } catch {
      case e: Exception => Log.getLogger.error(s"Got Exception ${e.getMessage}")
    }
  }

  private def initMeanStats(
      customer_id: String,
      ts: Long,
      invalidateCache: Boolean = false
  ): Unit = {
    val sqlContext = getSqlContext
    val sqlTableName: String = "metrics_stats_win"
    val keyspace = "nuvidata"
    val table = METRICS_STATS_COLS.TABLE_NAME

    // val ts = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).getTimeInMillis
    val windowDelta = 5 * 60 * 1000
    val delta = 1 * 60 * 1000
    try {
      if (invalidateCache) {
        throw new Exception(s"${sqlTableName} cache invalidated")
      }
      if (sqlContext.isCached(sqlTableName)) {
        // Check the last updated time if greater than 1 minute from now rebuid cache
        // sqlContext.uncacheTable(tableName)
        val sqlStmt =
          s"SELECT count(*) AS count FROM ${sqlTableName} WHERE customer_id = '${customer_id}' and ts <= ${ts}"
        val rows = sqlContext.sql(sqlStmt)
        val myrows = rows.map(_.getValuesMap[Long](List("count"))).collect()
        if (myrows.length > 0) {
          val count: Long = myrows(0).getOrElse("count", 0)
          if (count == 0) {
            sqlContext.uncacheTable(sqlTableName)
            throw new Exception(s"${sqlTableName} cache invalidated")
          }
        } else {
          sqlContext.uncacheTable(sqlTableName)
          throw new Exception(s"${sqlTableName} cache invalidated")
        }
      }
    } catch {
      case e: Exception => {
        Log.getLogger.error(e.getMessage)
        Log.getLogger.error(
          s"${sqlTableName} table does not exist in cache for ts <= ${ts}"
        )

        val rdd = sc
          .cassandraTable(keyspace, table)
          .select(
            METRICS_STATS_COLS.TS,
            METRICS_STATS_COLS.CUSTOMER_ID,
            METRICS_STATS_COLS.DEPLOYMENT_ID,
            METRICS_STATS_COLS.CLUSTER_ID,
            METRICS_STATS_COLS.TARGET,
            METRICS_STATS_COLS.HOST_IP,
            METRICS_STATS_COLS.HOST_NAME,
            METRICS_STATS_COLS.PLUGIN,
            METRICS_STATS_COLS.CLASSIFICATION,
            METRICS_STATS_COLS.IS_NON_NUMERIC,
            METRICS_STATS_COLS.HIGH_WATER_MARK,
            METRICS_STATS_COLS.FINAL_SCORE,
            METRICS_STATS_COLS.VAL_STATS,
            METRICS_STATS_COLS.VEL_STATS,
            METRICS_STATS_COLS.TAGS,
            METRICS_STATS_COLS.VAL_STR
          )
          .where(s"${METRICS_STATS_COLS.CUSTOMER_ID} = '${customer_id}'")
          .where(s"${METRICS_STATS_COLS.HOST_NAME} = '*'")
          .where(
            s"${METRICS_STATS_COLS.TS} >= ? and ts <= ? ",
            (ts - windowDelta),
            ts
          )
          .map(row => {

            new Metrics.MetricsStats(
              row.getLong(METRICS_STATS_COLS.TS),
              row.getString(METRICS_STATS_COLS.CUSTOMER_ID),
              row.getString(METRICS_STATS_COLS.DEPLOYMENT_ID),
              row.getString(METRICS_STATS_COLS.CLUSTER_ID),
              row.getString(METRICS_STATS_COLS.TARGET),
              row.getString(METRICS_STATS_COLS.HOST_IP),
              row.getString(METRICS_STATS_COLS.HOST_NAME),
              row.getString(METRICS_STATS_COLS.PLUGIN),
              row.getString(METRICS_STATS_COLS.CLASSIFICATION),
              row.getDouble(METRICS_STATS_COLS.HIGH_WATER_MARK),
              row.getDouble(METRICS_STATS_COLS.FINAL_SCORE),
              row.getBoolean(METRICS_STATS_COLS.IS_NON_NUMERIC),
              row.getString(METRICS_STATS_COLS.VAL_STATS),
              row.getString(METRICS_STATS_COLS.VEL_STATS),
              row.getString(METRICS_STATS_COLS.TAGS),
              row.getString(METRICS_STATS_COLS.VAL_STR)
            )
          })

        import sqlContext.implicits._
        val rddf = rdd.toDF()

        scala.util.Try(sqlContext.uncacheTable(sqlTableName))
        scala.util.Try(sqlContext.dropTempTable(sqlTableName))

        rddf.registerTempTable(sqlTableName)
        sqlContext.cacheTable(sqlTableName)
      }
    }
  }

  private def getMeanStats(
      customer_id: String,
      deployment_id: String,
      cluster_id: String,
      plugin: String,
      classification: String,
      target: String,
      ts: Long
  ): Metrics.MetricsStats = {
    try {
      initMeanStats(customer_id, ts)
    } catch {
      case e: Exception => return null
    }

    val tableName: String = "metrics_stats_win"
    val sqlContext = getSqlContext
    val sqlStmt: String = {
      s"SELECT size, min_val, max_val, mean, stddev, high_water_mark, final_score, val_stats, vel_stats, tags, val_str " +
        s" FROM ${tableName} " +
        s"WHERE customer_id = '${customer_id}' and deployment_id = '${deployment_id}' and  cluster_id = '${cluster_id}' and plugin = '${plugin}' and classification = '${classification}' and ts <= ${ts} " +
        s"ORDER BY ts DESC LIMIT 1"
    }
    val hostip = "255.255.255.255"
    val hostname = "*"

    // Logit(sqlStmt)
    val rows = sqlContext.sql(sqlStmt)

    val rowSet: Array[Metrics.MetricsStats] = rows
      .map(r => {

        new Metrics.MetricsStats(
          ts,
          customer_id,
          deployment_id,
          cluster_id,
          target,
          hostip,
          hostname,
          plugin,
          classification,
          r.getDouble(r.fieldIndex("high_water_mark")),
          r.getDouble(r.fieldIndex("final_score")),
          r.getBoolean(r.fieldIndex("is_non_numeric")),
          r.getString(r.fieldIndex("val_stats")),
          r.getString(r.fieldIndex("vel_stats")),
          r.getString(r.fieldIndex("tags")),
          r.getString(r.fieldIndex("val_str"))
        )

      })
      .collect()

    if (rowSet.length > 0) {
      rowSet(0)
    } else {
      initMeanStats(customer_id, ts, true)
      null
    }
  }

  private def computeDerivatives(rl: DStream[RawRecord]): DStream[RawRecord] = {
    val mappedStream: DStream[(String, RawRecord)] = rl.map(x => {
      val hashString =
        s"customer_id=${x.customer_id},deployment_id=${x.deployment_id},cluster_id=${x.cluster_id},host_ip=${x.host_ip},host_name=${x.host_name}," +
          s"target=${x.target},plugin=${x.plugin},classification=${x.classification}"
      (hashString, x)
    })

    val stateDStream: DStream[(String, RawRecord)] = mappedStream.mapWithState(
      StateSpec.function(StreamDerivatesFunc.updateFunc _)
    )
    stateDStream.map(rdd => {
      rdd._2
    })
  }

  private def computeInstance(
      rdd: RDD[RawRecord],
      _peer: Metrics.MetricsStats
  ): List[Metrics.MetricsStats] = {
    val v = JacksonWrapper.deserialize[Metrics.DStats](_peer.val_stats)
    val peer = new MeanAndVariance(v.mean, v.stddev * v.stddev, v.size)

    // First split by category and instance
    if (rdd.count() > 0) {
      val rdd1 = rdd.groupBy(x => {
        s"plugin=${x.plugin},classification=${x.classification},target=${x.target},cluster_id=${x.cluster_id}," +
          s"host_ip=${x.host_ip},host_name=${x.host_name}," +
          s"deployment_id=${x.deployment_id},customer_id=${x.customer_id},ts=${x.ts}"
      })
      val rdd2 = rdd1.collect()
      val rdd3 = rdd2.map(r => {

        val customer_id = r._2.last.customer_id
        val deployment_id = r._2.last.deployment_id
        val cluster_id = r._2.last.cluster_id
        val target = r._2.last.target
        val host_ip = r._2.last.host_ip
        val host_name = r._2.last.host_name
        val plugin = r._2.last.plugin
        val classification = r._2.last.classification
        val ts = r._2.last.ts.toLong
        val tags = avgTags(r._2.map(_.tags).toArray)
        val val_str = r._2.last.value

        /* Calculating the Frequency distribution across m - 3sd, m - 2sd, m, m + 2sd, m + 3sd */

        val doublePoiValue: Vector[Double] =
          for (x <- r._2.map(_.valtodouble()).toVector) yield x
        val doublePoiValue1: Vector[Double] = {
          for (x <- r._2.toVector) yield {
            val dx = {
              try {
                x.tags.getOrElse("delta_0_value", "0").toString.toDouble
              } catch {
                case e: Exception => 0
              }
            }
            var dt = x.tags.getOrElse("delta_0_time", "1").toString.toDouble
            if (dt == 0) {
              dt = 0.001
            }
            val vel = dx / dt
            vel
          }
        }

        val instance: MeanAndVariance =
          breeze.stats.meanAndVariance(doublePoiValue)
        val instance1: MeanAndVariance =
          breeze.stats.meanAndVariance(doublePoiValue1)

        val fdVal = new Array[Int](7)
        val fdVal1 = new Array[Int](6)
        var is_water_mark_present: Boolean = false
        var high_water_mark: Double = 0d
        try {
          high_water_mark = tags.get("high_water_mark").toString.toDouble
          is_water_mark_present = true
          fdVal(6) = r._2.map(_.valtodouble()).count(_ >= high_water_mark)
        } catch {
          case e: Exception => {}
        }
        /*
                    var peer: Metrics.MetricsStats = getMeanStats(customer_id, category, plugin, classification, ts)
                    if (peer == null) {
                      val velStats : VelocityStats = new VelocityStats(instance1.count, doublePoiValue1.min, doublePoiValue1.max,
                        instance1.mean, instance1.stdDev, fdVal1)
                      peer = new Metrics.MetricsStats(ts, customer_id, category, host_ip, host_name, plugin, classification, high_water_mark, 1.0,
                        instance.count, doublePoiValue.min, doublePoiValue.max, instance.mean, instance.stdDev,
                        fdVal(0), fdVal(1), fdVal(2),fdVal(3), fdVal(4), fdVal(5), fdVal(6),velStats.toString()
                      )
                    }
         */
        val _ts = Stats.findFPMap(r._2.map(_.valtodouble()).toArray, instance)
        _ts.foreach(x => {
          val i = _ts.indexOf(x)
          fdVal(i) = x.length
        })

        val _ts1 = Stats.findFPMap(doublePoiValue1.toArray, instance1)
        _ts1.foreach(x => {
          val i = _ts1.indexOf(x)
          fdVal1(i) = x.length
        })

        val sumScore = {
          if (_peer.final_score != 0) {
            (Metrics.computeScore(
              fdVal,
              doublePoiValue.length
            ) - _peer.final_score) / _peer.final_score
          } else {
            0
          }
        }

        var finalScore: Double = math.max(sumScore, fdVal(6) / instance.count)
        val velStats: Metrics.DStats = new Metrics.DStats(
          doublePoiValue1.length,
          doublePoiValue1.min,
          doublePoiValue1.max,
          instance1.mean,
          instance.stdDev,
          fdVal1
        )
        val valStats: Metrics.DStats = new Metrics.DStats(
          doublePoiValue1.length,
          doublePoiValue.min,
          doublePoiValue.max,
          instance.mean,
          instance.stdDev,
          fdVal
        )
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
          finalScore,
          r._2.last.is_non_numeric,
          JacksonWrapper.serialize(valStats),
          JacksonWrapper.serialize(velStats),
          JacksonWrapper.serialize(tags),
          val_str
        )
      })
      rdd3.toList
    } else {
      List()
    }
  }

  def run(): Boolean = {

    val conf = new SparkConf().setAppName("InfraredConsumer")
    val cassandra_ip: String = "127.0.0.1"
    conf.set("spark.cassandra.connection.host", cassandra_ip)
    sc = new SparkContext(conf)

    val zooKeeper: String = "localhost:2181"
    val groupId: String = "grplog"
    val topic: String = "log"

    val ssc = new StreamingContext(sc, Seconds(60))
    val messages = ssc.receiverStream(
      new StreamingHighLevelConsumer(zooKeeper, groupId, topic)
    )

    val rawrecordStream: DStream[RawRecord] = messages.map(line => {
      val str = Line.Parse(line)
      str
    })

    ssc.checkpoint("/tmp")
    val rawrecordDerivativesStream: DStream[RawRecord] = computeDerivatives(
      rawrecordStream
    )
    computeCluster(rawrecordDerivativesStream)

    val diff = {
      val ts = java.util.Calendar
        .getInstance(java.util.TimeZone.getTimeZone("UTC"))
        .getTimeInMillis
      val tsInSeconds: Long = ts / 1000
      val tsInMinutes: Long = tsInSeconds / 60
      val tsInMillis: Long = tsInMinutes * 60 * 1000
      (tsInMillis + 60 * 1000) - ts
    }
    Thread.sleep(diff)
    ssc.start()
    ssc.awaitTermination()

    true
  }

}
