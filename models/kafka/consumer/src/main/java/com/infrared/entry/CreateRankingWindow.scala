package com.infrared.entry


/**
 * Created by prashun on 30/5/16.
 */

import java.util.Calendar

import com.infrared.entry.Metrics._
import com.infrared.entry.anomaly._
import com.infrared.entry.infra.{Cluster, Customer, Deployment, Node}
import com.infrared.entry.summarystats._
import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.util.hashing.{MurmurHash3 => MH3}

object CreateRankingWindow {
  def processMetricsEveryNMinutes(n: Int, simulate: Boolean): Unit = {
    Log.getLogger.trace("Calling getCustomerIds")
    //Ideally this is called for every past hour for which it has not been computed.
    //Find oldest timestamp in metrics_stats tbl and look at the time now and find out
    //how many hours between them and now and create a list of hours - 1 timestamps
    //Then remove from that list entries for which there are already entries in summary_stats
    val active = Some(true)
    val grain = n * 60 * 1000
    Anomaly.setSimulationMode(simulate)
    val customer_id = None
    var next_time_slot = 0l
    var nw_details_update_time = 0l
    CassandraDB.start

    do {
      Log.getLogger.trace("Loop started")
      val (start, behindInMinutes) = getEarliesMinuteTimestampInMillis(n)
      if (next_time_slot != 0) {
        val diff: Long = start - next_time_slot
        if (diff > 0l) {
          for (i <- 0 until (diff / grain).toInt) {
            Log.getLogger.trace(s"Skipping time_slot = ${next_time_slot + i * grain}")
          }
        }
      }
      next_time_slot = start + grain
      val sleep_for_n_ms = (start + (behindInMinutes * 60 * 1000) - getUTCTimeNowInMillis())
      Log.getLogger.trace(s"The start time to process = ${start} and going to sleep for ${sleep_for_n_ms}\n")

      Thread.sleep(sleep_for_n_ms)
      val tr = new TimeRange(start, start + grain)
      val ts = tr.start
      val customerMap = Customer.getMaps(customer_id, active, None)
      customerMap.foreach(cm => {
        Log.getLogger.trace(s"Got customermap ${cm}")
        val deploymentMap = Deployment.getMaps(Some(cm.customer_id), None, active, None)
        deploymentMap.foreach(dm => {
          Log.getLogger.trace(s"Got deploymentmap ${dm}")
          val (detections, connections) = SummaryState.writeAnomaliesToDB(dm.customer_id, dm.deployment_id, tr, n)
          Log.getLogger.trace(s"Computed all anomalies and connection summary for the deployment")
          val ssh = new SummaryStatsMinute(dm.customer_id, dm.deployment_id)
          val clusterMap = Cluster.getMaps(Some(dm.customer_id), Some(dm.deployment_id), None, active, Some(tr))
          clusterMap.foreach(clm => {
            Log.getLogger.trace(s"Got clustermap ${clm}")
            val nodeMap = Node.getMaps(Some(clm.customer_id), Some(clm.deployment_id), Some(clm.cluster_id), None, active, Some(tr))
            nodeMap.foreach(nm => {
              Log.getLogger.trace(s"Got nodemap ${nm}")
              //Stats for each node in a cluster
              var ss: ssh.SummaryStats = null
              Log.getLogger.trace("Starting of node snapshot")
              val sn = SummaryState.getNodeSnapshot(nm.customer_id, nm.deployment_id, nm.cluster_id, nm.host_ip, tr, detections, connections)
              Log.getLogger.trace("Ending of node snapshot")
              if (sn != null) {
                ss = new ssh.SummaryStats(ts, getMinuteOfHour(ts), nm.customer_id, nm.deployment_id, nm.cluster_id, nm.host_ip, nm.host_name, sn.thermal.summary.severity,
                  JacksonWrapper.serialize(sn.thermal.critInfo), JacksonWrapper.serialize(sn.thermal.summary),
                  JacksonWrapper.serialize(sn.thermal.detail), JacksonWrapper.serialize(sn.stats),
                  JacksonWrapper.serialize(sn.connections), JacksonWrapper.serialize(sn.services))

                Log.getLogger.trace("DB Inserting of node snapshot")
                ssh.updateTimeSlot(ss)
                Log.getLogger.trace("Ending DB inserting of node snapshot")
                Log.getLogger.trace(JacksonWrapper.serialize(sn))
              } else {
                Log.getLogger.trace("Got null for node snapshot")
              }
            })

            //Stats for each cluster
            var ss: ssh.SummaryStats = null
            Log.getLogger.trace("Starting of cluster snapshot")
            val sn = SummaryState.getClusterSnapshot(clm.customer_id, clm.deployment_id, clm.cluster_id, tr, detections, connections)
            Log.getLogger.trace("Ending of cluster snapshot")
            if (sn != null) {
              ss = new ssh.SummaryStats(ts, getMinuteOfHour(ts), clm.customer_id, clm.deployment_id, clm.cluster_id, "255.255.255.255", "*", sn.thermal.summary.severity,
                JacksonWrapper.serialize(sn.thermal.critInfo), JacksonWrapper.serialize(sn.thermal.summary),
                JacksonWrapper.serialize(sn.thermal.detail), JacksonWrapper.serialize(sn.stats),
                JacksonWrapper.serialize(sn.connections), JacksonWrapper.serialize(sn.services))

              Log.getLogger.trace("DB Inserting of cluster snapshot")
              ssh.updateTimeSlot(ss)
              Log.getLogger.trace("Ending DB inserting of cluster snapshot")
              Log.getLogger.trace(JacksonWrapper.serialize(sn))
            } else {
              Log.getLogger.trace("Got null for cluster snapshot")
            }
          })

          //Stats for each deployment
          var ss: ssh.SummaryStats = null
          Log.getLogger.trace("Starting of deployment snapshot")
          val sn = SummaryState.getDeploymentSnapshot(dm.customer_id, dm.deployment_id, tr, detections, connections)
          Log.getLogger.trace("Ending of deployment snapshot")
          if (sn != null) {
            ss = new ssh.SummaryStats(ts, getMinuteOfHour(ts), dm.customer_id, dm.deployment_id, "*", "255.255.255.255", "*", sn.thermal.summary.severity,
              JacksonWrapper.serialize(sn.thermal.critInfo), JacksonWrapper.serialize(sn.thermal.summary),
              JacksonWrapper.serialize(sn.thermal.detail), JacksonWrapper.serialize(sn.stats),
              JacksonWrapper.serialize(sn.connections), JacksonWrapper.serialize(sn.services))

            Log.getLogger.trace("DB Inserting of deployment snapshot")
            ssh.updateTimeSlot(ss)
            Log.getLogger.trace("Ending DB inserting of deployment snapshot")
            Log.getLogger.trace(JacksonWrapper.serialize(sn))
          } else {
            Log.getLogger.trace("Got null for deployment snapshot")
          }



          if (ts >= nw_details_update_time) {
            Log.getLogger.trace("Starting update of kafka queue meta with NW SVC Activities")
            UpdateNWDetailsOnKafka.run(dm.customer_id, dm.deployment_id)
            Log.getLogger.trace("Ending update of kafka queue meta with NW SVC Activities")
            //Every 5 minutes or after
            nw_details_update_time = ts + 5 * 60 * 1000
          }
        })
      })
    } while (true)
    CassandraDB.shutdown
  }

