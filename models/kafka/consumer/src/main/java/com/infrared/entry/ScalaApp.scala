package com.infrared.entry

/**
 * Created by asitk on 18/2/16.
 */

import java.util.Calendar

import com.datastax.spark.connector._
import com.infrared.entry.rawrecord._
import com.infrared.util.CassandraDB.Address
import com.infrared.util._
import org.apache.log4j.Level
import org.apache.spark.SparkConf
import org.apache.spark.streaming._

import scala.annotation.tailrec
import scala.collection.immutable._
import scala.collection.mutable.{ListBuffer, SynchronizedQueue}
import scala.util.Random

//import org.apache.spark.ml.clustering._

import org.apache.spark.SparkContext
import org.apache.spark.mllib.clustering._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SQLContext}

//////////////////////////////////// VARIANCE ///////////////////////////////////////////

class StreamVarianceData(val label: String = "label", val value: Double = 0.0)
  extends Serializable {
  // override def toString():String = {label+s", value=$value"}
}

class StreamVarianceState(val count: Long = 0,
                          val mean: Double = 0.0,
                          val sumSquaresVar: Double = 0.0,
                          val welfordVar: Double = 0.0,
                          val stddev: Double = 0.0)
  extends Serializable {
  override def toString: String = {
    new String("count=$count%ld, mean=${mean}%.4f, sumSquaresVar=$sumSquaresVar%.4f, welfordVar=$welfordVar%.4f, stddev=$stddev%.4f")
  }
}


/**
 * A container for the DStream.updateStateByKey updateFunc.
 */
object StreamVarianceUpdateFunction {
  /**
   * @param values The new data.
   * @param state The previous state from the last RDD iteration.
   * @return The new state: created from the previous state by updating it
   *         with the new data.
   */
  val updateFunc: (scala.collection.Seq[Double], Option[StreamVarianceState]) => Option[StreamVarianceState] =
    (values: scala.collection.Seq[Double], state: Option[StreamVarianceState]) => {
      if (values.isEmpty) {
        state
      } else {
        val prevState = state.getOrElse(new StreamVarianceState())
        val prevCount = prevState.count
        val prevMean = prevState.mean
        val prevSumSquaresVar = prevState.sumSquaresVar
        val prevWelfordVar = prevState.welfordVar

        val valsCount = values.length
        val valsMean = values.sum / valsCount

        // Generate the new running statistics.
        val count: Long = prevState.count + values.size
        val mean = (prevState.mean * prevState.count + values.sum) / count

        val valsSumSquaresVariance = sumSquaresVariance(values)
        val sumSquaresVar = combinePrevCurrVars(prevSumSquaresVar, prevMean, prevCount,
          valsSumSquaresVariance, valsMean, valsCount)

        val valsWelfordVariance = welfordVariance(values)
        val welfordVar = combinePrevCurrVars(prevWelfordVar, prevMean, prevCount,
          valsWelfordVariance, valsMean, valsCount)

        val stddev = math.sqrt(welfordVar)

        Some(new StreamVarianceState(count,
          mean,
          sumSquaresVar,
          welfordVar,
          stddev))
      }
    }

  /**
   * Use "sum of squares minus mean squared" to find the variance.
   */
  def sumSquaresVariance(values: scala.collection.Seq[Double]): Double = {
    val k: Int = values.size
    val valuesMean: Double = values.sum / values.length
    (values.map(x => x * x).sum / k) - valuesMean * valuesMean
  }

  /**
   * Use "Welford's Algorithm" to find the variance.  We iterate through a batch
   * of values per RDD, even though the algorithm could be done as a streaming
   * algorithm if we did one Welford iteration per each Spark Streaming
   * iteration; i.e., if each RDD had only one x_i data value.
   * @return The variance of the values.
   */
  def welfordVariance(values: scala.collection.Seq[Double]): Double = {
    @tailrec
    def welfordRec(values: scala.collection.Seq[Double], m2: Double, mean: Double, n: Int): Double = {
      if (values.isEmpty) m2 / (n - 1) // Note we need n-1 for pop. variance here.
      else {
        val x = values.head
        val delta = x - mean
        val newMean = mean + delta / n
        val newM2 = m2 + delta * (x - newMean)
        welfordRec(values.tail, newM2, newMean, n + 1)
      }
    }
    welfordRec(values, 0.0, 0.0, 1)
  }

