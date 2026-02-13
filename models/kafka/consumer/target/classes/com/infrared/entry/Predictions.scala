package com.infrared.entry

import breeze.linalg._
import com.infrared.entry.Metrics._
import com.infrared.entry.SummaryState._
import com.infrared.entry.anomaly.Anomaly._
import com.infrared.entry.anomaly.Detections
import com.infrared.entry.summarystats._
import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.collection.mutable.ListBuffer
/**
 * Created by prashun on 3/8/16.
 */
object Predictions {
  case class Key(node : MetricsStats, prev_thermal : Int, current_thermal : Int) extends Serializable {
    override def toString(): String = {
      JacksonWrapper.serialize(this)
    }
  }

  val rgrid: Map[Int, Int] = getSevGrades.map(x => {
    (x.grade -> getSevGrades.indexOf(x))
  }).toMap

  def getRowCol(a: Int, b: Int): (Int, Int) = {
    val r = rgrid.getOrElse(a, -1)
    val size = getSevGrades.length
    val offset = r * size
    val c = (rgrid.getOrElse(b, -1) + offset) % size
    //println(s"For (a,b) = ${(a,b)} got (r,c) = ${(r,c)}")
    (r, c)
  }

  def correlate(): Unit = {
    CassandraDB.start()
    //Expt aand then parameterize
    val customer_id = "1234abcd"
    val deployment_id = "ec2-dc-01"
    val cluster_id = "myappcluster"
    val host_ip = "192.168.6.21"
    val time_range = new TimeRange(1466331323000L, 1475231400000L)
    val cassdb = getCassandraHandle
    val ssh = new SummaryStatsHourly(customer_id, deployment_id)
    val query = ssh.prepareSnapshotQuery("*", "*", time_range)
    val resultSet = cassdb.executeQuery(query)
    val thermalStructs: Array[(Long, Int, String, String, String)] = {
      if (resultSet != null) {
        resultSet.asScala.map(row => {
          (row.getTimestamp("ts").getTime, row.getInt("thermal"), row.getString("thermal_stats"),
            row.getString("stats"), row.getString("connections"))
        }).toArray
      } else {
        Array()
      }
    }
    cassdb.close()

    thermalStructs.foreach(x => {
      val ts = x._1
      var thermal = x._2
      val thermal_stats = JacksonWrapper.deserialize[Map[Int,Int]](x._3)
      val stats = JacksonWrapper.deserialize[List[Stats]](x._4)
      val connections = JacksonWrapper.deserialize[List[NWObject.NWGraph]](x._5)
      var intermediates = List[CORRFactor]()
      var wsList = List[Map[Int, Array[WindowScore]]]()
      val start_ts: Long = ts
      val end_ts = start_ts + 3600000
      println(s"Now in thermal structs loop =${stats.length}")
      stats.foreach(x => {
        val ts = new TimeRange(start_ts, end_ts)
        val ws = x.ws
        wsList ::= ws
        val hmList = Detections.Generic.getHeatMapWithScoreFromWindowScore(ws)
        val corr_factor = getMultiVariateParams(hmList, new TimeRange(start_ts, end_ts))
        intermediates ::= corr_factor
        println(s"The probability = \n${corr_factor.probability.toString}")
        println(s"The coefficient = \n${corr_factor.coefficient.toString}")
      })
      if (wsList.nonEmpty) {
        val final_ws = Detections.Generic.getResultantWindowScore(wsList)
        val final_hmList = Detections.Generic.getHeatMapWithScoreFromWindowScore(final_ws)
        val start_ts = final_hmList.head.startTime
        val end_ts = start_ts + 3600000
        val corr_factor = getMultiVariateParams(final_hmList, new TimeRange(start_ts, end_ts))
        println(s"The probability = \n${corr_factor.probability.toString}")
        println(s"The coefficient = \n${corr_factor.coefficient.toString}")
        solveMultiVariateCorrelation(intermediates, corr_factor, thermal)
      }
    })


    CassandraDB.shutdown()
  }

