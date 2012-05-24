package org.akkaresque

import cc.spray.json._
import akka.actor.{ Actor, ActorRef, PoisonPill, ActorLogging, ActorSystem, Props, ReceiveTimeout }
import akka.event.LoggingReceive
import akka.util.duration._
import akka.util.Duration
import akka.util.Timeout
import akka.pattern.{ ask, pipe }
import org.akkaresque.failure._

case class perform(args: List[String])

object Job {
  def apply(payload: Payload,
		    resq: ResQ, worker_id: Option[String], queue: String): Job = {
    new Job(payload, resq, worker_id, queue)
  }
  
  """Reserve a job on one of the queues. This marks this job so
	that other workers will not pick it up.
	"""
  def reserve(res: ResQ,
    worker: Option[String],
    timeout: Int, queues: List[String]) = {
    res.pop(timeout, queues : List[String]) match {
      case None =>
        None
      case Some(job) =>
        Some(Job(job._2, res, worker, job._1))
    }
  }
}

class Job(payload: Payload,
		  resq: ResQ, 
		  worker: Option[String], 
		  queue: String) {

  override def toString =
    "(Job{%s} | %s | %s)".format(queue, payload.classname, payload.args.toString)

  /*
This method converts payload into args and calls the ``perform``
method on the payload class.
*/
  val _payload = payload
  val _resq = resq
  val _queue = queue
  val _worker = worker
    
  def performJob(workerContext : ActorSystem) = {
    val payload_class_str = payload.classname
    val worker = workerContext.actorFor(payload_class_str)
    //TODO : The timeout should be configurable
    implicit val askTimeout = Timeout(60 seconds)
    worker.ask(perform(payload.args))
  }

  def fail(ex: Exception) {
	  val failure = ResqueFailure.create(ex, queue, payload, Some(_worker.getOrElse("").toString))
	  failure.save(resq)
  }
  //TODO implement later
  def retry(payload_class: String, args: List[String]) = {

  }
}