  /**
   * Given the variances, means, and counts from two sets of data, find the
   * variance of the combined set.  This algorithm is equivalent to the algorithm
   * given in Wikipedia's "Algorithms for calculating variance", crediting Chan.
   * Another equivalent implementation is in spark.util.StatCounter.
   */
  def combinePrevCurrVars(prevVar: Double, prevMean: Double, prevCount: Long,
                          currVar: Double, currMean: Double, currCount: Long): Double = {
    // The proportion
    val p: Double = prevCount.toDouble / (prevCount + currCount)
    p * prevVar + (1 - p) * currVar + p * (1 - p) * (prevMean - currMean) * (prevMean - currMean)
  }
}

object StreamVarianceDataGenerator {
  def pushToRdd(ssc: StreamingContext,
                rddQueue: SynchronizedQueue[RDD[StreamVarianceData]],
                pause: Int): Unit = {
    for (i <- 0 to 9) {
      val sineCount = 99
      val dataListSine: List[StreamVarianceData] = {
        for (j <- 0 to sineCount)
          yield {
            new StreamVarianceData("sine", scala.math.sin(2 * scala.math.Pi * Random.nextDouble))
          }
      }.toList

      val dataListA: List[StreamVarianceData] = List(
        // LabelA should avg to zero and for i=k have variance = sum(i^2 + (-i)^2)/2k.
        new StreamVarianceData("cpu.usage_user", i),
        new StreamVarianceData("cpu.usage_user", -i)
      )

      val dataList: List[StreamVarianceData] = dataListA ++ dataListSine // :::
      rddQueue += ssc.sparkContext.makeRDD(dataList)
      Thread.sleep(pause)
    }
  }
}

object StreamVarianceDataGeneratorTest {
  def pushToRdd(ssc: StreamingContext,
                rddQueue: SynchronizedQueue[RDD[StreamVarianceData]],
                pause: Int): Unit = {
    val dataListA: List[StreamVarianceData] = List(
      new StreamVarianceData("cpu.usage_user", 1),
      new StreamVarianceData("cpu.usage_user", 2),
      new StreamVarianceData("cpu.usage_user", 3),
      new StreamVarianceData("cpu.usage_user", 5),
      new StreamVarianceData("cpu.usage_user", 3),
      new StreamVarianceData("cpu.usage_user", 4),
      new StreamVarianceData("cpu.usage_user", 5),
      new StreamVarianceData("cpu.usage_user", 4),
      new StreamVarianceData("cpu.usage_user", 5),
      new StreamVarianceData("cpu.usage_user", 4)
    )

    val dataList: List[StreamVarianceData] = dataListA
    rddQueue += ssc.sparkContext.makeRDD(dataList)

    Thread.sleep(pause)
    val dataListB: List[StreamVarianceData] = List(
      new StreamVarianceData("cpu.usage_user", 1),
      new StreamVarianceData("cpu.usage_user", 2),
      new StreamVarianceData("cpu.usage_user", 3),
      new StreamVarianceData("cpu.usage_user", 5),
      new StreamVarianceData("cpu.usage_user", 3),
      new StreamVarianceData("cpu.usage_user", 40),
      new StreamVarianceData("cpu.usage_user", 5),
      new StreamVarianceData("cpu.usage_user", 50),
      new StreamVarianceData("cpu.usage_user", 1),
      new StreamVarianceData("cpu.usage_user", 4)
    )

    val dataList2: List[StreamVarianceData] = dataListB
    rddQueue += ssc.sparkContext.makeRDD(dataList2)

    Thread.sleep(pause)
  }
}


//////////////////////////////////// VARIANCE ///////////////////////////////////////////


//////////////////////////////////// MEAN & SD OF RDD //////////////////////////////////

object ScalaApp extends App {
  private var sc: SparkContext = null
  private var sqlContext: org.apache.spark.sql.SQLContext = null
  private var sqlHiveContext: org.apache.spark.sql.hive.HiveContext = null

  def processRDD(r: RDD[RawRecord]): Unit = {
    r.foreach {
      rawrecordRDD => rawrecordRDD.write()
    }
  }

