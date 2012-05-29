package org.akkaresque.failure

import org.akkaresque.Payload
import org.akkaresque.PayloadJsonProtocol._
import org.akkaresque.ResQ
import cc.spray.json._

object ResqueFailure extends DefaultJsonProtocol {

  def create(ex: Exception, queue: String,
    payload: Payload, worker: Option[String]) = {
    new RedisBackend(ex, queue, payload, worker)
  }
  def count(resq: ResQ) =
    RedisBackend.count(resq)

  def clear(resq: ResQ) =
    RedisBackend.clear(resq)

  def requeue(resq: ResQ, fail: Failure) =
    resq.push(fail.queue, CompactPrinter(fail.payload.toJson))

  def retry(resq: ResQ, queue: String, payload: Payload) =
    {
      resq.push(queue, CompactPrinter(payload.toJson))
      delete(resq, payload)
    }
  def delete(resq: ResQ, payload: Payload) = {
    resq.redis_cli.withClient(client => {
    	client.lrem("resque:failed", 1, CompactPrinter(payload.toJson))
    })
  }
}
