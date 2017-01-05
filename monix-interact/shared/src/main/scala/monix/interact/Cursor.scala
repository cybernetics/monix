/*
 * Copyright (c) 2014-2017 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
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

package monix.interact

import monix.interact.cursors.{ArrayCursor, IteratorCursor}

/** Similar to Java's and Scala's `Iterator`, the `Cursor` type can
  * can be used to iterate over the data in a collection, but it cannot
  * be used to modify the underlying collection.
  *
  * Inspired by the standard `Iterator`, but also by the C# `IEnumerator`,
  * it exists because [[Iterant]] needs a way to efficiently apply
  * operations such as `map`, `filter`, `collect` on the underlying
  * collection without such operations having necessarily lazy behavior.
  * So in other words, when wrapping a standard `Array`, an application of
  * `map` will copy the data to a new `Array` instance with its elements
  * modified, immediately and is thus having strict behavior. In other case,
  * when wrapping potentially infinite collections, like `Iterable` or `Stream`,
  * that's when lazy behavior happens.
  *
  * Sample:
  * {{{
  *   try while (cursor.moveNext()) {
  *     println(cursor.current)
  *   }
  *   catch {
  *     case NonFatal(ex) => report(ex)
  *   }
  * }}}
  *
  * Contract:
  *
  *  - the [[monix.interact.Cursor#current current]] method does not trigger
  *    any side-effects, it simply caches the last generated value and can
  *    be called multiple times (in contrast with `Iterator.next()`);
  *    it should also not throw any exceptions, unless `moveNext()` never
  *    happened so there is no `current` element to return
  *
  *  - in order to advance the cursor to the next element, one has to call
  *    the [[monix.interact.Cursor#moveNext moveNext()]] method; this
  *    method triggers any possible side-effects, returns `true` for as
  *    long as there are elements to return and can also throw
  *    exceptions, in which case the iteration must stop
  *
  * Provided because it is needed by the [[Iterant]] type, but exposed
  * as a public type, so can be used by users.
  *
  * @define strictOrLazyNote NOTE: application of this function can be
  *         either strict or lazy (depending on the underlying cursor type),
  *         but it does not modify the original collection.
  */
trait Cursor[+A] extends Serializable {
  /** Gets the current element of the underlying collection.
    *
    * After an enumerator is created, the [[moveNext]] method must be
    * called to advance the cursor to the first element of the collection,
    * before reading the value of the [[current]] property; otherwise the
    * behavior of `current` is undefined and can throw an exception.
    *
    * This method does not move the position of the cursor, and consecutive
    * calls to [[current]] return the same object until [[moveNext]] is called.
    */
  def current: A

  /** Advances the enumerator to the next element of the collection.
    *
    * A cursor remains valid as long as the collection remains unchanged.
    * If changes are made to the collection, such as adding, modifying,
    * or deleting elements, the cursor might be invalidated and the next
    * call to [[moveNext]] might throw an exception
    * (such as `ConcurrentModificationException`), but not necessarily.
    *
    * @return `true` in case the advancement succeeded and there is a
    *        [[current]] element that can be fetched, or `false` in
    *        case there are no further elements and the iteration must
    *        stop
    */
  def moveNext(): Boolean

  /** Creates a new cursor that maps all produced values of this cursor
    * to new values using a transformation function.
    *
    * $strictOrLazyNote
    *
    * @param f is the transformation function
    * @return a new cursor which transforms every value produced by this
    *         cursor by applying the function `f` to it.
    */
  def map[B](f: A => B): Cursor[B]

  /** Returns an cursor over all the elements of the source cursor
    * that satisfy the predicate `p`. The order of the elements
    * is preserved.
    *
    * $strictOrLazyNote
    *
    * @param p the predicate used to test values.
    * @return a cursor which produces those values of this cursor
    *         which satisfy the predicate `p`.
    */
  def filter(p: A => Boolean): Cursor[A]

  /** Creates a cursor by transforming values produced by the source
    * cursor with a partial function, dropping those values for which
    * the partial function is not defined.
    *
    * $strictOrLazyNote
    *
    * @param pf the partial function which filters and maps the iterator.
    * @return a new iterator which yields each value `x` produced by this
    *         cursor for which `pf` is defined the image `pf(x)`.
    */
  def collect[B](pf: PartialFunction[A,B]): Cursor[B]

  /** Applies a binary operator to a start value and all elements
    * of this cursor, going left to right.
    *
    * NOTE: applying this function on the cursor will consume it
    * completely.
    *
    * @param initial is the start value.
    * @param op the binary operator to apply
    * @tparam R is the result type of the binary operator.
    *
    * @return the result of inserting `op` between consecutive elements
    *         of this cursor, going left to right with the start value
    *         `initial` on the left. Returns `initial` if the cursor
    *         is empty.
    */
  def foldLeft[R](initial: R)(op: (R,A) => R): R = {
    var result = initial
    while (moveNext()) result = op(result, current)
    result
  }

  /** Converts this cursor into a Scala `Iterator`. */
  def toIterator: Iterator[A]

  /** Converts this cursor into a Java `Iterator`. */
  def toJavaIterator[B >: A]: java.util.Iterator[B]
}

object Cursor {
  /** Converts a Scala `Iterator` into a `Cursor`. */
  def fromIterator[A](iter: Iterator[A]): Cursor[A] =
    new IteratorCursor[A](iter)

  /** Converts a standard `java.util.Iterator` into a `Cursor`. */
  def fromJavaIterator[A](iter: java.util.Iterator[A]): Cursor[A] = {
    import scala.collection.JavaConverters._
    new IteratorCursor(iter.asScala)
  }

  /** Builds a [[Cursor]] from a standard `Array`, with strict
    * semantics on transformations.
    *
    * @param array is the underlying reference to use for traversing
    *        and transformations
    */
  def fromArray[A](array: Array[A]): Cursor[A] =
    new ArrayCursor[A](array)

  /** Builds a [[Cursor]] from a standard `Array`, with strict
    * semantics on transformations.
    *
    * @param array is the underlying reference to use for traversing
    *        and transformations
    *
    * @param offset is the offset to start from, which would have
    *        been zero by default
    *
    * @param length is the length of created cursor, which would
    *        have been `array.length` by default
    */
  def fromArray[A](array: Array[A], offset: Int, length: Int): Cursor[A] =
    new ArrayCursor[A](array, offset, length)
}