  def processVariance(o: (String, StreamVarianceState)): Unit = {
    val str: String = o._1
    /* x.customerid + ":::" + x.ccategory + ":::"+ "all" + ":::"  + "all" + ":::" + x.plugin + ":::" + x.classification, x.valtodouble */
    /*customerid::ccategory.hostname
    rectype: Int, plugin: String, classification:String, hostip: String,
    hostname:String, category: String, customerid : String, value:String, ts:String,
    tags : scala.collection.mutable.Map[String, String], hinttagv:String
    */
    var field = str.split(":::")
    val customerid = field(0)
    val deploymentid = field(1)
    var clusterid = field(2)
    var hostip = field(3)
    var hostname = field(4)
    var plugin = field(5)
    var classification = field(6)
    var target = field(7)

    val state: StreamVarianceState = o._2
    val orig_ts_ms: Long = System.currentTimeMillis()
    val ts: Long = orig_ts_ms / 1000
    val tagWelford = scala.collection.mutable.HashMap[String, String]("stat" -> "welford")
    val tagSumSq = scala.collection.mutable.HashMap[String, String]("stat" -> "sumsquares")
    val tagStd = scala.collection.mutable.HashMap[String, String]("stat" -> "stddev")

    // create a rawrecord and write it
    val rawWelford: RawRecord = {
      new RawRecord(RawRecordType.SYSTEM, plugin, classification + "_welford", target, hostip, hostname, clusterid,
        deploymentid, customerid, state.welfordVar.toString, ts.toString, orig_ts_ms, tagWelford, "")
    }

    val rawSumSq: RawRecord = {
      new RawRecord(RawRecordType.SYSTEM, plugin, classification + "_sumsq", target, hostip, hostname, clusterid,
        deploymentid, customerid, state.sumSquaresVar.toString, ts.toString, orig_ts_ms, tagSumSq, "")
    }

    val rawStd: RawRecord = {
      new RawRecord(RawRecordType.SYSTEM, plugin, classification + "_std", target, hostip, hostname, clusterid,
        deploymentid, customerid, state.stddev.toString, ts.toString, orig_ts_ms, tagStd, "")
    }

    //System.out.println(rawStd.toString())
    //System.out.println(rawWelford.toString())
    //System.out.println(rawSumSq.toString())
    rawWelford.write()
    rawSumSq.write()
    rawStd.write()
  }

  def processStateRDD(r: RDD[(String, StreamVarianceState)]): Unit = {
    r.foreach(processVariance)
    r.take(10).foreach(println)
  }

  def getSparkContext: SparkContext = {
    sc
  }


  def getSqlContext: SQLContext = {
    if (sqlContext == null) {
      sqlContext = new SQLContext(sc)
    }
    sqlContext
  }

  case class OutlierStats(
                           ts: Long,
                           customerid: String,
                           category: String,
                           hostip: String,
                           hostname: String,
                           plugin: String,
                           classification: String,
                           otype: String,
                           value: Double,
                           prb: Double,
                           cmpval: Double
                           )

  def persistOutliers(customerid: String, category: String, hostip: String, hostname: String, otype: String,
                      plugin: String, classification: String,
                      value: Double, prb: Double, cmpval: Double, ts: Long): Unit = {
    val sc = ScalaApp.getSparkContext
    val ts = Calendar.getInstance().getTimeInMillis

    val collection = sc.parallelize(Seq(new OutlierStats(ts, customerid, category, hostip, hostname, plugin,
      classification, otype, value, prb, cmpval)))
    try {
      val table: String = "outlier_stats"
      val keyspace: String = "nuvidata"
      Log.getLogger.trace(s"${ts} ${customerid} ${category} ${hostip} ${hostname}  ${plugin} ${classification} ${otype} ${value} ${prb} ${cmpval}")
      collection.saveToCassandra(keyspace, table, SomeColumns("ts" as "ts", "customerid" as "customerid",
        "category" as "category", "hostip" as "hostip", "hostname" as "hostname",
        "plugin" as "plugin", "classification" as "classification",
        "otype" as "otype", "value" as "value", "prb" as "prb", "cmpval" as "cmpval"
      ))
    } catch {
      case e: Exception => Log.getLogger.error(e.getMessage)
    }
  }


  case class NwPortMap(ip: String, svc: Int, ipver: Int, proto: Int, port: Int, interface: String)

