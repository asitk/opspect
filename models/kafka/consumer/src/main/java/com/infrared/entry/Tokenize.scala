package com.infrared.entry

/**
 * Created by prashun on 3/9/16.
 */
object Tokenize {
  def findStringTemplate(a: String, b: String): (String, Boolean) = {
    var last_str = ""
    val delim = Array(' ', '\t', '\r', '\n')
    val toks_a = a.split(delim)
    val toks_b = b.split(delim)
    var conf_a = Array[(Boolean, String)]()
    var conf_b = Array[(Boolean, String)]()
    toks_a.foreach(x => {
      val status = {
        if (toks_b.contains(x)) true else false
      }
      conf_a :+=(status, x)
    })
    toks_b.foreach(x => {
      val status = {
        if (toks_a.contains(x)) true else false
      }
      conf_b :+=(status, x)
    })

    val c = conf_a.filter(_._1 == true)
    val d = conf_b.filter(_._1 == true)
    val status = {
      if (c.length == a.length || d.length == b.length) false else true
    }

    //println(s"The status = ${status}")
    if (status) {
      val int_str_a = findIntermediateString(c, conf_a)
      val int_str_b = findIntermediateString(d, conf_b)
      val final_str_a = finalizeString(int_str_a)
      val final_str_b = finalizeString(int_str_b)
      val ftoks_a = final_str_a.split(delim)
      val ftoks_b = final_str_b.split(delim)
      if (ftoks_a.count(_ != "_") == ftoks_b.count(_ != "_")) {
        last_str = final_str_a
      } else {
        val (a, b) = findStringTemplate(final_str_a, final_str_b)
        if (b) {
          last_str = a
        }
      }
    }

    val fStatus = {
      if (last_str == "_" || last_str.length == 0) false else true
    }

    (last_str, fStatus)
  }

  private def findIntermediateString(c: Array[(Boolean, String)], d: Array[(Boolean, String)]): String = {
    var i = 0
    var final_str = ""

    d.foreach(x => {
      if (i < c.length && x._2 == c(i)._2) {
        final_str = s"${final_str} ${x._2} "
        i += 1
      } else {
        final_str = s"${final_str}_*_"
      }
    })
    final_str
  }

  private def finalizeString(str: String): String = {
    val delimiters = Array(' ', '\t', '\r', '\n')
    //println(s"final_str = ${str}")
    var last_str = ""
    val final_tokens = str.split(delimiters)
    final_tokens.foreach(v => {
      //println(s"v = ${v}")
      val x = v.trim
      if (x.length > 0) {
        //println(s"x = ${x}")
        val y = x.replace("_*_", "").trim
        //println(s"y = ${y}")
        val z = {
          if (y.length == 0) y.replace("", "_") else y
        }
        //println(s"z = ${z}")
        if (z.trim.length > 0) {
          last_str = s"$last_str ${z.trim}"
        }
      }
      //println(s"last_str = ${last_str}")
    })
    last_str
  }
}
