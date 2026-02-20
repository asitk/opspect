package com.opspect.util

/** Created by prashun on 10/5/16.
  */

import java.net.InetSocketAddress

import com.datastax.driver.core._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.collection.mutable.ListBuffer

object CassandraDB {
  private var inited = false
  private var count = 0
  private var cluster: Cluster = null
  private var instance =
    scala.collection.mutable.HashMap[String, (CassDB, Int)]()
  private var prev_session: Session = null

  var accessed: Int = 0
  case class TimeRange(start: Long, end: Long) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  def getTimeInSeconds(ts: Long): Long = {
    val tsInSeconds: Long = ts / 1000
    val tsInMinutes: Long = tsInSeconds / 60
    val tsInMillis: Long = tsInMinutes * 60 * 1000
    tsInMillis
  }
  def getTimeRangeAsASetString(tr: TimeRange, grainInMinutes: Int): String = {
    val start = getTimeInSeconds(tr.start)
    val stop = getTimeInSeconds(tr.end)
    val tsSet = (start until stop by grainInMinutes * 60 * 1000)
    var str = ""
    tsSet.foreach(x => { str += s"${x}," })
    str += s"${tsSet.last}"
    str
  }
  case class Address(ip: String, port: Int) extends Serializable {
    override def toString: String = {
      JacksonWrapper.serialize(this)
    }
  }

  def getScalaInstance(keyspace: String, addressList: List[Address]): CassDB = {
    val session = init(keyspace, addressList)
    val po = session.getCluster.getConfiguration.getPoolingOptions
    new CassDB(session)
  }

  def start(): Unit = {}

  def shutdown(): Unit = {
    if (cluster != null && !cluster.isClosed) {
      cluster.close()
    }
  }

  private def init(keyspace: String, addresses: List[Address]): Session = {
    val key = s"${keyspace}::${addresses}"
    var addressList = new ListBuffer[InetSocketAddress]()
    val session: Session = {
      Log.getLogger.debug(s"Session Count = ${count} accessed = ${accessed}")
      if (count > 100 && accessed == 0) {
        inited = false
        count = 0
        if (prev_session != null && !prev_session.isClosed) {
          prev_session.closeAsync()
          prev_session = null
        }
        cluster.closeAsync()
      }
      if (!inited || cluster.isClosed) {
        Log.getLogger.trace("Recreating cluster")
        for (addr <- addresses) {
          addressList += new InetSocketAddress(addr.ip, addr.port)
        }
        cluster = {
          var so = new SocketOptions()
          so = so.setReadTimeoutMillis(60000)
          so = so.setKeepAlive(true)
          so = so.setSoLinger(1)
          var po = new PoolingOptions()
          po = po.setConnectionsPerHost(HostDistance.LOCAL, 4, 1000)
          po = po.setMaxRequestsPerConnection(HostDistance.LOCAL, 1000)
          po = po.setHeartbeatIntervalSeconds(60).setIdleTimeoutSeconds(60)
          po = po.setPoolTimeoutMillis(30000)

          Cluster
            .builder()
            .addContactPointsWithPorts(addressList.asJavaCollection)
            .withPoolingOptions(po)
            .withSocketOptions(so)
            .withQueryOptions(
              new QueryOptions()
                .setConsistencyLevel(QueryOptions.DEFAULT_CONSISTENCY_LEVEL)
            )
            .build()
        }
        val s = cluster.connect(keyspace)
        inited = true
        s
      } else {
        count += 1
        if (accessed == 0 && prev_session != null && !prev_session.isClosed) {
          prev_session
        } else {
          cluster.newSession()
        }
      }
    }
    prev_session = session
    session.execute(s"USE ${keyspace};")
    session
  }

  class CassDB(_session: Session) {
    private val session = _session

    def close(): Unit = {
      if (accessed < 0) {
        throw new Exception("Already closed handle")
      }
      accessed -= 1
    }

    def executeQuery(query: String): ResultSet = {
      var result: ResultSet = null
      if (query.contains("SELECT ")) {
        Log.getLogger.debug(s"Executing query ${query}")
      } else {
        Log.getLogger.debug("Executing query INSERT...")
      }
      try {
        val t1 = System.currentTimeMillis()
        result = session.execute(query)
        val t2 = System.currentTimeMillis()
        var matched = false
        var ntries = 5
        do {
          if (result.isFullyFetched || ntries == 0) {
            matched = true
          } else {
            Log.getLogger.debug(
              s"Not fully fetched yet - ${ntries} tries remaining"
            )
            Thread.sleep(100L)
            ntries -= 1
          }
        } while (!matched)
        Log.getLogger.debug(s"Time taken = ${t2 - t1} ms")
      } catch {
        case e: Exception => {
          Log.getLogger.error(
            s"Got exception ${e.getMessage} on query ${query}"
          )
          throw new Exception(e)
        }
      }
      accessed += 1
      result
    }
  }
}
