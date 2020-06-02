package pl.rpw.core.persistance.vm

case class VM(id: String, var state: String, cpu: Int, ram: Int, disk: Int, user: String, var hypervisor: String, var freeCpu: Int, var freeRam: Int, var freeDisk: Int)
