package com.cloudera.hue.livy.server.sessions

import com.cloudera.hue.livy.yarn.{Client, Job}
import org.apache.hadoop.fs.Path
import org.apache.hadoop.yarn.api.ApplicationConstants

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, TimeoutException}

object YarnSession {
  private val LIVY_YARN_PACKAGE = System.getenv("LIVY_YARN_PACKAGE")

  protected implicit def executor: ExecutionContextExecutor = ExecutionContext.global

  def create(client: Client, id: String, lang: String): Future[Session] = {
    val packagePath = new Path(LIVY_YARN_PACKAGE)

    val job = client.submitApplication(
      "livy " + lang,
      packagePath,
      List(
        "__package/bin/run-am.sh %s 1>%s/stdout 2>%s/stderr" format (
          lang,
          ApplicationConstants.LOG_DIR_EXPANSION_VAR,
          ApplicationConstants.LOG_DIR_EXPANSION_VAR
          )
      )
    )

    Future {
      var x = job.waitForRPC(100000)

      println("x: %s" format x)

      x match {
        case Some((hostname, port)) =>
          new YarnSession(id, job, hostname, port)
        case None =>
          throw new TimeoutException()
      }
    }
  }
}

private class YarnSession(id: String, job: Job, hostname: String, port: Int)
  extends WebSession(id, hostname, port) {

  override def stop(): Future[Unit] = {
    super.stop() andThen { case r =>
      job.waitForFinish(10000)
      r
    }
  }

}
