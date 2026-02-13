package com.infrared.entry.infra

import com.infrared.entry.anomaly.Detections
import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.collection.mutable.{HashMap, ListBuffer}
import scala.util.hashing.{MurmurHash3 => MH3}

/**
 * Created by prashun on 24/6/16.
 */

trait Group {
  def getCassandraHandle: CassDB = {
    val addressList = new ListBuffer[CassandraDB.Address]()
    addressList += new Address("127.0.0.1", 9042)
    CassandraDB.getScalaInstance("nuvidata", addressList.toList)
  }

  case class Marker(start : Long, end : Long, thermal : Int) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  def getThermalStats(thermalStructs : Array[(Long, Int, String)], cluster_replicated : Boolean) : (Int, Map[Int, Int]) = {
    var thermalCounts = HashMap[Int, Int]()
    thermalStructs.foreach(x => {
      val o = JacksonWrapper.deserialize[Map[String, Int]](x._3)
      o.foreach(x => {
        val key : Int = x._1.toInt
        val y = thermalCounts.getOrElseUpdate(key, 0)
        thermalCounts.remove(key)
        thermalCounts += (key -> (y + x._2))
      })
    })

    val quantized_thermal = Detections.Generic.quantizeThermals(thermalStructs.map(_._2), cluster_replicated)
    (quantized_thermal, thermalCounts.toMap)
  }

}

