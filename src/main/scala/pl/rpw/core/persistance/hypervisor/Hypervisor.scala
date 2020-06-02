package pl.rpw.core.persistance.hypervisor

case class Hypervisor(id: String, state: String, cpu: Int, ram: Int, disk: Int, freeCpu: Int, freeRam: Int, freeDisk: Int)
