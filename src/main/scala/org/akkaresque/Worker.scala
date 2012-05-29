package org.akkaresque

import cc.spray.json._
import com.redis._
import org.akkaresque.Machine._
import akka.actor.{ Actor, ActorRef, PoisonPill, ActorLogging, ActorSystem, Props, ActorContext }
import akka.event.LoggingReceive
import akka.util.duration._
import akka.util.Duration
import akka.util.Timeout
import akka.pattern.{ ask, pipe }
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import workerDataJsonProtocol._

case class work(msg: String)
case class jobPassed(job: Job)
case class jobFailed(ex: Exception, job: Job)

/*
 *   TODO Provide Alternatives
     Load from config etc ...
     Returns a reference to the actor created
     Send it a poison pill if you want to shut it down -it should clean itself up before shutting down
 */
object Worker {
  def apply(system: ActorContext,
    queues: List[String],
    host: RedisClientPool,
    timeout: Int = 5,
    Interval: Int = 5) = {
    //Create a worker and start processing jobs!
    val worker = system.actorOf(Props(new Worker(queues, host, timeout, Interval)))
    worker ! work("bang!")
    worker
  }
  def all(host: RedisClientPool, context: ActorSystem) = {
    val resq = ResQ(host)
    resq.redis_cli.withClient(client => {
      val workers = client.smembers("resque:workers")
      workers match {
        case None =>
          None
        case Some(worker) =>
          val workerSet = worker map {
            case worker =>
              find(worker.get, resq, client, context)
          }
          Some(workerSet)
      }
    })
  }
  def find(worker_id: String, resq: ResQ,
    redisServer: RedisClient, context: ActorSystem) = {
    if (exists(worker_id, resq)) {
      val queues = worker_id.split(":")(2).split(",").toList
      val workerRef = context.actorFor(new String(Base64.decode(worker_id.split("1")(1).toString).toArray))
      Some(workerRef)
    } else {
      None
    }
  }
  def exists(worker_id: String, resq: ResQ) = {
    resq.redis_cli.withClient(client =>
      client.sismember("resque:workers", worker_id))
  }
  def working(host: RedisClientPool, context: ActorSystem) = {
    val workers = all(host, context)
  }
  //TODO: Send a poison pill to all workers
  def shutdown_all(host: String, port: Int, context: ActorSystem) {

  }
  //TODO : Unregister worker for all actors that return a dead letter reference
  def prune_dead_workers = {

  }
}

class Worker(queues: List[String],
  host: RedisClientPool,
  timeout: Int,
  interval: Int) extends Actor
  with ActorLogging
  with DefaultJsonProtocol {

  private val _resq: ResQ = ResQ(host)
  private var _shutdown = false
  private var _started: Option[DateTime] = _
  //Path is Base64 encoded to prevent confusion when using remote actor paths
  //TODO : Implement a new view in resque web to display more info on akka workers - Base64 decode the actor path name
  //We can remotely kill it if it is a remote actor address
  private val _id = "%s:%s:%s".format(hostname, Base64.encode(self.path.toString.getBytes), queues.mkString(","))

  override def preStart() = {
    startup
  }
  override def postStop() = {
    log.info("Stopping Worker")
    unregister_worker
    _set_started(None)
  }

  def receive = LoggingReceive {
    case work(msg) =>
      //log.info("Checking for jobs")
      DoWork
    case jobPassed(job) =>
      log.info("Job Passed")
      done_working
      self ! work("bang!")
    case jobFailed(ex, job) =>
      log.error("Job Failed")
      _handle_job_exception(ex, job)
      done_working
      self ! work("bang!")
  }

  //Instead of running in an infinite loop -> query it with a scheduler
  def DoWork() = {
    //To do so in a non-blocking way send a timeout of zero
    val job = reserve(timeout)
    job match {
      case None =>
        //Schedule a retry if no jobs found
        context.system.scheduler.scheduleOnce(interval seconds, self, work("bang!"))
      case Some(reservedJob) =>
        process(reservedJob)
    }
  }

  def _set_started(time: Option[DateTime]) {
    time match {
      case None =>
        host.withClient(client => {
          client.del("resque:worker:%s:started".format(_id))
        })
      case Some(t) =>
        val fmt = ISODateTimeFormat.dateTime();
        host.withClient(client => {
          client.set("resque:worker:%s:started".format(_id), fmt.print(t))
        })
    }
  }

  def _get_started() = {
    host.withClient(client => {
      val dt = client.get("resque:worker:%s:started".format(_id))
      dt.getOrElse("0").toLong
    })
  }

  def unregister_worker() = {
    host.withClient(client => {
      client.srem("resque:workers", _id)
    })
    _started = None
    Stat("processed:%s".format(_id), _resq).clear
    Stat("failed:%s".format(_id), _resq).clear
  }

  //Verify that the worker has atleast one queue to listen on
  def validate_queues() {

  }

  //Replace this with event handlers from the akka event bus
  //Register a common message to stop worker actor for i.e
  def register_signal_handlers {

  }

  def register_worker() = {
    _resq.addWorker(_id)
    _started = Some(new DateTime())
    _set_started(_started)
  }

  def startup = {
    register_signal_handlers
    register_worker
  }

  def schedule_shutdown {
    _shutdown = true
  }

  def process(job: Job) {
    working_on(job)
    val original_job = job
    try {
      //Send a Message Perform to the Actor
      val future = job.performJob(context.system)
      future map {
        case _ =>
          self ! jobPassed(original_job)
      }
      future onFailure {
        case e: Exception =>
          self ! jobFailed(e, original_job)
      }
    } catch {
      case ex: Exception =>
        _handle_job_exception(ex, original_job)
        done_working
    }
  }
  def reserve(timeout: Int = 10) =
    //logger.debug('checking queues %s' % self.queues)
    Job.reserve(_resq, Some(_id), timeout, queues)

  def working_on(job: Job) {
    //logger.debug('marking as working on')
    val fmt = ISODateTimeFormat.dateTime()
    val now = new DateTime()
    host.withClient(client => {
      client.set("resque:worker:%s".format(_id),
        CompactPrinter(workerData(job._queue, fmt.print(now), job._payload).toJson))
    })
  }
  def done_working {
    processed
    host.withClient(client => {
      client.del("resque:worker:%s".format(_id))
    })
  }
  def processed {
    val total_processed = Stat("processed", _resq)
    val worker_processed = Stat("processed:%s".format(_id), _resq)
    total_processed.incr()
    worker_processed.incr()
  }
  def get_processed = {
    Stat("processed:%s".format(_id), _resq).get
  }
  def failed {
    Stat("failed", _resq).incr()
    Stat("failed:%s".format(_id), _resq).incr()
  }
  def get_failed = {
    Stat("failed:%s".format(_id), _resq).get
  }
  def processing =
    {
      job
    }
  def job = {
    host.withClient(client => {
      val data = client.get("resque:worker:%s".format(_id))
      data match {
        case None =>
          None
        case Some(d) =>
          val s = JsonParser(d).convertTo[workerData]
          Some(s)
      }
    })
  }
  def state = {
    host.withClient(client => {
      if (client.exists("resque:worker:%s".format(_id)))
        "working"
      else
        "idle"
    })
  }
  def _handle_job_exception(ex: Exception, job: Job) {
    job.fail(ex)
    failed
  }
}