  def createNwPortInfo(): Unit = {
    val sqlContext = getSqlContext
    val tableName: String = "nw_port_map"
    try {
      if (sqlContext.isCached(tableName)) {
        //Check the last updated time if greater than 1 minute from now rebuid cache
        //sqlContext.uncacheTable(tableName)
        Log.getLogger.trace(s"${tableName} is cached")
      }
    } catch {
      case e: Exception => {
        Log.getLogger.error(s"${tableName} table does not exist in cache")
        var df = sqlContext.read.parquet(tableName)
        if (df.count() == 0) {
          val npm: NwPortMap = new NwPortMap("0", 0, 0, 0, 0, "*")
          val rdd: RDD[NwPortMap] = sc.parallelize(Seq(npm))

          import sqlContext.implicits._
          df = rdd.toDF() //toDF("ip", "svc", "ipver", "proto","port", "interface")
          df.write.parquet(tableName)
          Log.getLogger.trace(s"${tableName} registered")
        } else {
          df.toDF()
        }
        df.registerTempTable(tableName)
        sqlContext.cacheTable(tableName)
      }
    }
  }

  def testCassandra(): Boolean = {
    val conf = new SparkConf().setAppName("InfraredConsumer")
    conf.set("spark.cassandra.connection.host", "127.0.0.1")
    val sc = new SparkContext(conf)
    val keyspace: String = "nuvidata"
    val table: String = "deployment_map"

    val rdd = sc.cassandraTable(keyspace, table).select("customer_id", "deployment_id", "name", "description", "active", "ts")
    rdd.map(row => {
      val customer_id = row.getString("customer_id")
      val deploymentid = row.getString("deployment_id")
      val name = row.getString("name")
      val description = row.getString("description")
      val active = row.getBoolean("active")
      val ts = row.getDate("ts")
      (customer_id, deploymentid, name, description, active, ts)
    }).foreach(println)

    true
  }


  def testScalaCassandra(): Boolean = {
    val addressList = new ListBuffer[CassandraDB.Address]()
    addressList += new Address("127.0.0.1", 9042)
    CassandraDB.start()
    val cassdb = CassandraDB.getScalaInstance("nuvidata", addressList.toList)
    val resultSet = cassdb.executeQuery("select customer_id, deployment_id, ts, name, description, active from deployment_map")
    if (resultSet != null) {
      println(resultSet.getColumnDefinitions)
    }
    cassdb.close()
    CassandraDB.shutdown()

    true
  }

  def testProducer(): Unit = {
    val message = "Hello there"
    val key = "details"
    //HighLevelProducer.send("meta", key.getBytes(), message.getBytes())
  }