  def processMetricsEveryNHours(n: Int, simulate: Boolean): Unit = {
    Log.getLogger.trace("Calling getCustomerIds")
    //Ideally this is called for every past hour for which it has not been computed.
    //Find oldest timestamp in metrics_stats tbl and look at the time now and find out
    //how many hours between them and now and create a list of hours - 1 timestamps
    //Then remove from that list entries for which there are already entries in summary_stats
    val active = Some(true)
    val grain = n * 3600 * 1000
    Anomaly.setSimulationMode(simulate)
    val customer_id = None
    var next_time_slot = 0l
    CassandraDB.start

    do {
      Log.getLogger.trace("Loop started")
      val (start, behindInHours) = getEarliesHourTimestampInMillis(n)
      if (next_time_slot != 0) {
        val diff: Long = start - next_time_slot
        if (diff > 0l) {
          for (i <- 0 until (diff / grain).toInt) {
            Log.getLogger.trace(s"Skipping time_slot = ${next_time_slot + i * grain}")
          }
        }
      }
      next_time_slot = start + grain
      val sleep_for_n_ms = (start + (behindInHours * 3600 * 1000) - getUTCTimeNowInMillis())
      Log.getLogger.trace(s"The start time to process = ${start} and going to sleep for ${sleep_for_n_ms}\n")

      Thread.sleep(sleep_for_n_ms)
      val tr = new TimeRange(start, start + grain)
      val ts = tr.start
      val customerMap = Customer.getMaps(customer_id, active, None)
      customerMap.foreach(cm => {
        Log.getLogger.trace(s"Got customermap ${cm}")
        val deploymentMap = Deployment.getMaps(Some(cm.customer_id), None, active, None)
        deploymentMap.foreach(dm => {
          Log.getLogger.trace(s"Got deploymentmap ${dm}")
          val (detections, connections) = SummaryState.getAnomaliesByGrain(dm.customer_id, dm.deployment_id, None, None, tr, n * 60)
          val ssh = new SummaryStatsHourly(dm.customer_id, dm.deployment_id)
          val clusterMap = Cluster.getMaps(Some(dm.customer_id), Some(dm.deployment_id), None, active, Some(tr))
          clusterMap.foreach(clm => {
            Log.getLogger.trace(s"Got clustermap ${clm}")
            val nodeMap = Node.getMaps(Some(clm.customer_id), Some(clm.deployment_id), Some(clm.cluster_id), None, active, Some(tr))
            nodeMap.foreach(nm => {
              Log.getLogger.trace(s"Got nodemap ${nm}")
              //Stats for each node in a cluster
              var ss: ssh.SummaryStats = null
              val sn = SummaryState.getNodeSnapshot(nm.customer_id, nm.deployment_id, nm.cluster_id, nm.host_ip, tr, detections, connections)
              if (sn != null) {
                ss = new ssh.SummaryStats(ts, getMinuteOfHour(ts), nm.customer_id, nm.deployment_id, nm.cluster_id, nm.host_ip, nm.host_name, sn.thermal.summary.severity,
                  JacksonWrapper.serialize(sn.thermal.critInfo), JacksonWrapper.serialize(sn.thermal.summary),
                  JacksonWrapper.serialize(sn.thermal.detail), JacksonWrapper.serialize(sn.stats),
                  JacksonWrapper.serialize(sn.connections), JacksonWrapper.serialize(sn.services))

                ssh.updateTimeSlot(ss)
                Log.getLogger.trace(JacksonWrapper.serialize(sn))
              } else {
                Log.getLogger.trace("Got null for node snapshot")
              }
            })

            //Stats for each cluster
            var ss: ssh.SummaryStats = null
            val sn = SummaryState.getClusterSnapshot(clm.customer_id, clm.deployment_id, clm.cluster_id, tr, detections, connections)
            if (sn != null) {
              ss = new ssh.SummaryStats(ts, getMinuteOfHour(ts), clm.customer_id, clm.deployment_id, clm.cluster_id, "255.255.255.255", "*", sn.thermal.summary.severity,
                JacksonWrapper.serialize(sn.thermal.critInfo), JacksonWrapper.serialize(sn.thermal.summary),
                JacksonWrapper.serialize(sn.thermal.detail), JacksonWrapper.serialize(sn.stats),
                JacksonWrapper.serialize(sn.connections), JacksonWrapper.serialize(sn.services))

              ssh.updateTimeSlot(ss)
              Log.getLogger.trace(JacksonWrapper.serialize(sn))
            } else {
              Log.getLogger.trace("Got null for cluster snapshot")
            }
          })

          //Stats for each deployment
          var ss: ssh.SummaryStats = null
          val sn = SummaryState.getDeploymentSnapshot(dm.customer_id, dm.deployment_id, tr, detections, connections)
          if (sn != null) {
            ss = new ssh.SummaryStats(ts, getMinuteOfHour(ts), dm.customer_id, dm.deployment_id, "*", "255.255.255.255", "*", sn.thermal.summary.severity,
              JacksonWrapper.serialize(sn.thermal.critInfo), JacksonWrapper.serialize(sn.thermal.summary),
              JacksonWrapper.serialize(sn.thermal.detail), JacksonWrapper.serialize(sn.stats),
              JacksonWrapper.serialize(sn.connections), JacksonWrapper.serialize(sn.services))

            ssh.updateTimeSlot(ss)
            Log.getLogger.trace(JacksonWrapper.serialize(sn))
          } else {
            Log.getLogger.trace("Got null for deployment snapshot")
          }
        })
      })
    } while (true)
    CassandraDB.shutdown
  }

