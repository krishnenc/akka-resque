package org.akkaresque

import com.redis._
import com.redis.serialization._
import cc.spray.json._

object Stat {
  def apply(name: String, resq: ResQ) = {
    new Stat(name, "resque:stat:%s".format(name), resq)
  }
}
//A Stat class which shows the current status of the queue.
case class Stat(_name: String, _key: String, _resq: ResQ) {

  def get = {
    _resq.redis_cli.withClient(client => {
      val v = client.get(_key)
      v match {
        case None =>
          0
        case Some(f) =>
          f.toInt
      }
    })
  }

  def incr(amount: Int = 1) =
    {
      _resq.redis_cli.withClient(client => {
        client.incrby(_key, amount)
      })
    }

  def decr(amount: Int = 1) =
    {
      _resq.redis_cli.withClient(client => {
        client.decrby(_key, amount)
      })
    }

  def clear =
    {
      _resq.redis_cli.withClient(client => {
        client.del(_key)
      })
    }

}