  def correlate(): Unit = {
    val conf = new SparkConf().setAppName("InfraredConsumer")
    val cassandra_ip: String = "127.0.0.1"
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)
    // Crates a DataFrame
    for (i <- 0 to 3 by 1) {
      val df: DataFrame = sqlContext.createDataFrame(Seq(
        (1, 0.3),
        (2, 4.3),
        (3, 6.7),
        (4, 14.0),
        (5, 54.3),
        (6, 60.9),
        (7, 20.6),
        (8, 31.4),
        (9, 8.7)
      )).toDF("id", "features")

      val rowrdd = df.map(r => {
        (r.getAs[Int]("id"), r.getAs[Double]("features"))
      })
      rowrdd.cache()
      val vect = rowrdd.map(r => {
        Vectors.dense(r._2)
      })
      vect.cache()

      Log.getLogger.trace("Starting the kmeans train")
      // Trains a k-means model
      val kmeans = KMeans.train(vect, 3, 1)
      kmeans.clusterCenters.foreach(println)
      val pred = rowrdd.map(r => {
        (r._1, r._2, kmeans.predict(Vectors.dense(r._2)))
      })
      pred.foreach(println)
      Log.getLogger.trace("Ending the kmeans train")
    }
  }

  def testcache(): Unit = {
    import com.infrared.util._
    MyCache.put("name", "prashun")
    println(MyCache.get("name"))
    println("Sleeping for 6 seconds")
    Thread.sleep(6000)
    println(MyCache.get("name"))
    MyCache.put("name", "asitk")
    MyCache.invalidate("name")
    println(MyCache.get("name"))
  }

  def printOptions(): Unit = {
    val message = {
      "<Options> [log-level=<error|debug|trace|warn|info|off>] \n" +
        "Options can be any of the following :\n" +
        " process-kafka-stream - " +
        "  processes the kafka stream using spark and updates metricstats\n" +
        " process-metrics-for-anomalies <number> <minute|hour> [simulate] - " +
        " process the metricsstats for anomaly detection and prepares thermal stats\n" +
        "  number is an integer value greater than 0 and unit is either minute or hour and \n" +
        "    represents every n minute for the present hour or at a n hour interval\n" +
        "  use simulate if you want to simulate anomalies\n" +
        " process-past-metrics-for-anomalies [simulate] -" +
        " catches up all the pending process till now with simulate option\n" +
        " test-api-set  - runs through all the exported api for deployment, cluster and node\n" +
        " update-kafka-for-agents  - updates the kafka queue meta with node and service info for the agents\n" +
        " test-spark-cassandra - test to see if the cassandra spark connection is up and running \n" +
        " test-java-cassandra  - test to see if the cassandra java connection is up and running\n" +
        " test-kafka-producer  - inserts message into the meta queue of kafka\n" +
        " nuvidata-engine  - starts the embedded jetty server as a webservice API\n" +
        " ai - test to see mllib is working correctly"
    }
    println(message)

  }

  override def main(arg: Array[String]): Unit = {
    org.apache.log4j.BasicConfigurator.configure()
    val logger = Log.getLogger
    logger.setLevel(Level.ERROR)
    if (!arg.isEmpty) {
      val last_arg = arg.last.toLowerCase.split("=")
      if (last_arg.head == "log-level" && last_arg.head != last_arg.last) {
        last_arg.last match {
          case "error" => logger.setLevel(Level.ERROR)
          case "debug" => logger.setLevel(Level.DEBUG)
          case "trace" => logger.setLevel(Level.TRACE)
          case "warn" => logger.setLevel(Level.WARN)
          case "info" => logger.setLevel(Level.INFO)
          case "off" => logger.setLevel(Level.OFF)
          case _ => printOptions()
        }
      }
      val cmdType = arg.head match {
        case "process-kafka-stream" => ProcessKafkaStream.run()
        case "process-past-metrics-for-anomalies" => {
          val simulate = {
            if (arg.length > 1) {
              arg(1) match {
                case "simulate" => true
                case _ => false
              }
            } else false
          }
          Log.getLogger.trace(s"Going ahead with simulation = ${simulate.toString}")
          CreateRankingWindow.processPastMetricsHourly(simulate)
        }
        case "process-metrics-for-anomalies" => {
          if (arg.length > 2) {
            val (n, units) = {
              val dur = if (arg.length >= 3) arg(1).toInt else 0
              val unit = arg(2).toLowerCase match {
                case "minute" => "minute"
                case "hour" => "hour"
                case _ => "_"
              }
              (dur, unit)
            }
            val simulate = {
              if (arg.length >= 4) {
                arg(3).toLowerCase match {
                  case "simulate" => true
                  case _ => false
                }
              } else {
                false
              }
            }
            Log.getLogger.debug(s"got values dur = ${n} and units = ${units} and simulate = ${simulate.toString}")

            if (n > 0) {
              units.toLowerCase match {
                case "minute" => {
                  if (n < 60) {
                    CreateRankingWindow.processMetricsEveryNMinutes(n, simulate)
                  }
                }
                case "hour" => {
                  CreateRankingWindow.processMetricsEveryNHours(n, simulate)
                }
                case _ => printOptions()
              }
            }
          } else {
            printOptions()
          }
        }
        case "test-api-set" => DApi.run()
        case "update-kafka-for-agents" => UpdateNWDetailsOnKafka.run("1234abcd", "ec2-dc-01")
        case "test-spark-cassandra" => testCassandra()
        case "test-java-cassandra" => testScalaCassandra()
        case "test-kafka-producer" => testProducer()
        case "testjava" => com.infrared.entry.TryMeOut.run()
        case "ai" => correlate() //Predictions.correlate()
        case "nuvidata-engine" => ServiceInit.Init(null)
        case "testcache" => testcache()
        case _ => printOptions()
      }
    } else {
      printOptions()
    }
  }
}
