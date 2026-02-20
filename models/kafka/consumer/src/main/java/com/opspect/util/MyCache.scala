package com.opspect.util

import java.util.concurrent.TimeUnit

import com.google.common.cache.{Cache, CacheBuilder}

/** Created by prashun on 8/9/16.
  */
object MyCache {
  private lazy val gCache: Cache[String, String] = CacheBuilder
    .newBuilder()
    .concurrencyLevel(1)
    .softValues()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build[String, String]

  def put(k: String, v: String): Unit = {
    gCache.put(k, v)
  }

  def get(k: String): String = {
    gCache.getIfPresent(k)
  }

  def invalidate(k: String) = {
    gCache.invalidate(k)
  }

  def invalidateAll(): Unit = {
    gCache.invalidateAll()
  }
}
