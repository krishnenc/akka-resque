package org.akkaresque.demo

import akka.actor.{ Actor, ActorRef, PoisonPill, ActorLogging, ActorSystem, Props, ReceiveTimeout }
import org.akkaresque.perform
import org.akkaresque.ResQ
import org.akkaresque.Worker

object Demo {
  def main(args: Array[String]): Unit = {
    try {
      println("OK")
      val r = ResQ("localhost", 6379)
      val testActorSystem = ActorSystem("TestApplication")
      val ref = testActorSystem.actorOf(Props(new TestActor), "test")
      println(ref.path.toString)
      r.enqueue(ref.path.toString, "Spam", List("1", "2", "3"))
      r.enqueue(ref.path.toString, "Soda", List("4", "5", "6"))
      println("Queued")
      r.redis_cli.disconnect
      //Create a worker which will handle the job queued
      val worker = Worker(testActorSystem, List("Spam","Soda"), "localhost", 6379, 5,5)
      //Wait for Job to finish
      //Thread.sleep(5000)
      //worker ! PoisonPill
    } catch {
      case ex: Exception =>
        print(ex.printStackTrace.toString)
    }
  }
}

class TestActor
  extends Actor with ActorLogging {
  def receive = {
    case perform(args) =>
      try {
        log.info("Got a Job" + args)
        //Fail the job 
        val uugh = 1 / 0
        //Do work
        sender ! "Done"
      } catch {
        case e: Exception =>
          sender ! akka.actor.Status.Failure(e)
      }
  }
}


