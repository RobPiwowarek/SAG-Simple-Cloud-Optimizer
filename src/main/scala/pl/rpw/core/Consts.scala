package pl.rpw.core

import pl.rpw.core.persistance.hypervisor.Hypervisor

object Consts {
  val overprovisioningThreshold = 0.90
  val underprovisioningThreshold = 0.70
  val EmptyHypervisor: Hypervisor = Hypervisor("EMPTY", "IDLE", 0, 0, 0, 0, 0, 0)
}