  case class CORRFactor(probability: DenseMatrix[Double], coefficient: DenseMatrix[Double]) extends Serializable {
  }

  def rankDependencies(thermal: Int, stats: List[Stats], time_range: TimeRange): List[Stats] = {
    if (stats.isEmpty) {
      return stats
    }

    var wsList = List[Map[Int, Array[WindowScore]]]()
    var intermediates = List[(Stats,CORRFactor)]()
    stats.foreach(x => {
      wsList ::= x.ws
      val hmList = Detections.Generic.getHeatMapWithScoreFromWindowScore(x.ws)
      val corr_factor = getMultiVariateParams(hmList, time_range)
      intermediates ::=(x, corr_factor)
    })

    val final_corr_factor = {
      val final_ws = Detections.Generic.getResultantWindowScore(wsList)
      val final_hmList = Detections.Generic.getHeatMapWithScoreFromWindowScore(final_ws)
      getMultiVariateParams(final_hmList, time_range)
    }
    val final_mat: DenseMatrix[Double] = (final_corr_factor.coefficient :* final_corr_factor.probability)

    val (r, c) = getRowCol(thermal, thermal)
    val inter = intermediates.filter(x => {
      val m: DenseMatrix[Double] = (x._2.coefficient :* x._2.probability)
      (m(0, c) != 0.0)
    })
      .sortWith((a, b) => {
        val mat1: Double = {
          val m: DenseMatrix[Double] = (a._2.coefficient :* a._2.probability)
          m(0, c)
        }
        val mat2: Double = {
          val m: DenseMatrix[Double] = (b._2.coefficient :* b._2.probability)
          m(0, c)
        }
        mat1 > mat2
      })
    val constant_coeff: DenseMatrix[Double] = {
      var sum: DenseMatrix[Double] = DenseMatrix.zeros(1, getSevGrades.length)
      inter.foreach(x => {
        sum :+= (x._2.coefficient * x._2.probability)
      })
      final_mat - sum
    }
    var rankedStats = List[Stats]()
    for (i <- inter.indices) {
      val s = inter(i)._1
      val rank = i + 1
      val inter_mat: DenseMatrix[Double] = (inter(i)._2.coefficient :* inter(i)._2.probability)
      val share = inter_mat(0, c) / (final_mat(0, c) - constant_coeff(0, c))
      val contrib = new Contribution(rank, share)
      rankedStats ::= new Stats(s.customer_id, s.deployment_id, s.cluster_id, s.host_ip, s.host_name, s.plugin, s.target, s.classification,
        s.thermal, s.anomaly_class, s.anomaly_type, contrib, s.thermal_reason, s.ws, s.tags)
    }
    rankedStats = rankedStats.sortWith(_.contribution.rank < _.contribution.rank)
    rankedStats
  }

  def solveMultiVariateCorrelation(intermediates: List[CORRFactor], finalCorr: CORRFactor, thermal: Int): Unit = {
    var sum_prod: DenseMatrix[Double] = DenseMatrix.zeros(1, getSevGrades.length)
    var int_prod = List[(Int, DenseMatrix[Double])]()
    var count: Int = 0

    intermediates.foreach(x => {
      val ip = x.coefficient :* x.probability
      int_prod ::=(count, ip)
      count += 1
      sum_prod += ip
    })
    println(s"sum_prod =\n${sum_prod.toString}")

    val final_prod: DenseMatrix[Double] = finalCorr.coefficient :* finalCorr.probability
    println(s"The final prod = \n${final_prod.toString}")
    println(s"The sum prod = \n${sum_prod.toString}")
    val constant_matrix = {
      var coeff = finalCorr.coefficient
      getSevGrades.foreach(x => {
        val (r, c) = getRowCol(x.grade, x.grade)
        if (coeff(0, c) == 0.0) {
          coeff(0, c) = 0.000000001
        }
      })
      (final_prod - sum_prod) :/ coeff
    }
    println(s"The constant_matrix = \n${constant_matrix.toString}")
    println(s"The final coefficient_matrix = \n${finalCorr.coefficient.toString}")

    //Get the matrix line based on thermal
    int_prod.sortWith((x, y) => {
      val (r, c) = getRowCol(thermal, thermal)
      val mat1 = x._2
      val mat2 = y._2
      mat1(0, c) > mat2(0, c)
    }).foreach(println)
  }

