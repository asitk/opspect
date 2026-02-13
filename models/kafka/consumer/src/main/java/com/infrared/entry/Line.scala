package com.infrared.entry
import com.infrared.entry.rawrecord._
/**
 * Created by prashun on 12/5/16.
 */
object Line {
  def Parse(rawStr: String): RawRecord = {
    var recType: Int = 0 // Default

    var plugin: String = ""
    var classification: String = ""
    var target : String = "*"
    var host_ip: String = ""
    var host_name: String = ""
    var cluster_id : String = ""
    var deployment_id : String = ""
    var customer_id : String = ""
    var value: String = ""
    var ts: String = ""
    var tags = scala.collection.mutable.HashMap[String, String]()
    var hinttagv: String = ""

    val valKey = "value="
    val index = rawStr.indexOf(valKey)
    var valStr = rawStr.slice(index + valKey.length ,rawStr.length)
    val tokStr = rawStr.slice(0, index)

    val segment : Array[String] = tokStr.split(",")
    segment.foreach(x => {
      val kv:Array[String] = x.split("=")
      if (kv(0) == "plugin") {
        val pc:Array[String] = kv(1).split("\\.")
        plugin = pc(0)
        classification = pc(1)
      }
      else if (kv(0) == "cluster_id") { cluster_id = kv(1)}
      else if (kv(0) == "deployment_id") { deployment_id = kv(1)}
      else if (kv(0) == "customer_id") { customer_id = kv(1)}
      else if (kv(0) == "ts") {
        ts = kv(1)

      }
      else if (kv(0) == "host")  {
        val h = kv(1).split(":")
        host_name = h(0)
        host_ip = h(1)
      }
      else if (kv(0) == "logs") { hinttagv = kv(1) }
      else {
        tags += (kv(0) -> kv(1))
      }
    })

    value = {
      val last = {
        if (valStr.endsWith("i")) {
          valStr.length - 1
        } else {
          valStr.length
        }
      }

      try {
        valStr.slice(0, last).toDouble.toString
      } catch {
        case e : Exception => valStr.stripPrefix("\"").stripSuffix("\"")
      }
    }

    if (plugin == "procstat") {
      target = tags.getOrElse("svc_name","unknown")
    }

    if (plugin == "nwgraph") {
      target = tags.getOrElse("binding", "0")
    }

    val tmpts = tags.getOrElse("replace_ts", null)
    if (tmpts != null) {
      ts = tmpts
    }

    val orig_time_in_ms: Long = ts.toString.toLong
    val tsInSeconds: Long = ts.toString.toLong / 1000
    val tsInMinutes: Long = tsInSeconds / 60
    val tsInMillis: Long = tsInMinutes * 60 * 1000
    ts = tsInMillis.toString

    // Record Type
    if (plugin == "logs") {
      recType = RawRecordType.LOG
    } else recType = RawRecordType.SYSTEM

    new RawRecord(recType, plugin, classification, target, host_ip, host_name, cluster_id, deployment_id, customer_id,
      value, ts, orig_time_in_ms, tags, hinttagv)
  }
}

