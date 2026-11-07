/*
 * Copyright 2015 Hartmut Jürgens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.juergens.time.predicate

import java.time.DayOfWeek
import java.time.temporal.{TemporalAccessor, TemporalQuery}
import java.util.function.Predicate

/**
 * date 29.08.15
 * @author juergens
 */
class Attribute(accessor: TemporalAccessor)
  extends Predicate[TemporalAccessor] {

  override def toString = s"Predicate(temporalAccessor=$accessor)"

  object Filter extends TemporalQuery[Boolean] {
    def matchAttribute(t: TemporalAccessor) =     accessor match {
      case dayOfWeek : DayOfWeek => DayOfWeek.from(t) == dayOfWeek
    }
    override def queryFrom(temporal: TemporalAccessor) = matchAttribute(temporal)
  }

  override def test(t: TemporalAccessor): Boolean = {
    t.query(Filter)
  }
}