  def processPastMetricsHourly(simulate: Boolean): Unit = {
    Log.getLogger.trace("Calling getCustomerIds")
    val grain = 60 * 60 * 1000
    //Ideally this is called for every past hour for which it has not been computed.
    //Find oldest timestamp in metrics_stats tbl and look at the time now and find out
    //how many hours between them and now and create a list of hours - 1 timestamps
    //Then remove from that list entries for which there are already entries in summary_stats
    val active = Some(true)
    Anomaly.setSimulationMode(simulate)
    val customer_id = None
    CassandraDB.start
    val customerMap = Customer.getMaps(customer_id, active, None)
    customerMap.foreach(cm => {
      Log.getLogger.trace(s"Got customermap ${cm}")
      val deploymentMap = Deployment.getMaps(Some(cm.customer_id), None, active, None)
      deploymentMap.foreach(dm => {
        Log.getLogger.trace(s"Got deploymentmap ${dm}")
        val ssh = new SummaryStatsHourly(dm.customer_id, dm.deployment_id)
        val timeSlots = ssh.getTimeSlots
        Log.getLogger.trace(s"Finished getting slots")
        timeSlots.foreach(ts => {
          val tr = new TimeRange(ts, ts + grain)
          val (detections, connections) = SummaryState.getAnomaliesByGrain(dm.customer_id, dm.deployment_id, None, None, tr, 60)
          val clusterMap = Cluster.getMaps(Some(dm.customer_id), Some(dm.deployment_id), None, active, Some(tr))
          clusterMap.foreach(clm => {
            Log.getLogger.trace(s"Got clustermap ${clm}")
            val nodeMap = Node.getMaps(Some(clm.customer_id), Some(clm.deployment_id), Some(clm.cluster_id), None, active, Some(tr))
            nodeMap.foreach(nm => {
              Log.getLogger.trace(s"Got nodemap ${nm}")
              //Stats for each node in a cluster
              var ss: ssh.SummaryStats = null
              val sn = SummaryState.getNodeSnapshot(nm.customer_id, nm.deployment_id, nm.cluster_id, nm.host_ip, tr, detections, connections)
              if (sn != null) {
                ss = new ssh.SummaryStats(ts, getMinuteOfHour(ts), nm.customer_id, nm.deployment_id, nm.cluster_id, nm.host_ip, nm.host_name, sn.thermal.summary.severity,
                  JacksonWrapper.serialize(sn.thermal.critInfo), JacksonWrapper.serialize(sn.thermal.summary),
                  JacksonWrapper.serialize(sn.thermal.detail), JacksonWrapper.serialize(sn.stats),
                  JacksonWrapper.serialize(sn.connections), JacksonWrapper.serialize(sn.services))

                ssh.updateTimeSlot(ss)
                Log.getLogger.trace(JacksonWrapper.serialize(sn))
              } else {
                Log.getLogger.trace("Got null for node snapshot")
              }
            })

            //Stats for each cluster
            var ss: ssh.SummaryStats = null
            val sn = SummaryState.getClusterSnapshot(clm.customer_id, clm.deployment_id, clm.cluster_id, tr, detections, connections)
            if (sn != null) {
              ss = new ssh.SummaryStats(ts, getMinuteOfHour(ts), clm.customer_id, clm.deployment_id, clm.cluster_id, "255.255.255.255", "*",
                sn.thermal.summary.severity, JacksonWrapper.serialize(sn.thermal.critInfo), JacksonWrapper.serialize(sn.thermal.summary),
                JacksonWrapper.serialize(sn.thermal.detail), JacksonWrapper.serialize(sn.stats),
                JacksonWrapper.serialize(sn.connections), JacksonWrapper.serialize(sn.services))

              ssh.updateTimeSlot(ss)
              Log.getLogger.trace(JacksonWrapper.serialize(sn))
            } else {
              Log.getLogger.trace("Got null for cluster snapshot")
            }
          })

          //Stats for each deployment
          var ss: ssh.SummaryStats = null
          val sn = SummaryState.getDeploymentSnapshot(dm.customer_id, dm.deployment_id, tr, detections, connections)
          if (sn != null) {
            ss = new ssh.SummaryStats(ts, getMinuteOfHour(ts), dm.customer_id, dm.deployment_id, "*", "255.255.255.255", "*", sn.thermal.summary.severity,
              JacksonWrapper.serialize(sn.thermal.critInfo), JacksonWrapper.serialize(sn.thermal.summary),
              JacksonWrapper.serialize(sn.thermal.detail), JacksonWrapper.serialize(sn.stats),
              JacksonWrapper.serialize(sn.connections), JacksonWrapper.serialize(sn.services))

            ssh.updateTimeSlot(ss)
            Log.getLogger.trace(JacksonWrapper.serialize(sn))
          } else {
            Log.getLogger.trace("Got null for deployment snapshot")
          }
        })
      })
    })
    CassandraDB.shutdown
  }

