// See LICENSE.SiFive for license details.

package freechips.rocketchip.tilelink

import Chisel._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._

/** Specifies widths of various attachement points in the SoC */
trait HasTLBusParams {
  val beatBytes: Int
  val blockBytes: Int

  def beatBits: Int = beatBytes * 8
  def blockBits: Int = blockBytes * 8
  def blockBeats: Int = blockBytes / beatBytes
  def blockOffset: Int = log2Up(blockBytes)
}

abstract class TLBusWrapper(params: HasTLBusParams, val busName: String)(implicit p: Parameters)
    extends SimpleLazyModule with LazyScope with HasTLBusParams {

  val beatBytes = params.beatBytes
  val blockBytes = params.blockBytes
  require(blockBytes % beatBytes == 0)

  def inwardNode: TLInwardNode
  def outwardNode: TLOutwardNode

  protected def bufferFrom(buffer: BufferParams): TLInwardNode =
    inwardNode :=* TLBuffer(buffer)

  protected def fixFrom(policy: TLFIFOFixer.Policy, buffer: BufferParams): TLInwardNode =
    inwardNode :=* TLBuffer(buffer) :=* TLFIFOFixer(policy)

  protected def bufferTo(buffer: BufferParams): TLOutwardNode =
    TLBuffer(buffer) :*= outwardNode

  protected def fixedWidthTo(buffer: BufferParams): TLOutwardNode =
    TLWidthWidget(beatBytes) :*= bufferTo(buffer)

  protected def fragmentTo(buffer: BufferParams): TLOutwardNode =
    TLFragmenter(beatBytes, blockBytes) :*= bufferTo(buffer)

  protected def fragmentTo(minSize: Int, maxSize: Int, buffer: BufferParams): TLOutwardNode =
    TLFragmenter(minSize, maxSize) :*= bufferTo(buffer)

  def to[T](name: String)(body: => T): T = {
    this { LazyScope(s"coupler_to_${name}") { body } }
  }

  def from[T](name: String)(body: => T): T = {
    this { LazyScope(s"coupler_from_${name}") { body } }
  }
}

trait HasTLXbarPhy { this: TLBusWrapper =>
  private val xbar = LazyModule(new TLXbar).suggestName(busName + "_xbar")

  def inwardNode: TLInwardNode = xbar.node
  def outwardNode: TLOutwardNode = xbar.node
}
