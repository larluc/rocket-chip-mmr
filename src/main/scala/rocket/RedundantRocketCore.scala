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


class DirectedRecord(record: Record, direction: ActualDirection) extends Record {
  val elements = 
    for ((name, data) <- record.elements
      // Only iterate over Data if it is Record or if direction matches
      if ((data.isInstanceOf[Record] || DataMirror.directionOf(data.asInstanceOf[Data]) == direction)
        && {
          // Avoid zero width Aggregates/Data
          if (data.isInstanceOf[Record]) {
            (new DirectedRecord(data.asInstanceOf[Record], direction)).getWidth > 0
          }
          else {
            data.getWidth > 0
          }
        }))
      yield {
        if (data.isInstanceOf[Record]) {
          (name -> new DirectedRecord(data.asInstanceOf[Record], direction))
        }
        else {
          (name -> data.asInstanceOf[Data].cloneType)
        }
      }

  override def cloneType: this.type = (new DirectedRecord(record, direction)).asInstanceOf[this.type]
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
}

class RedundantRocket(numberOfCores: Int)(implicit p: Parameters) extends CoreModule()(p)
    with HasRocketCoreParameters
    with HasCoreIO
    with RecordHelperFunctions {
  
  require(numberOfCores > 1)

  def coreInRec() : Record = new DirectedRecord(io, ActualDirection.Input)
  def coreOutRec() : Record = new DirectedRecord(io, ActualDirection.Output)

  require(io.getWidth == coreInRec().getWidth + coreOutRec().getWidth)

  // Instantiate MultiVoter, use all cores
  val multiVoter = Module(new MultiVoter(coreOutRec().getWidth, numberOfCores))
  multiVoter.io.sel := (numberOfCores - 1).U

  val coreInWire = Wire(coreInRec())
  connectRecordElementsByName(io.elements, coreInWire.elements)

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
}