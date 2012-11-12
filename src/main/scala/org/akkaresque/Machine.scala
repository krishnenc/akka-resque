package org.akkaresque

import java.net.InetAddress
import java.lang.management.ManagementFactory

object Machine {
    def hostname = InetAddress.getLocalHost.getHostName
    def pid = ManagementFactory.getRuntimeMXBean.getName.split("@").head
}


