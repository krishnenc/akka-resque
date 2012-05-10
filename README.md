Akka-Resque
===========
A Resque clone written using Scala/Akka

[Resque](http://github.com/defunkt/resque) is a fantastic background job processing software written by the github developers.
The web interface provides lots of cool metrics on background jobs (number processed, failed etc..).
I principally work on Scala, so i took the task of starting to port the background part
to Scala using [Akka](https://github.com/akka/akka) actors for concurrency. I can then use resque-web to see how jobs enqueued in Akka were processed.  
I was heavily inspired by the work done on [Pyres](http://github.com/binarydud/pyres) and [scala-resque-worker](jamesgolick/scala-resque-worker)

Installation
------------
Use sbt to build the project. Currently it depends on version 0.11.2. Publish it to your local ivy repo and add it as a dependency to your project.

* sbt compile
* sbt publish-local

To add as a dependency type the following in your build.sbt

libraryDependencies += "org.akkaresque" %% "akka-resque" % "0.0.1"

How it Works
------------

* Enqueing Jobs
  
  To enqueue a job :
     
     Use the ResQ object.The constructor expects the Redis connection params. If none provided it assumes the default localhost and port 6379.
     The enqueue method accepts as parameters either the path of the Actor which will perform the job or its Actor Reference and a list of arguments to pass to its perform message.
     It currently accepts only a list of string as arguments for the moment.
     
     <pre lang="scala"><code>  
     import org.akkaresque.ResQ
     
     object Demo {
      	def main(args: Array[String]): Unit = {
  			val res = ResQ("localhost", 6379) 
  			res.enqueue("akka://MyApplication/user/MyWorker", "Spam", List("1", "2", "3"))
  		}
  	  }
     </code></pre>
     
* Workers   

  The workers need to be started using the org.akkaresque.Worker object.
  The constructor expects the following values : 
    * system : The Actor System where the worker will be created
    * queues : A list of queues that the worker has to listen to
    * redisServer : The address of the Redis server 
    * redisPort : The port of the Redis server
    * timeout : The amount in seconds it should wait on the queues when doing a BLPOP command
    * interval : It will poll the queues in the interval specified in seconds
    
  You can start as many workers as you want. To create a worker and start it :
     
    <pre lang="scala"><code>  
     import akka.actor.{ Actor , ActorSystem }
     import org.akkaresque.Worker
     
     object Demo {
     	def main(args: Array[String]): Unit = {
			val testActorSystem = ActorSystem("MyApplication")
			val worker = Worker(testActorSystem, List("Spam","Soda"), "localhost", 6379, 5,5)     	   
     	}
     }
    </code></pre>
  
  The worker reference is returned back. You can choose to keep it and kill the actor later by either stopping it or sending it a poison pill. 
  This will take care of cleaning up the resources taken by the worker.

  This implementation of Resque assumes that the jobs will be run using Akka actors.The only requirement for actors that perform the jobs 
  is that they should have a case class message called perform (org.akkaresque.perform) 
  handled in their receive method.(At the moment i cannot find a way to enforce that yet, so it has to be by convention for now)

  An example actor which would handle the job created above :
      
     <pre lang="scala"><code>  
      import akka.actor.{ Actor , ActorSystem }
      import org.akkaresque.perform
  
	  class TestActor
	  		extends Actor {
	  def receive = {
	    case perform(args) =>
	      try {
	        //Do something dangerous
	        sender ! "Done"
	      } catch {
	        case e: Exception =>
	          sender ! akka.actor.Status.Failure(e)
	      }
	  }
	}
    </code></pre>

  You need to reply to the worker that sent the job with a message to indicate that it completed. On failure always send akka.actor.Status.Failure so it knows it fails and logs it as such
  The only difference in resque web is the workers are named differently and the class names are replaced with actor reference paths.

Gotchas
-------
  1. Scala is statically typed, so, you can only pass arguments that can get parsed in to List[String]. Probably not a big deal in practice, but worth knowing about.
  2. This is not yet production code!. I still need to test it properly.

TODO
----
* Write a complete test suite
* Provide Examples
* Allow usage of configuration files to configure it.

Patches
-------

This is my first open source scala project. I'd love some feedback, patches, etc... to improve this. 

Credits
-------

[Pyres](http://github.com/binarydud/pyres), [scala-resque-worker](jamesgolick/scala-resque-worker) and of course [Resque](http://github.com/defunkt/resque) itself.
The Base64 implementation was lifted from [totoshi](https://github.com/tototoshi/scala-base64)


Disclaimer
-------
I have a few dependencies in this project. So if i am misusing some license please let me know!

License
------

akka-resque is copyright (c) 2012 Krishnen Chedambarum, released under the terms of the MIT License. See the LICENSE file for details.