  def getCassandraHandle: CassDB = {
    val addressList = new ListBuffer[CassandraDB.Address]()
    addressList += new Address("127.0.0.1", 9042)
    CassandraDB.getScalaInstance("nuvidata", addressList.toList)
  }

  def getMultiVariateParams(hmList: List[HeatMapDetailWithScore], time_range: TimeRange): CORRFactor = {

    val rows = getSevGrades.length
    val cols = rows
    var probability_matrix: DenseMatrix[Double] = DenseMatrix.zeros(rows, cols)
    var coefficient_matrix: DenseMatrix[Double] = DenseMatrix.zeros(1, cols)
    var freq_matrix: DenseMatrix[Double] = DenseMatrix.zeros(1, cols)

    var total  = 0l
    val last_thermal_bucket = getSevGrades.last.grade
    var prev_thermal = last_thermal_bucket
    var prev_ts = time_range.start
    var current_thermal =  0
    var current_ts = 0l
    var count = 0l

    hmList.foreach(x => {
      current_thermal = x.scoreType
      current_ts = x.startTime
      //println(s"prev_thermal = ${prev_thermal} and current_thermal = ${current_thermal}")
      //println(s"prev_ts = ${prev_ts} and current_ts = ${current_ts} current_duration = ${x.duration}")
      //println(s"count = ${(current_ts - prev_ts)/(60*1000)}")

      if (true) {
        val (r, c) = getRowCol(current_thermal, current_thermal)
        coefficient_matrix(0, c) += x.score
        freq_matrix(0, c) += 1
      }

      //First update the previous thermal
      count = (current_ts - prev_ts) / (60 * 1000)
      if (count > 0) {
        val (r, c) = getRowCol(prev_thermal, prev_thermal)
        probability_matrix(r, c) += count
        //println(s"count = ${count}")
        total += count
      }

      //Transition change
      if (true) {
        val (r, c) = getRowCol(prev_thermal, current_thermal)
        probability_matrix(r, c) += 1
        //println(s"count = 1")
        total += 1
      }

      count = x.duration
      if (count > 0) {
        //Now update the current thermal
        val (r, c) = getRowCol(current_thermal, current_thermal)
        probability_matrix(r, c) += x.duration
        //println(s"count = ${count}")
        total += count
      }

      prev_thermal = current_thermal
      prev_ts = current_ts + ((x.duration) * (60 * 1000))
    })

    count = (time_range.end - prev_ts) / (60 * 1000)
    //Transition change
    if (count > 0) {
      val (r, c) = getRowCol(prev_thermal, last_thermal_bucket)
      probability_matrix(r, c) += 1
      total += 1
      //println(s"count = 1")
    }

    //println(s"2 count = ${count}")
    if (count > 0) {
      val (r, c) = getRowCol(last_thermal_bucket, last_thermal_bucket)
      probability_matrix(r, c) += count
      coefficient_matrix(0, c) += 1
      freq_matrix(0, c) += 1
      //println(s"count = ${count}")
      total += count
    }

    getSevGrades.foreach(x => {
      val (r, c) = getRowCol(x.grade, x.grade)
      if (freq_matrix(0, c) == 0.0) {
        freq_matrix(0, c) = 0.00000000000001
      }
    })
    //println(s"Total = ${total}")
    //println(s"${probability_matrix.toString}")
    //println(s"coefficient_matrix = \n${coefficient_matrix.toString}")
    //println(s"freq_matrix = \n${freq_matrix.toString}")
    coefficient_matrix :/= freq_matrix
    //This is the probability matrix
    probability_matrix :/= total * 1.0d
    new CORRFactor(probability_matrix, coefficient_matrix)
  }
}
