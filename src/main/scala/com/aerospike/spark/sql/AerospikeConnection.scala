package com.aerospike.spark.sql

import com.aerospike.helper.query._
import com.aerospike.client.AerospikeClient

/**
 * This class caches the AerospikeClient. The key used to retrive the client is based on the
 * seen host supplied and the port.
 * 
 * The purpose of this class is to eliminate excessive client creation with
 * the goal of having 1 client per executor.
 */
object AerospikeConnection {
  val clientCache = new scala.collection.mutable.HashMap[String, AerospikeClient]()
  val queryEngineCache = new scala.collection.mutable.HashMap[AerospikeClient, QueryEngine]()
  
  def getQueryEngine(config: AerospikeConfig) : QueryEngine = synchronized {
    val host = config.get(AerospikeConfig.SeedHost);
    val port = config.get(AerospikeConfig.Port);
    val client = getClient(s"$host:$port")
    queryEngineCache.getOrElse(client, {
        val newEngine = new QueryEngine(client)
        newEngine.refreshCluster()
        queryEngineCache += (client -> newEngine)
        newEngine
      })
  }
  def getClient(config: AerospikeConfig) : AerospikeClient = {
    val host = config.get(AerospikeConfig.SeedHost);
    val port = config.get(AerospikeConfig.Port);
    getClient(s"$host:$port")
  }
  
   def getClient(host: String, port: Int) : AerospikeClient = {
    getClient(s"$host:$port")
  }
  
  def getClient(hostPort: String) : AerospikeClient = synchronized {
    var client = clientCache.getOrElse(hostPort, {
        newClient(hostPort)
      })
    if (!client.isConnected())
      client = newClient(hostPort)
    client   
  }
  
  private def newClient(hostPort: String): AerospikeClient = {
    val splitHost = hostPort.split(":")
        val host = splitHost(0)
        val port = splitHost(1).toInt
        val newClient = new AerospikeClient(host, port)
        val nodes = newClient.getNodes
        for (node <- nodes) {
          clientCache += (node.getHost.toString() -> newClient)
        }
        newClient
  }
  
}