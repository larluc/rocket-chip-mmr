// TODO: License?
// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.redundantrocket

import Chisel._

import freechips.rocketchip.config._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._

import chisel3.core.ActualDirection
import chisel3.experimental.DataMirror

import scala.collection.immutable.ListMap


class DirectedRecord(record: Record, direction: ActualDirection, exclude: Set[Seq[String]] = Set()) extends Record {
  val elements = 
    for ((name, data) <- record.elements
      // Only iterate over Data if it is Record or if direction matches
      if ((data.isInstanceOf[Record] || DataMirror.directionOf(data.asInstanceOf[Data]) == direction)
        && {
          // Avoid zero width Aggregates/Data
          if (data.isInstanceOf[Record]) {
            // Generate exclude list for sub aggregates
            val subExclude = exclude.filter(_.head == name).map(_.drop(1))
            
            (new DirectedRecord(data.asInstanceOf[Record], direction, subExclude)).getWidth > 0
          }
          else {
            data.getWidth > 0
          }
        }
        &&
          // Do not iterate over excluded aggregates
          !exclude.contains(Seq(name))
        ))
      yield {
        if (data.isInstanceOf[Record]) {
          // Generate exclude list for sub aggregates
          val subExclude = exclude.filter(_.head == name).map(_.drop(1))

          (name -> new DirectedRecord(data.asInstanceOf[Record], direction, subExclude))
        }
        else {
          (name -> data.asInstanceOf[Data].cloneType)
        }
      }

  override def cloneType: this.type = (new DirectedRecord(record, direction, exclude)).asInstanceOf[this.type]
}

trait RecordHelperFunctions {
  def connectRecordElementsByName(src: ListMap[String, Any], dest: ListMap[String, Any], direction: Option[ActualDirection] = None) : Unit = {
    for ((name, data) <- dest) {
      // Only connect if element also exists in source and direction matches if specified
      if (src.contains(name) && (data.isInstanceOf[Record] || direction.isEmpty || DataMirror.directionOf(data.asInstanceOf[Data]) == direction.get)) {
        if (data.isInstanceOf[Record]) {
          connectRecordElementsByName(src(name).asInstanceOf[Record].elements, data.asInstanceOf[Record].elements, direction)
        }
        else {
          dest(name).asInstanceOf[Data] := src(name).asInstanceOf[Data]
        }
      }
    }
  }

  def connectRecordElement(src: ListMap[String, Any], dest: ListMap[String, Any], path: Seq[String]) : Unit = {
    if (path.size > 1) {
      connectRecordElement(
        src(path.head).asInstanceOf[Record].elements,
        dest(path.head).asInstanceOf[Record].elements,
        path.drop(1)
      )
    }
    else {
      dest(path.head).asInstanceOf[Data] := src(path.head).asInstanceOf[Data]
    }
  }
}

class RedundantRocket(numberOfCores: Int)(implicit p: Parameters) extends CoreModule()(p)
    with HasRocketCoreParameters
    with HasCoreIO
    with RecordHelperFunctions {
  
  require(numberOfCores > 1)

  val exclude = Set(
      "dmem.s2_kill",
      "dmem.s1_data.mask",
      "dmem.req.bits.phys",
      "dmem.req.bits.typ",
      "dmem.req.bits.cmd",
      "dmem.req.valid",
      "ptw.ptbr.mode",
      "ptw.status.dprv"
    ).map(_.split("\\.").toSeq)

  def coreInRec() : Record = new DirectedRecord(io, ActualDirection.Input, exclude)
  def coreOutRec() : Record = new DirectedRecord(io, ActualDirection.Output, exclude)

  //require(io.getWidth == coreInRec().getWidth + coreOutRec().getWidth)

  // Instantiate MultiVoter, use all cores
  val multiVoter = Module(new MultiVoter(coreOutRec().getWidth, numberOfCores))
  multiVoter.io.sel := (numberOfCores - 1).U

  val coreInWire = Wire(coreInRec())
  connectRecordElementsByName(io.elements, coreInWire.elements)

  // Instantiate fault-free core
  val faultFreeCore = Module(new Rocket()(p))

  connectRecordElementsByName(coreInWire.elements, faultFreeCore.io.elements)

  // Instantiate redundant cores
  val cores =
    for (i <- 0 until numberOfCores) yield {
      val core = Module(new Rocket()(p))

      // Connect RedundantRocket input to cores
      connectRecordElementsByName(coreInWire.elements, core.io.elements)

      // Connect core outputs to voter
      val coreOutWire = Wire(coreOutRec())
      connectRecordElementsByName(core.io.elements, coreOutWire.elements)
      multiVoter.io.in(i) := coreOutWire.asUInt

      core
    }

  // Use voter output as RedundantRocket output
  val coreOutWire = Wire(coreOutRec())
  coreOutWire := multiVoter.io.out.asTypeOf(coreOutRec())
  connectRecordElementsByName(coreOutWire.elements, io.elements)

  // Take output signals that would lead to comb loop from fault-free core
  for (path <- exclude) {
    connectRecordElement(faultFreeCore.io.elements, io.elements, path)
  }
}