package pl.rpw.core.persistance.hypervisor

case class Hypervisor(id: String, var state: String, cpu: Int, ram: Int, disk: Int, var freeCpu: Int, var freeRam: Int, var freeDisk: Int)
