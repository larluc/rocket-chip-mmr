// TODO: License?
// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.redundantrocket

import Chisel._
import Chisel.ImplicitConversions._
import chisel3.experimental._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._
import freechips.rocketchip.scie._
import freechips.rocketchip.rocket._

class RedundantRocket(numberOfCores: Int)(implicit p: Parameters) extends CoreModule()(p)
    with HasRocketCoreParameters
    with HasCoreIO {
  
  require(numberOfCores > 1)

  val cores = for (i <- 0 until numberOfCores) yield
  {
    val core = Module(new Rocket()(p))
    core.io.hartid := io.hartid
    core
  }
  
  io.imem := cores(0).io.imem
  io.dmem := cores(0).io.dmem
  io.trace := cores(0).io.trace

  cores(0).io.interrupts := io.interrupts
  cores(0).io.ptw := io.ptw
  cores(0).io.fpu := io.fpu
  cores(0).io.rocc :=io.rocc
}