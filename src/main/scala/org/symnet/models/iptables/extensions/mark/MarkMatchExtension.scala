// Copyright (C) 2017 Calin Cruceru <calin.cruceru@stud.acs.upb.ro>.
//
// See the LICENCE file distributed with this work for additional
// information regarding copyright ownership.

package org.symnet
package models.iptables
package extensions.mark

// 3rd-party
// -> Symnet
import org.change.v2.analysis.expression.concrete.ConstantBitVector
import org.change.v2.analysis.expression.concrete.nonprimitive.{:@, <|>, <&>}
import org.change.v2.analysis.processingmodels.instructions._

// project
import core._

object MarkModuleLoader extends ModuleLoader {
  override def loaderParser: Parser[Match] =
    iptParsers.moduleLoaderParser("mark", new ModuleLoaderMatch {
      type Self = this.type
      override def extensionsEnabled = List(MarkMatchExtension)
    })
}

object MarkMatchExtension extends MatchExtension {
  override val matchParsers: List[Parser[Match]] = List(
    MarkMatch.parser((value, maybeMask) => MarkMatch(value, maybeMask))
  )
}

case class MarkMatch(value: Long, maybeMask: Option[Long]) extends Match {
  type Self = MarkMatch

  override def seflCondition(options: SeflGenOptions): SeflCondition = {
    val nfmarkTag = virtdev.nfmarkTag(options.deviceId)
    val nfmarkTmpTag = nfmarkTag + "-tmp"
    val mask = maybeMask getOrElse 0xFFFFFFFFL

    // TODO: Should we Allocate/Deallocate the tmp?  See IPMirror in the click
    // model.
    SeflCondition.single(
      initInstr =
        Assign(nfmarkTmpTag, <&>(:@(nfmarkTag), ConstantBitVector(mask))),
      constraint = Constrain(nfmarkTmpTag, :==:(ConstantBitVector(value)))
    )
  }
}

object MarkMatch extends BaseParsers {
  import ParserMP.monadPlusSyntax._

  type MarkMatchFactory = (Long, Option[Long]) => Match

  def parser(factory: MarkMatchFactory): Parser[Match] =
    for {
      _ <- spacesParser
      n1 <- optional(parseChar('!') >> someSpacesParser)
      _ <- parseString("--mark")

      // TODO: Change this to a general number (u32) parser.
      value <- someSpacesParser >> hexLongParser
      maybeMask <- optional(parseChar('/') >> hexLongParser)
    } yield Match.maybeNegated(factory(value, maybeMask), n1)
}
