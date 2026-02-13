package com.infrared.entry.infra

import java.util.Calendar

import com.infrared.util._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.util.hashing.{MurmurHash3 => MH3}

/**
 * Created by prashun on 10/10/16.
 */

object Service extends Group {

  case class ServiceMap(customer_id: String, deployment_id: String, cluster_id: String, name: String, svc: Int,
                        ipver: Int, proto: Int, port: Int, interface: String, last_modified: Long) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  object SERVICE_MAP_COLS {
    val TABLE_NAME = "service_map"
    val CUSTOMER_ID = "customer_id"
    val DEPLOYMENT_ID = "deployment_id"
    val CLUSTER_ID = "cluster_id"
    val NAME = "name"
    val SVC = "svc"
    val IPVER = "ipver"
    val PROTO = "proto"
    val PORT = "port"
    val INTERFACE = "interface"
    val TS = "ts"
    val UHASH = "uhash"
  }

  def getMaps(customer_id: Option[String], deployment_id: Option[String], cluster_id: Option[String],
              name: Option[String], svc: Option[Int], ipver: Option[Int], proto: Option[Int], port: Option[Int],
              interface: Option[String]): List[ServiceMap] = {
    val cassdb = getCassandraHandle
    val query = s"SELECT ${SERVICE_MAP_COLS.CUSTOMER_ID}, ${SERVICE_MAP_COLS.DEPLOYMENT_ID}, ${SERVICE_MAP_COLS.CLUSTER_ID}, " +
      s"${SERVICE_MAP_COLS.NAME}, ${SERVICE_MAP_COLS.SVC}, ${SERVICE_MAP_COLS.IPVER}, ${SERVICE_MAP_COLS.PROTO}, " +
      s"${SERVICE_MAP_COLS.PORT}, ${SERVICE_MAP_COLS.INTERFACE}, ${SERVICE_MAP_COLS.TS} FROM ${SERVICE_MAP_COLS.TABLE_NAME}"
    val resultSet = cassdb.executeQuery(query)

    val service_map = {
      if (resultSet != null) {
        val svc_map = resultSet.asScala
          .filter(customer_id.isDefined && _.getString(SERVICE_MAP_COLS.CUSTOMER_ID) == customer_id.get || !customer_id.isDefined)
          .filter(deployment_id.isDefined && _.getString(SERVICE_MAP_COLS.DEPLOYMENT_ID) == deployment_id.get || !deployment_id.isDefined)
          .filter(cluster_id.isDefined && _.getString(SERVICE_MAP_COLS.CLUSTER_ID) == cluster_id.get || !cluster_id.isDefined)
          .filter(name.isDefined && _.getString(SERVICE_MAP_COLS.NAME) == name.get || !name.isDefined)
          .filter(svc.isDefined && _.getInt(SERVICE_MAP_COLS.SVC) == svc.get || !svc.isDefined)
          .filter(ipver.isDefined && _.getInt(SERVICE_MAP_COLS.IPVER) == ipver.get || !ipver.isDefined)
          .filter(proto.isDefined && _.getInt(SERVICE_MAP_COLS.PROTO) == proto.get || !proto.isDefined)
          .filter(port.isDefined && _.getInt(SERVICE_MAP_COLS.PORT) == port.get || !port.isDefined)
          .filter(interface.isDefined && _.getString(SERVICE_MAP_COLS.INTERFACE) == interface.get || !interface.isDefined)
          .map(row => {
            new ServiceMap(row.getString(SERVICE_MAP_COLS.CUSTOMER_ID),
              row.getString(SERVICE_MAP_COLS.DEPLOYMENT_ID),
              row.getString(SERVICE_MAP_COLS.CLUSTER_ID),
              row.getString(SERVICE_MAP_COLS.NAME),
              row.getInt(SERVICE_MAP_COLS.SVC),
              row.getInt(SERVICE_MAP_COLS.IPVER),
              row.getInt(SERVICE_MAP_COLS.PROTO),
              row.getInt(SERVICE_MAP_COLS.PORT),
              row.getString(SERVICE_MAP_COLS.INTERFACE),
              row.getTimestamp(SERVICE_MAP_COLS.TS).getTime)
          }).toList
        svc_map
      } else {
        List()
      }
    }
    cassdb.close()
    service_map
  }

  def setMap(item: ServiceMap): Boolean = {
    val cassdb = getCassandraHandle
    val ts = Calendar.getInstance().getTimeInMillis
    val uhash = s"${item.customer_id},${item.deployment_id},${item.cluster_id},${item.name}"
    val ttl = 2 * 86400
    val query = s"INSERT INTO ${SERVICE_MAP_COLS.TABLE_NAME}(${SERVICE_MAP_COLS.CUSTOMER_ID}, " +
      s"${SERVICE_MAP_COLS.DEPLOYMENT_ID}, ${SERVICE_MAP_COLS.CLUSTER_ID}, ${SERVICE_MAP_COLS.NAME}," +
      s"${SERVICE_MAP_COLS.SVC}, ${SERVICE_MAP_COLS.IPVER}, ${SERVICE_MAP_COLS.PROTO}, ${SERVICE_MAP_COLS.PORT}, " +
      s"${SERVICE_MAP_COLS.INTERFACE}, ${SERVICE_MAP_COLS.TS}, ${SERVICE_MAP_COLS.UHASH}) " +
      s"VALUES ('${item.customer_id}', '${item.deployment_id}', '${item.cluster_id}','${item.name}', " +
      s"${item.svc}, ${item.ipver}, ${item.proto}, ${item.port}, '${item.interface}', ${item.last_modified}, '${uhash}') " +
      s"USING TTL ${ttl}"
    val resultSet = cassdb.executeQuery(query)
    cassdb.close()
    true
  }
}
