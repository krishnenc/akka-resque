package org.akkaresque

import akka.actor.{ Actor, ActorRef }
import com.redis._
import com.redis.serialization._
import cc.spray.json._

case class Payload(classname: String, firstAttempts: Option[String], args: List[String])
case class workerData(queue: String, run_at: String, payload: Payload)

object PayloadJsonProtocol extends DefaultJsonProtocol {
  implicit val PayloadFormat =
    jsonFormat(Payload, "class", "firstAttempt", "args")
}

object workerDataJsonProtocol extends DefaultJsonProtocol {
  import PayloadJsonProtocol._
  implicit val workerDataFormat =
    jsonFormat(workerData, "queue", "run_at", "payload")
}

object ResQ {
  def apply(host: String = "localhost",
    port: Int = 6379): ResQ = {
    new ResQ(new RedisClient(host, port))
  }
}

class ResQ(redis: RedisClient)
  extends DefaultJsonProtocol {
  import PayloadJsonProtocol._

  val _watched_queues = collection.mutable.Set.empty[String]
  val redis_cli = redis

  def watch_queue(queue: String) = {
    if (!_watched_queues.contains(queue)) {
      redis_cli.sadd("resque:queues", queue)
      _watched_queues.add(queue)
    }
  }
  def push(queue: String, item: String) {
    watch_queue(queue)
    redis.rpush("resque:queue:%s".format(queue), item)
  }
  def pop(timeout: Int, queues: List[String]) = {
    val formatted_keys = queues map (q => "resque:queue:%s".format(q))
    //val ret = redis.blpop(timeout, "dummy", formatted_keys: _*)
    listPop(0 ,formatted_keys)
  }

  def listPop(index: Int, queues: List[String]): Option[(java.lang.String, org.akkaresque.Payload)] =
    {
      val ret = redis.lpop(queues(index))
      ret match {
        case Some(value) =>
          Some(queues(index).substring(13, queues(index).length), JsonParser(value).convertTo[Payload])
        case None =>
          if (index == queues.length - 1)
            None
          else
            listPop(index + 1, queues)
      }
    }

  def size(queue: String) = {
    redis_cli.llen("resque:queue:%s".format(queue))
  }

  def peek(queue: String, start: Int = 0, count: Int = 1) =
    list_range("resque:queue:%s".format(queue), start, count)

  def list_range(key: String, start: Int, count: Int) =
    {
      val items = redis.lrange(key, start, (start + count) - 1)
      items match {
        case None =>
          None
        case Some(value) =>
          value map {
            case x =>
              Some(JsonParser(x.getOrElse("")))
          }
      }

    }
  //TODO : This would really be possible with reflection
  //       Need the necessary introspection to verify attributes on a class
  def enqueue(klass: java.lang.Class[_], args: String*) {

  }
  def enqueue(klass_as_string: String,
    queue: String, args: List[String]) =
    {
      push(queue, CompactPrinter(Payload(klass_as_string, None, args).toJson))
    }
  def enqueue(workerRef: ActorRef,
    queue: String, args: List[String]) =
    {
      push(queue, CompactPrinter(Payload(workerRef.path.toString, None, args).toJson))
    }
  def queues() = {
    redis.smembers("resque:queues")
  }
  def addWorker(name: String) {
    redis.sadd("resque:workers", name)
  }
}