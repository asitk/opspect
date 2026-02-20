package com.opspect.entry.rawrecord

import com.google.gson._
import com.opspect.util.{JacksonWrapper, TCPClient, _}
import org.apache.commons.lang.StringEscapeUtils

import scala.collection.mutable
import scala.collection.mutable.HashMap
import scala.util.hashing.{MurmurHash3 => MH3}

/** Created by prashun on 12/5/16.
  */

object RawRecordType {
  val LOG: Int = 0
  val SYSTEM: Int = 1
}

case class RawRecord(
    rectype: Int,
    plugin: String,
    classification: String,
    target: String,
    host_ip: String,
    host_name: String,
    cluster_id: String,
    deployment_id: String,
    customer_id: String,
    value: String,
    ts: String,
    orig_ts: Long,
    tags: scala.collection.mutable.HashMap[String, String],
    hinttagv: String
) extends Serializable {
  // val httpClient:HttpClientSingleton = HttpClientSingleton.getInstance()

  override def toString: String = {
    JacksonWrapper.serialize(this)
  }

  def is_non_numeric(): Boolean = {
    try {
      value.toDouble
      false
    } catch {
      case e: Exception => true
    }
  }

  def valtodouble(): Double = {
    var r: Double = {
      try {
        value.toDouble
      } catch {
        case e: Exception => 0
      }
    }
    r
  }

  /*
     OpenTSDB Format
     {
      "metric": "sys.cpu.nice",
      "timestamp": 1346846400,
      "value": 18,
      "tags": {
        "host": "web01",
        "dc": "lga"
      }
} */

  def getDeltaValue(r1: RawRecord, n: Int): (Int, HashMap[String, Any]) = {
    val r2: RawRecord = this
    val dataTypes = Array[String]("int", "float", "double", "long", "short")
    val deltaValueType = s"delta_${n}_value"
    val deltaValueTimeType = s"delta_${n}_time"
    var hm = mutable.HashMap[String, Any]()

    val (value1, value2) = {
      if (n == 0) {
        (r1.value, r2.value)
      } else {
        (
          r1.tags.getOrElse(deltaValueType, null),
          r2.tags.getOrElse(deltaValueType, null)
        )
      }
    }

    if (value1 != null && value2 != null) {
      val vdelta = {
        try {
          val diff = (value1.toDouble - value2.toDouble)
          if (diff > 0.0 && value2.toDouble != 0.0) {
            diff / value2.toDouble
          } else 0.0
        } catch {
          case e: Exception => 1.0d
        }
      }
      val tdelta = r1.orig_ts - r2.orig_ts
      hm += (deltaValueType -> vdelta)
      hm += (deltaValueTimeType -> tdelta.toString)
    }
    (n, hm)
  }

  def toOpenTSDBJsonString: String = {
    val jsonplugin: JsonObject = new JsonObject()
    jsonplugin.addProperty("metric", plugin + "." + classification)

    // elasticsearch 2.2 accepts date fields
    jsonplugin.addProperty("timestamp", ts.toLong)

    jsonplugin.addProperty("value", value)

    val taglist: JsonObject = new JsonObject()
    taglist.addProperty("customer_id", customer_id)
    taglist.addProperty("deployment_id", deployment_id)
    taglist.addProperty("cluster_id", cluster_id)
    taglist.addProperty("target", target)
    taglist.addProperty("host_name", host_name)
    taglist.addProperty("host_ip", host_ip)
    for ((k, v) <- tags) {
      taglist.addProperty(k, v)
    }
    jsonplugin.add("tags", taglist)
    jsonplugin.toString
  }

  // NODEIP NODENAME MESSAGE
  def toLogStashString: String = {
    var sbOrig: StringBuilder = new StringBuilder(value)
    var add: String = host_name + " " + host_ip + " "

    if (sbOrig.charAt(0).equals('"')) {
      sbOrig.insert(1, add)
    } else {
      sbOrig.insert(0, add)
    }

    StringEscapeUtils.unescapeJava(sbOrig.toString())
  }

  // put temperatures 1356998400 23.5 room=bedroom floor=1 \n
  def toOpenTSDBTelnetString: String = {
    var str: String = new String("put ")

    str += plugin + "." + classification + " "
    str += ts + " "
    str += value + " "
    str += "customer_id=" + customer_id + " "
    str += "deployment_id=" + deployment_id + " "
    str += "cluster_id=" + cluster_id + " "
    str += "target=" + target + " "
    str += "host_name=" + host_name + " "
    str += "host_ip=" + host_ip + " "
    for ((k, v) <- tags) {
      str += k + "=" + v + " "
    }

    Log.getLogger.debug("TELNET: " + str)

    str = str + "\n"

    str
  }

  def toElasticString: Tuple2[String, String] = {
    var docIndex, docType, docHintTag, docjson: String = ""

    docIndex = plugin
    docType = classification

    if (rectype == RawRecordType.LOG) {
      docHintTag = hinttagv
    }

    // Build docjson
    val jsonplugin: JsonObject = new JsonObject()

    // Using @timestamp to match up with logstash
    jsonplugin.addProperty("@timestamp", ts.toLong)

    jsonplugin.addProperty("text", value)
    docjson = jsonplugin.toString
    docType += "_" + docHintTag

    (docIndex + "/" + docType, docjson)
  }

  // Write points to openTSDB and Elastic
  def write(): Unit = {
    // Handle Log
    /** if (rectype == RawRecordType.LOG) { val (url, text) = toElasticString()
      * var url_prefix = "http://localhost:9200/" url_prefix = url_prefix + url
      *
      * System.out.println("URL: " + url_prefix + " Text: " + text)
      * httpClient.Post(url_prefix, text) return }
      *
      * // Handle Procstat if (rectype == RawRecordType.SYSTEM && classification ==
      * "cmd") { System.out.println(toOpenTSDBTelnetString()) return }
      *
      * // Handle other metrics
      *
      * //System.out.println(toJsonString)
      * //httpClient.Put("http://localhost:4242/api/put", toJsonString)
      * //System.out.println(toTelnetString())
      */
    val sc = new TCPClient

    // Kyrosdb
    sc.Send(toOpenTSDBTelnetString, "localhost", 4141)

    // OpenTSDB
    // sc.Send(toOpenTSDBTelnetString(), "localhost", 4242)

  }
}
