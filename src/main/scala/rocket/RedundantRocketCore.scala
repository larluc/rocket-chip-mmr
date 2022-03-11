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

    // Connect imem (FrontendIO)
    core.io.imem.resp.bits := io.imem.resp.bits  /*Decoupled(new FrontendResp).flip*/
    core.io.imem.resp.valid := io.imem.resp.valid  /*Decoupled(new FrontendResp).flip*/

    core.io.imem.npc := io.imem.npc  /*UInt(INPUT, width = vaddrBitsExtended)*/
    core.io.imem.perf := io.imem.perf  /*new FrontendPerfEvents().asInput*/

    // Connect dmem (HellaCacheIO)
    core.io.dmem.req.ready := io.dmem.req.ready /*Decoupled(new HellaCacheReq)*/
    core.io.dmem.s2_nack := io.dmem.s2_nack  /*Bool(INPUT)*/
    core.io.dmem.s2_nack_cause_raw := io.dmem.s2_nack_cause_raw  /*Bool(INPUT)*/
    core.io.dmem.resp := io.dmem.resp  /*Valid(new HellaCacheResp).flip*/
    core.io.dmem.replay_next := io.dmem.replay_next  /*Bool(INPUT)*/
    core.io.dmem.s2_xcpt := io.dmem.s2_xcpt  /*(new HellaCacheExceptions).asInput*/
    core.io.dmem.ordered := io.dmem.ordered  /*Bool(INPUT)*/
    core.io.dmem.perf := io.dmem.perf  /*new HellaCachePerfEvents().asInput*/
    core.io.dmem.clock_enabled := io.dmem.clock_enabled  /**Bool(INPUT)*/

    // Connect interrupts (CoreInterrupts)
    core.io.interrupts := io.interrupts

    // Connect ptw (DatapathPTWIO)
    core.io.ptw.perf := io.ptw.perf  /*new PTWPerfEvents().asOutput*/

    // Connect fpu (FPUCoreIO)
    core.io.fpu.fcsr_flags := io.fpu.fcsr_flags    /*Valid(Bits(width = FPConstants.FLAGS_SZ))*/
    core.io.fpu.store_data := io.fpu.store_data    /*Bits(OUTPUT, fLen)*/
    core.io.fpu.toint_data := io.fpu.toint_data    /*Bits(OUTPUT, xLen)*/
    core.io.fpu.fcsr_rdy := io.fpu.fcsr_rdy        /*Bool(OUTPUT)*/
    core.io.fpu.nack_mem := io.fpu.nack_mem        /*Bool(OUTPUT)*/
    core.io.fpu.illegal_rm := io.fpu.illegal_rm    /*Bool(OUTPUT)*/
    core.io.fpu.dec := io.fpu.dec                  /*new FPUCtrlSigs().asOutput*/
    core.io.fpu.sboard_set := io.fpu.sboard_set    /*Bool(OUTPUT)*/
    core.io.fpu.sboard_clr := io.fpu.sboard_clra   /*Bool(OUTPUT)*/
    core.io.fpu.sboard_clra := io.fpu.sboard_clra  /*UInt(OUTPUT, 5)*/

    // Connect rocc (RoCCCoreIO)
    core.io.rocc.resp.bits := io.rocc.resp.bits  /*Decoupled(new RoCCResponse)*/
    core.io.rocc.resp.valid := io.rocc.resp.valid

    core.io.rocc.cmd.ready := io.rocc.cmd.ready  /*Decoupled(new RoCCCommand).flip*/

    core.io.rocc.busy := io.rocc.busy  /*Bool(OUTPUT)*/
    core.io.rocc.interrupt := io.rocc.interrupt  /*Bool(OUTPUT)*/

    core.io.rocc.mem.req.bits := io.rocc.mem.req.bits  /*Decoupled(new HellaCacheReq)*/
    core.io.rocc.mem.req.valid := io.rocc.mem.req.valid

    core.io.rocc.mem.s1_kill := io.rocc.mem.s1_kill  /*Bool(OUTPUT)*/
    core.io.rocc.mem.s1_data := io.rocc.mem.s1_data  /*new HellaCacheWriteData().asOutput*/
    core.io.rocc.mem.s2_kill := io.rocc.mem.s2_kill   /*Bool(OUTPUT)*/
    core.io.rocc.mem.keep_clock_enabled := io.rocc.mem.keep_clock_enabled  /*Bool(OUTPUT)*/

    core
  }

  // Connect imem (FrontendIO)
  io.imem.might_request := cores(0).io.imem.might_request  /*Bool(OUTPUT)*/
  io.imem.flush_icache := cores(1).io.imem.flush_icache  /*Bool(OUTPUT)*/
  io.imem.req := cores(2).io.imem.req  /*Valid(new FrontendReq)*/
  io.imem.sfence := cores(3).io.imem.sfence  /*Valid(new SFenceReq)*/
  io.imem.btb_update := cores(0).io.imem.btb_update  /*Valid(new BTBUpdate)*/
  io.imem.bht_update := cores(1).io.imem.bht_update  /*Valid(new BHTUpdate)*/
  io.imem.ras_update := cores(2).io.imem.ras_update  /*Valid(new RASUpdate)*/

  io.imem.resp.ready := cores(0).io.imem.resp.ready  /*Decoupled(new FrontendResp).flip*/

  // Connect dmem (HellaCacheIO)
  io.dmem.req.bits := cores(0).io.dmem.req.bits  /*Decoupled(new HellaCacheReq)*/
  io.dmem.req.valid := cores(1).io.dmem.req.valid  /*Decoupled(new HellaCacheReq)*/
  io.dmem.s1_kill := cores(2).io.dmem.s1_kill  /*Bool(OUTPUT)*/
  io.dmem.s1_data := cores(3).io.dmem.s1_data  /*new HellaCacheWriteData().asOutput*/
  io.dmem.s2_kill := cores(0).io.dmem.s2_kill  /*Bool(OUTPUT)*/
  io.dmem.keep_clock_enabled := cores(1).io.dmem.keep_clock_enabled  /*Bool(OUTPUT)*/

  // Connect trace (TracedInstruction)
  io.trace := cores(0).io.trace

  // Connect ptw (DatapathPTWIO)
  io.ptw.ptbr := cores(0).io.ptw.ptbr  /*new PTBR().asInput*/
  io.ptw.sfence := cores(1).io.ptw.sfence  /*Valid(new SFenceReq).flip*/
  io.ptw.status := cores(2).io.ptw.status  /*new MStatus().asInput*/
  io.ptw.pmp := cores(3).io.ptw.pmp  /*Vec(nPMPs, new PMP).asInput*/
  io.ptw.customCSRs := cores(0).io.ptw.customCSRs  /*coreParams.customCSRs.asInput*/

  // Connect fpu (FPUCoreIO)
  io.fpu.inst := cores(0).io.fpu.inst  /*Bits(INPUT, 32)*/
  io.fpu.fromint_data := cores(1).io.fpu.fromint_data  /*Bits(INPUT, xLen)*/
  io.fpu.fcsr_rm := cores(2).io.fpu.fcsr_rm  /*Bits(INPUT, FPConstants.RM_SZ)*/
  
  io.fpu.dmem_resp_val := cores(0).io.fpu.dmem_resp_val    /*Bool(INPUT)*/
  io.fpu.dmem_resp_type := cores(1).io.fpu.dmem_resp_type  /*Bits(INPUT, 3)*/
  io.fpu.dmem_resp_tag := cores(2).io.fpu.dmem_resp_tag    /*UInt(INPUT, 5)*/
  io.fpu.dmem_resp_data := cores(3).io.fpu.dmem_resp_data  /*Bits(INPUT, fLen)*/

  io.fpu.valid := cores(0).io.fpu.valid  /*Bool(INPUT)*/
  io.fpu.killx := cores(1).io.fpu.killx  /*Bool(INPUT)*/
  io.fpu.killm := cores(2).io.fpu.killm  /*Bool(INPUT)*/
  io.fpu.keep_clock_enabled := cores(3).io.fpu.keep_clock_enabled  /*Bool(INPUT)*/

  // Connect rocc (RoCCCoreIO)
  io.rocc.resp.ready := cores(0).io.rocc.resp.ready && cores(1).io.rocc.resp.ready  /*Decoupled(new RoCCResponse)*/
  io.rocc.cmd.bits := cores(1).io.rocc.cmd.bits  /*Decoupled(new RoCCCommand).flip*/
  io.rocc.cmd.valid := cores(2).io.rocc.cmd.valid  /*Decoupled(new RoCCCommand).flip*/
  io.rocc.exception := cores(3).io.rocc.exception  /*Bool(INPUT)*/

  io.rocc.mem.req.ready := cores(0).io.rocc.mem.req.ready && cores(1).io.rocc.mem.req.ready  /*Decoupled(new HellaCacheReq)*/

  io.rocc.mem.s2_nack := cores(0).io.rocc.mem.s2_nack  /*Bool(INPUT)*/
  io.rocc.mem.s2_nack_cause_raw := cores(1).io.rocc.mem.s2_nack_cause_raw  /*Bool(INPUT)*/

  io.rocc.mem.resp := cores(0).io.rocc.mem.resp  /*Valid(new HellaCacheResp).flip*/
  io.rocc.mem.replay_next := cores(1).io.rocc.mem.replay_next  /*Bool(INPUT)*/
  io.rocc.mem.s2_xcpt := cores(2).io.rocc.mem.s2_xcpt  /*(new HellaCacheExceptions).asInput*/
  io.rocc.mem.ordered := cores(3).io.rocc.mem.ordered  /*Bool(INPUT)*/
  io.rocc.mem.perf := cores(0).io.rocc.mem.perf  /*new HellaCachePerfEvents().asInput*/
  io.rocc.mem.clock_enabled := cores(1).io.rocc.mem.clock_enabled  /**Bool(INPUT)*/
}