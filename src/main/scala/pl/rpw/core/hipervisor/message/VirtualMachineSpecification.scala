package pl.rpw.core.hipervisor.message

import pl.rpw.core.ResourceType

class VirtualMachineSpecification(val resources: Map[ResourceType.Value, Int]) {

}
