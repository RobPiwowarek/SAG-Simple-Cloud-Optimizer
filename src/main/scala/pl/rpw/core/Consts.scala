package pl.rpw.core

import pl.rpw.core.persistance.hypervisor.Hypervisor
import pl.rpw.core.persistance.vm.VM

object Consts {
  val overprovisioningThreshold = 0.90
  val underprovisioningThreshold = 0.70
  val EmptyHypervisor: Hypervisor = Hypervisor("EMPTY", "IDLE", 0, 0, 0, 0, 0, 0)
  val EmptyVM: VM = VM("EMPTY", "IDLE", 0, 0, 0, "emptyuser", "emptyvisor", 0, 0, 0)
}
