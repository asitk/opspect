package com.infrared.entry.summarystats

import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.collection.immutable._
import scala.collection.mutable.ListBuffer
import scala.util.hashing.{MurmurHash3 => MH3}

/**
 * Created by prashun on 20/6/16.
 */
trait SummarizedStats {

  case class SummaryStats(ts: Long, moh: Int, customer_id: String, deployment_id: String, cluster_id: String,
                          host_ip: String, host_name: String,
                          thermal : Int, thermal_stats : String, thermal_reason: String, thermal_summary : String,
                          stats: String, connections: String, services: String) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  protected def getCassandraHandle: CassDB = {
    val addressList = new ListBuffer[CassandraDB.Address]()
    addressList += new Address("127.0.0.1", 9042)
    CassandraDB.getScalaInstance("nuvidata", addressList.toList)
  }

  protected def getTimeToNearestHour(ts : Long) : Long = {
    val tsInSeconds : Long = ts/1000
    val tsInHours: Long = tsInSeconds / 3600
    val tsInMillis : Long = tsInHours * 3600 * 1000
    tsInMillis
  }


  def getLastTimeSlot: Long

  def getTimeSlots: List[Long]
  def updateTimeSlot(ssList : SummaryStats) : Boolean
}


