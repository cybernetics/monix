/*
 * Copyright (c) 2014-2017 by The Monix Project Developers.
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

package monix.eval

import minitest.SimpleTestSuite
import monix.execution.schedulers.TestScheduler

object TaskAppSuite extends SimpleTestSuite {
  test("runl works") {
    val testS = TestScheduler()
    var wasExecuted = false

    val app = new TaskApp {
      override val scheduler = Coeval.now(testS)
      override def runl(args: List[String]) =
        Task { wasExecuted = args.headOption.getOrElse("false") == "true" }
    }

    app.main(Array("true")); testS.tick()
    assertEquals(wasExecuted, true)
  }

  test("runc works") {
    val testS = TestScheduler()
    var wasExecuted = false

    val app = new TaskApp {
      override val scheduler = Coeval.now(testS)
      override def runc = Task { wasExecuted = true }
    }

    app.main(Array.empty); testS.tick()
    assertEquals(wasExecuted, true)
  }
}
