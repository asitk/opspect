package com.opspect.entry

import breeze.stats._
import com.opspect.entry.Metrics.DStats
import com.opspect.util.{JacksonWrapper, Log}

import scala.collection.immutable._

/** Created by prashun on 3/9/16.
  */
object Stats {

  def findDStats(x: Array[Double]): Metrics.DStats = {
    val (peer, fpMap) = findPeerMean(x)
    new DStats(
      x.length,
      x.min,
      x.max,
      peer.mean,
      peer.stdDev,
      fpMap.map(_.length)
    )
  }

  def findPeerMean(x: Array[Double]): (MeanAndVariance, Array[List[Double]]) = {
    var doublePoi: Vector[Double] = for (a <- x.toVector) yield a
    var found = false
    var ntries = 2
    var peer: MeanAndVariance = null
    do {
      peer = breeze.stats.meanAndVariance(doublePoi)
      Log.getLogger.debug(
        s"FP Means are ${JacksonWrapper.serialize(peer)} and ntries = ${ntries}"
      )
      val fdVal = findFPMap(x, peer)
      Log.getLogger.debug(s"FdVal =  ${JacksonWrapper.serialize(fdVal)}")
      val max_bucket = fdVal.indexOf(fdVal.maxBy(_.length))
      // Distribution is close and balanced
      if (List(2, 3).contains(max_bucket)) {
        found = true
      } else {
        doublePoi = fdVal(max_bucket).toVector
      }
      ntries -= 1
    } while (!found && ntries > 0)
    (peer, findFPMap(x, peer))
  }

  def findFPMap(
      x: Array[Double],
      peer: MeanAndVariance
  ): Array[List[Double]] = {
    val fdVal = new Array[List[Int]](6).map(x => {
      List[Double]()
    })
    var doublePoi: Vector[Double] = for (a <- x.toVector) yield a
    for (x <- doublePoi) {
      val index: Int = {
        if (x == peer.mean && peer.stdDev == 0) {
          2
        } else if (x < peer.mean - 2 * peer.stdDev) {
          0
        } else if (
          Metrics.isInRange(
            x,
            peer.mean - 2 * peer.stdDev,
            peer.mean - peer.stdDev
          )
        ) {
          1
        } else if (Metrics.isInRange(x, peer.mean - peer.stdDev, peer.mean)) {
          2
        } else if (Metrics.isInRange(x, peer.mean, peer.mean + peer.stdDev)) {
          3
        } else if (
          Metrics.isInRange(
            x,
            peer.mean + peer.stdDev,
            peer.mean + 2 * peer.stdDev
          )
        ) {
          4
        } else if (x >= peer.mean + 2 * peer.stdDev) {
          5
        } else {
          // Should throw exception as its not possible to come here
          -1
        }
      }

      fdVal(index) ::= x
    }
    fdVal
  }
}