  def getEarliesHourTimestampInMillis(grain: Int): (Long, Int) = {
    val timeNow = {
      val timestamp = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).getTimeInMillis
      val tsInSeconds: Long = timestamp / 1000
      val tsInHours: Long = tsInSeconds / 3600
      val tsInMillis: Long = tsInHours * 3600 * 1000
      tsInMillis
    }

    //Processing of the data and cassandra write happens at least one hour behind to be complete
    val behindInHours = 1
    val earliest = {
      timeNow + (grain - behindInHours) * 3600 * 1000
    }
    (earliest, behindInHours)
  }

  def getEarliesMinuteTimestampInMillis(grain: Int): (Long, Int) = {
    val timeNow = {
      val timestamp = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).getTimeInMillis
      val tsInSeconds: Long = timestamp / 1000
      val tsInMinutes: Long = tsInSeconds / 60
      val tsInMillis: Long = tsInMinutes * 60 * 1000
      tsInMillis
    }

    //Data coming from agent is sent only the completion of the nth minute
    //Data process in kafka stream is picked by the minute one minute behind which is n - 1 th minute
    //Processing of the data and cassandra write happens in the n - 2 th minute
    val behindInMinutes = 2
    val earliest = {
      val a = Metrics.getMinuteOfHour(timeNow - behindInMinutes * 60 * 1000)
      val b = (math.floor((a + grain) / grain) * grain).toInt
      timeNow + ((b - a) - behindInMinutes) * 60 * 1000
    }
    (earliest, behindInMinutes)
  }

  def getUTCTimeNowInMillis(): Long = {
    val timestamp = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).getTimeInMillis
    timestamp
  }
}
