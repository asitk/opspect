package com.infrared.entry

import com.infrared.entry.infra.{Cluster, Deployment, Node}
import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.util.hashing.{MurmurHash3 => MH3}

/**
 * Created by prashun on 22/6/16.
 */
object DApi {
  def run(): Unit = {
    val customer_id = "1234abcd"
    val deployment_id = "ec2-dc-01"
    val cluster_id = "myappcluster"
    val host_ip = "192.168.6.21"
    CassandraDB.start()
    val start = 1473379200000L
    val end = start + 10*24*60 * 60 * 1000
    val tr = new TimeRange(start, end)
    val a1 = Deployment.getTimelineMarkers(customer_id, deployment_id, tr)
    println(s"The timeline markers are ${JacksonWrapper.serialize(a1)}")
    val b1 = Deployment.getDetails(customer_id, deployment_id, tr)
    println(s"The deployment details are ${JacksonWrapper.serialize(b1)}")
    val c1 = Deployment.getSnapshot(customer_id, deployment_id, tr)
    println(s"The deployment snapshots are ${JacksonWrapper.serialize(c1)}")
    val d1 = Deployment.getConnection(customer_id, deployment_id, tr)
    println(s"The deployment connections are ${JacksonWrapper.serialize(d1)}")
    val e1 = Deployment.getService(customer_id, deployment_id, tr)
    println(s"The deployment services are ${JacksonWrapper.serialize(e1)}")

    val a2 = Cluster.getTimelineMarkers(customer_id, deployment_id, cluster_id, tr)
    println(s"The timeline markers are ${JacksonWrapper.serialize(a2)}")
    val b2 = Cluster.getDetails(customer_id, deployment_id, cluster_id, tr)
    println(s"The cluster details are ${JacksonWrapper.serialize(b2)}")
    val c2 = Cluster.getSnapshot(customer_id, deployment_id, cluster_id, tr)
    println(s"The cluster snapshot are ${JacksonWrapper.serialize(c2)}")
    val d2 = Cluster.getConnection(customer_id, deployment_id, cluster_id, tr)
    println(s"The cluster connections are ${JacksonWrapper.serialize(d2)}")
    val e2 = Cluster.getService(customer_id, deployment_id, cluster_id, tr)
    println(s"The cluster services are ${JacksonWrapper.serialize(e2)}")

    val a3 = Node.getTimelineMarkers(customer_id, deployment_id, cluster_id, host_ip, tr)
    println(s"The timeline markers are ${JacksonWrapper.serialize(a3)}")
    val b3 = Node.getDetails(customer_id, deployment_id, cluster_id, host_ip, tr)
    println(s"The node details are ${JacksonWrapper.serialize(b3)}")
    val c3 = Node.getSnapshot(customer_id, deployment_id, cluster_id, host_ip, tr)
    println(s"The node snapshot are ${JacksonWrapper.serialize(c3)}")
    val d3 = Node.getConnection(customer_id, deployment_id, cluster_id, host_ip, tr)
    println(s"The node connections are ${JacksonWrapper.serialize(d3)}")
    val e3 = Node.getService(customer_id, deployment_id, cluster_id, host_ip, tr)
    println(s"The node services are ${JacksonWrapper.serialize(e3)}")

    val m = NWObject.getNWServices(customer_id, deployment_id, tr)
    println(s"The deployment network svc details are ${JacksonWrapper.serialize(m)}")
    CassandraDB.shutdown()
  }
}
