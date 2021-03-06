// Copyright (C) 2017 Calin Cruceru <calin.cruceru@stud.acs.upb.ro>.
//
// See the LICENCE file distributed with this work for additional
// information regarding copyright ownership.

package org.symnet
package models.iptables.virtdev
package devices
package ivds

import org.change.v2.analysis.expression.concrete.ConstantValue
import org.change.v2.analysis.expression.concrete.nonprimitive.:@
import org.change.v2.analysis.processingmodels.instructions._
import org.change.v2.util.canonicalnames.{IPDst, IPSrc, TcpDst, TcpSrc}

import models.iptables.core.{Chain, Table}

// TODO: A `Chain' should probably suffice if we re-design it.
trait ChainIVDInitializerConfig {
  val deviceId: String
  val chain: Chain
  val table: Table
}

case class ChainIVDInitializer(
    name:   String,
    config: ChainIVDInitializerConfig)
  extends RegularVirtualDevice[ChainIVDInitializerConfig](
    name,
      // one input port
    1,
      // two output ports:
      //  * 0 - continue
      //  * 1 - skip
    2,
    config) {

  def inputPort:  Port = inputPort(0)

  def continuePort: Port = outputPort(0)
  def skipPort:     Port = outputPort(1)

  override def portInstructions: Map[Port, Instruction] = {
    val snatOrigSrc = snatFromIp(config.deviceId)
    val snatOrigPort = snatFromPort(config.deviceId)
    val snatNewSrc = snatToIp(config.deviceId)
    val snatNewPort = snatToPort(config.deviceId)

    val dnatOrigDst = dnatFromIp(config.deviceId)
    val dnatOrigPort = dnatFromPort(config.deviceId)
    val dnatNewDst = dnatToIp(config.deviceId)
    val dnatNewPort = dnatToPort(config.deviceId)

    List(
      Map(inputPort -> InstructionBlock(
        // Generate code for NAT handling.
        if (config.table.name == "nat") {
          if (config.chain.name == "PREROUTING") {
            InstructionBlock(
              // Reuse existing DNAT mapping.
              If(Constrain(IPDst, :==:(:@(dnatOrigDst))),
                 If(Constrain(TcpDst, :==:(:@(dnatOrigPort))),
                    InstructionBlock(
                      Assign(IPDst, :@(dnatNewDst)),
                      Assign(TcpDst, :@(dnatNewPort)),
                      Forward(skipPort)),
                    NoOp),
                 NoOp),

              // Rewrite dst in a reply to a SNAT'ed packet.
              // NOTE: It still passes through the chain (this is where
              // --ctstate SNAT can be applied, in fact).
              If(Constrain(IPDst, :==:(:@(snatNewSrc))),
                 If(Constrain(TcpDst, :==:(:@(snatNewPort))),
                    InstructionBlock(
                      Assign(IPDst, :@(snatOrigSrc)),
                      Assign(TcpDst, :@(snatOrigPort))),
                    NoOp),
                 NoOp)
            )
          } else if (config.chain.name == "POSTROUTING") {
            InstructionBlock(
              // Reuse existing SNAT mapping.
              If(Constrain(IPSrc, :==:(:@(snatOrigSrc))),
                 If(Constrain(TcpSrc, :==:(:@(snatOrigPort))),
                    InstructionBlock(
                      Assign(IPSrc, :@(snatNewSrc)),
                      Assign(TcpSrc, :@(snatNewPort)),
                      Forward(skipPort)),
                    NoOp),
                 NoOp),

              // Rewrite src in a reply to a DNAT'ed packet.
              // NOTE: It still passes through the chain (this is where
              // --ctstate DNAT can be applied, in fact).
              If(Constrain(IPSrc, :==:(:@(dnatNewDst))),
                 If(Constrain(TcpSrc, :==:(:@(dnatNewPort))),
                    InstructionBlock(
                      Assign(IPSrc, :@(dnatOrigDst)),
                      Assign(TcpSrc, :@(dnatOrigPort))),
                    NoOp),
                 NoOp)
            )
          } else {
            NoOp
          }
        } else {
          NoOp
        },

        // Initialize the input dispatch tag: allocate a new one and initialize
        // it to zero.
        Allocate(InputDispatchTag),
        Assign(InputDispatchTag, ConstantValue(0)),
        Forward(continuePort))),

      Map(skipPort -> Fail(s"Packet skipped by $name"))
    ).flatten.toMap
  }
}
