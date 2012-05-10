package org.akkaresque.failure

import org.akkaresque.Payload
import org.akkaresque.Worker

trait BaseBackend {

  def _parse_traceback(trace: String) = {
    """Return the given traceback string formatted for a notification."""
    trace.split('\n')
  }
  def _parse_message(exc: Exception) = {
    "%s: %s".format(exc.getMessage, exc.getStackTraceString)
  }

}