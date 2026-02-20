package com.opspect.util

import org.apache.log4j.{Level, Logger}

/** Created by prashun on 9/9/16.
  */
object Log {
  val module = "nuvidata"
  Logger.getRootLogger.setLevel(Level.ERROR)

  def getLogger: org.apache.log4j.Logger = {
    Logger.getLogger(module)
  }
}
