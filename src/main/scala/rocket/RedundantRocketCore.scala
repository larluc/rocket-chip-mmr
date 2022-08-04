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
import chisel3.core.dontTouch

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

  def coreInRec() : Record = new DirectedRecord(io, ActualDirection.Input)
  def coreOutRec() : Record = new DirectedRecord(io, ActualDirection.Output)

  val coreInWire = Wire(coreInRec())
  connectRecordElementsByName(io.elements, coreInWire.elements)

  // Instantiate ConfigurableVoter
  val configurableVoter = Module(new ConfigurableVoter(coreOutRec().getWidth, numberOfCores))
  configurableVoter.io.sel := RegNext(io.coreredunconf.num)

  // Instantiate fault-free core
  val faultFreeCore = Module(new Rocket()(p))

  connectRecordElementsByName(coreInWire.elements, faultFreeCore.io.elements)
  val faultFreeCoreOutWire = Wire(coreOutRec())
  connectRecordElementsByName(faultFreeCore.io.elements, faultFreeCoreOutWire.elements)
  val faultFreeOut = dontTouch(Wire(UInt(coreOutRec().getWidth.W)))
  faultFreeOut := faultFreeCoreOutWire.asUInt

  // Fault injection

  // Instantiate 16-bit Fibonacci LFSR
  val lfsr = RegInit(1.U(16.W))
  // taps: 16, 14, 13, 11
  lfsr := Cat(lfsr(0)^lfsr(2)^lfsr(3)^lfsr(5), lfsr(15,1))
  val lfsr_wire = dontTouch(Wire(UInt(16.W)))
  lfsr_wire := lfsr
  
  // Instantiate redundant cores
  val cores =
    for (i <- 0 until numberOfCores) yield {
      val core = Module(new Rocket()(p))

      // Connect RedundantRocket input to cores
      connectRecordElementsByName(coreInWire.elements, core.io.elements)

      // Connect core outputs to voter
      val coreOutWire = dontTouch(Wire(coreOutRec()))
      connectRecordElementsByName(core.io.elements, coreOutWire.elements)
      configurableVoter.io.in(i) := coreOutWire.asUInt

      // Config fault injection

      core.io.faultconf.inject := lfsr(i*2) && lfsr(i*2 + 1)
      // core.io.faultconf.inject := lfsr(i*3) && lfsr(i*3 + 1) && lfsr(i*3 + 2)

      core.io.faultconf.faultmask := (1.U << i)

      core
    }

  connectRecordElementsByName(faultFreeCoreOutWire.elements, io.elements)
}
