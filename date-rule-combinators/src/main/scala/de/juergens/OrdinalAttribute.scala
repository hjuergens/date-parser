package de.juergens

import java.time.temporal._
import java.time.{LocalDate => Date}

import de.juergens.util.Ordinal

abstract class OrdinalAttribute(ordinal:Ordinal, val predicate:(TemporalAccessor)=>Boolean)
  extends TemporalAdjuster



