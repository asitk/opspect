package com.opspect.entry

import com.opspect.entry.NWObject._
import com.opspect.entry.Tokenize._
import com.opspect.entry.infra._
import com.opspect.util._

import scala.collection.mutable.HashMap

/** Created by prashun on 3/9/16.
  */
object NWSvcMetrics {

  case class NWSvcStatsCounter(
      ts: Long,
      to: Node.NodeMap,
      svc_info: NWSvcInfo,
      avg_rsp_size_stats: Metrics.DStats,
      avg_ttfb_stats: Metrics.DStats,
      avg_ttlb_stats: Metrics.DStats,
      max_ttfb_stats: Metrics.DStats,
      max_ttlb_stats: Metrics.DStats,
      recv_bytes_stats: Metrics.DStats,
      sent_bytes_stats: Metrics.DStats,
      request_count_stats: Metrics.DStats,
      error_count_stats: Metrics.DStats,
      duration_stats: Metrics.DStats,
      is_modified: Boolean
  ) extends Serializable {
    override def toString(): String = {
      JacksonWrapper.serialize(this)
    }
  }

  def updateRequestStats(
      xl: List[RequestStat],
      rsl: List[RequestStat]
  ): List[RequestStat] = {
    var frsl: List[RequestStat] = null
    xl.foreach(x => {
      frsl = rsl.map(y => {
        val (final_str, status) = findStringTemplate(y.req, x.req)
        if (status) {
          new RequestStat(
            y.req,
            y.count + x.count,
            (y.count * y.ttfb + x.count * x.ttfb) / (y.count + x.count),
            (y.count * y.ttlb + x.count * x.ttfb) / (y.count + x.count)
          )
        } else {
          y
        }
      })
    })
    frsl
  }

}
