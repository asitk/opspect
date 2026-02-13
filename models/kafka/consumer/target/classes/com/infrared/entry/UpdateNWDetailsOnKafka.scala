package com.infrared.entry

import java.util.Calendar

import com.infrared.entry.infra.Node
import com.infrared.injest.HighLevelProducer
import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.collection.immutable._
import scala.collection.mutable.HashMap

/**
 * Created by prashun on 6/7/16.
 */
object UpdateNWDetailsOnKafka {

  case class NWMessage(nodeDetails: Map[String, NWDetails], nwSvcInfoList: List[NWObject.NWSvcInfo]) extends Serializable {
    override def toString():String = {
      JacksonWrapper.serialize(this)
    }
  }
  case class NWDetails(customer_id : String, deployment_id : String, cluster_id : String) extends Serializable {
    override def toString():String = {
      JacksonWrapper.serialize(this)
    }
  }
  def run(customer_id : String, deployment_id : String) : Unit = {
    val t2 = {
      val timestamp = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).getTimeInMillis
      val tsInSeconds: Long = timestamp / 1000
      val tsInMinutes: Long = tsInSeconds / 60
      val tsInMillis: Long = tsInMinutes * 60 * 1000
      tsInMillis
    }
    val t1 = t2 - (5*60*1000)
    val tr = new TimeRange(t1, t2)
    CassandraDB.start
    var nodeDetails  = HashMap[String, NWDetails]()

    Node.getMaps(Some(customer_id), Some(deployment_id), None, None, Some(true), Some(tr))
    .foreach(x => {
      nodeDetails += (x.host_ip -> new NWDetails(x.customer_id, x.deployment_id, x.cluster_id))
    })

    Log.getLogger.trace("Calling getNWServices")
    val h = NWObject.getNWServices(customer_id, deployment_id, tr)
    var nwSvcInfoList = List[NWObject.NWSvcInfo]()
    h.foreach(x => {
      nwSvcInfoList :::= x.nw_svc_info_list
    })

    val nwMessage = new NWMessage(nodeDetails.toMap, nwSvcInfoList)

    val message = JacksonWrapper.serialize(nwMessage)
    val key = "nwmessage"
    Log.getLogger.debug(s"Writing message to meta queue = ${message}")
    HighLevelProducer.send("meta", key.getBytes(), message.getBytes())

    CassandraDB.shutdown()
  }

}
