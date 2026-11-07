/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */

package de.juergens.rule

import java.util.function.Predicate
import scala.languageFeature.implicitConversions

/**
 * [[java.util.function.Predicate]]
 * @constructor create a predicates as and composition
 * @param predicates tany predicates on type T
 * @tparam T
 */
case class IntersectionPredicate[T](predicates: Predicate[T]*) extends Predicate[T] {

  /**
   * Checks if all of the predicates matches for the specified object.
   * @param t an object to test
   * @return and-composition
   */
  def test(t: T) = predicates.forall(_.test(t)) // predicates.foldRight(true)((r, b) => b & r.test(t))

  override def toString = predicates.mkString("&")
}
