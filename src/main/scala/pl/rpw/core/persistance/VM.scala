package pl.rpw.core.persistance

case class VM(id: String, state: String, cpu: Int, ram: Int, disk: Int, user: String, hypervisor: String, freeCpu: Int, freeRam: Int, freeDisk: Int)

