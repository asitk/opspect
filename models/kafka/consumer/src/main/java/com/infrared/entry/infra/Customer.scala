package com.infrared.entry.infra

import java.util.Calendar

import com.infrared.util.CassandraDB._
import com.infrared.util._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.util.hashing.{MurmurHash3 => MH3}
/**
 * Created by prashun on 7/6/16.
 */
object Customer extends Group{
  case class CustomerMap(customer_id : String, name : String, description : String, active : Boolean, last_modified : Long) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  object CUSTOMER_MAP_COLS {
    val TABLE_NAME = "customer_map"
    val CUSTOMER_ID = "customer_id"
    val NAME = "name"
    val DESCRIPTION = "description"
    val ACTIVE = "active"
    val TS = "ts"
  }
  def getMaps(customer_id : Option[String], active : Option[Boolean], time_range: Option[TimeRange]) : List[CustomerMap] = {
    val cassdb = getCassandraHandle
    val active_filtered = active.getOrElse(false)
    val customer_id_filtered = customer_id.orNull
    val tr = time_range.orNull
    val query = s"SELECT ${CUSTOMER_MAP_COLS.CUSTOMER_ID}, ${CUSTOMER_MAP_COLS.NAME}, ${CUSTOMER_MAP_COLS.DESCRIPTION}, ${CUSTOMER_MAP_COLS.ACTIVE}, ${CUSTOMER_MAP_COLS.TS} FROM ${CUSTOMER_MAP_COLS.TABLE_NAME}"
    val resultSet = cassdb.executeQuery(query)

    val cust_map = {
      if (resultSet != null) {
        val cust_map = resultSet.asScala
          .filter(tr == null || _.getTimestamp(CUSTOMER_MAP_COLS.TS).getTime <= tr.end)
          .filter(active_filtered == _.getBool(CUSTOMER_MAP_COLS.ACTIVE) || !active_filtered)
          .filter(customer_id_filtered == _.getString(CUSTOMER_MAP_COLS.CUSTOMER_ID) || customer_id_filtered == null)
          .groupBy(row => {
            row.getString(CUSTOMER_MAP_COLS.CUSTOMER_ID)
          })
          .mapValues(rows => {
            rows.head
          }).values
          .map(row => {
            new CustomerMap(row.getString(CUSTOMER_MAP_COLS.CUSTOMER_ID), row.getString(CUSTOMER_MAP_COLS.NAME),
              row.getString(CUSTOMER_MAP_COLS.DESCRIPTION), row.getBool(CUSTOMER_MAP_COLS.ACTIVE),
              row.getTimestamp(CUSTOMER_MAP_COLS.TS).getTime)
          }).toList
        cust_map
      } else {
        List()
      }
    }
    cassdb.close()
    cust_map
  }

  def setMap(item : CustomerMap) : Boolean = {
    val cassdb = getCassandraHandle
    val ts = Calendar.getInstance().getTimeInMillis
    val query = s"INSERT INTO ${CUSTOMER_MAP_COLS.TABLE_NAME}(${CUSTOMER_MAP_COLS.CUSTOMER_ID}, ${CUSTOMER_MAP_COLS.NAME}, ${CUSTOMER_MAP_COLS.DESCRIPTION}, ${CUSTOMER_MAP_COLS.ACTIVE}, ${CUSTOMER_MAP_COLS.TS}) " +
      s"VALUES ('${item.customer_id}, '${item.name}', '${item.description}', ${item.active}, ${ts})"
    val resultSet = cassdb.executeQuery(query)
    cassdb.close()
    true
  }
}
