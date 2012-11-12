package org.akkaresque.failure

import cc.spray.json._
import org.akkaresque.ResQ
import org.akkaresque.Payload
import org.scala_tools.time.Imports._
import org.joda.time.format.ISODateTimeFormat

import org.akkaresque.PayloadJsonProtocol._

case class Failure(failed_at: String, payload: Payload,
  exception: String, error: String,
  backtrace: Array[String], queue: String,
  worker: Option[String])

object FailureJsonProtocol extends DefaultJsonProtocol {
  import org.akkaresque.PayloadJsonProtocol._
  implicit val FailureFormat = jsonFormat(Failure, "failed_at", "payload", "exception", "error", "backtrace", "queue", "worker")
}
object RedisBackend {
  import FailureJsonProtocol._
  def count(resq: ResQ) {
    resq.redis_cli.withClient(client => {
      client.llen("resque:failed")
    })
  }
  def all(resq: ResQ, start: Int = 0, count: Int = 1) = {
    resq.redis_cli.withClient(client => {
      val items = client.lrange("resque:failed", start, count)
      items match {
        case None =>
          None
        case Some(item) =>
          val failures = item map {
            case failureJson =>
              JsonParser(failureJson.get).convertTo[Failure]
          }
          Some(failures)
      }
    })
  }
  def clear(resq: ResQ) = {
    resq.redis_cli.withClient(client => {
      client.del("resque:failed")
    })
  }
}
//"""Extends the ``BaseBackend`` to provide a Redis backend for failed jobs."""
class RedisBackend(ex: Exception, queue: String,
  payload: Payload, worker: Option[String]) extends BaseBackend {

  private val _exp = ex
  private val _traceback: String = ex.getMessage
  private val _queue: String = queue
  private val _payload = payload
  private val _worker = worker

  import FailureJsonProtocol._
  import RedisBackend._
  //"""Saves the failed Job into a "failed" Redis queue preserving all its original enqueud info."""
  def save(resq: ResQ) {
    val today = DateTime.now
    val fmt = ISODateTimeFormat.dateTimeNoMillis()
    val failure = Failure(today.toString(fmt),
      _payload, _exp.getMessage,
      _parse_message(_exp),
      _parse_traceback(_traceback),
      _queue, _worker)
    resq.redis_cli.withClient(client => {
      client.rpush("resque:failed", CompactPrinter(failure.toJson))
    })
  }
}