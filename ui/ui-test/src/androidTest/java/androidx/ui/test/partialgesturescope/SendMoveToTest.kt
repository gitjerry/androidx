/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.test.partialgesturescope

import android.os.SystemClock.sleep
import androidx.test.filters.MediumTest
import androidx.ui.test.android.AndroidInputDispatcher
import androidx.ui.test.createComposeRule
import androidx.ui.test.movePointerTo
import androidx.ui.test.partialgesturescope.Common.partialGesture
import androidx.ui.test.runOnIdleCompose
import androidx.ui.test.sendCancel
import androidx.ui.test.sendDown
import androidx.ui.test.sendMove
import androidx.ui.test.sendMoveTo
import androidx.ui.test.sendUp
import androidx.ui.test.util.ClickableTestBox
import androidx.ui.test.util.MultiPointerInputRecorder
import androidx.ui.test.util.assertTimestampsAreIncreasing
import androidx.ui.test.util.expectError
import androidx.ui.test.util.verify
import androidx.ui.geometry.Offset
import androidx.ui.unit.milliseconds
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * Tests if [sendMoveTo] and [movePointerTo] work
 */
@MediumTest
class SendMoveToTest() {
    companion object {
        private val downPosition1 = Offset(10f, 10f)
        private val downPosition2 = Offset(20f, 20f)
        private val moveToPosition1 = Offset(11f, 11f)
        private val moveToPosition2 = Offset(21f, 21f)
    }

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val inputDispatcherRule: TestRule =
        AndroidInputDispatcher.TestRule(disableDispatchInRealTime = true)

    private val recorder = MultiPointerInputRecorder()

    @Before
    fun setUp() {
        // Given some content
        composeTestRule.setContent {
            ClickableTestBox(recorder)
        }
    }

    @Test
    fun onePointer() {
        // When we inject a down event followed by a move event
        partialGesture { sendDown(downPosition1) }
        sleep(20) // (with some time in between)
        partialGesture { sendMoveTo(moveToPosition1) }

        runOnIdleCompose {
            recorder.run {
                // Then we have recorded 1 down event and 1 move event
                assertTimestampsAreIncreasing()
                assertThat(events).hasSize(2)

                var t = events[0].getPointer(0).timestamp
                val pointerId = events[0].getPointer(0).id

                t += 10.milliseconds
                assertThat(events[1].pointerCount).isEqualTo(1)
                events[1].getPointer(0).verify(t, pointerId, true, moveToPosition1)
            }
        }
    }

    @Test
    fun twoPointers_separateMoveEvents() {
        // When we inject two down events followed by two move events
        partialGesture { sendDown(1, downPosition1) }
        partialGesture { sendDown(2, downPosition2) }
        partialGesture { sendMoveTo(1, moveToPosition1) }
        partialGesture { sendMoveTo(2, moveToPosition2) }

        runOnIdleCompose {
            recorder.run {
                // Then we have recorded two down events and two move events
                assertTimestampsAreIncreasing()
                assertThat(events).hasSize(4)

                var t = events[0].getPointer(0).timestamp
                val pointerId1 = events[0].getPointer(0).id
                val pointerId2 = events[1].getPointer(1).id

                t += 10.milliseconds
                assertThat(events[2].pointerCount).isEqualTo(2)
                events[2].getPointer(0).verify(t, pointerId1, true, moveToPosition1)
                events[2].getPointer(1).verify(t, pointerId2, true, downPosition2)

                t += 10.milliseconds
                assertThat(events[3].pointerCount).isEqualTo(2)
                events[3].getPointer(0).verify(t, pointerId1, true, moveToPosition1)
                events[3].getPointer(1).verify(t, pointerId2, true, moveToPosition2)
            }
        }
    }

    @Test
    fun twoPointers_oneMoveEvent() {
        // When we inject two down events followed by one move events
        partialGesture { sendDown(1, downPosition1) }
        partialGesture { sendDown(2, downPosition2) }
        sleep(20) // (with some time in between)
        partialGesture { movePointerTo(1, moveToPosition1) }
        partialGesture { movePointerTo(2, moveToPosition2) }
        partialGesture { sendMove() }

        runOnIdleCompose {
            recorder.run {
                // Then we have recorded two down events and one move events
                assertTimestampsAreIncreasing()
                assertThat(events).hasSize(3)

                var t = events[0].getPointer(0).timestamp
                val pointerId1 = events[0].getPointer(0).id
                val pointerId2 = events[1].getPointer(1).id

                t += 10.milliseconds
                assertThat(events[2].pointerCount).isEqualTo(2)
                events[2].getPointer(0).verify(t, pointerId1, true, moveToPosition1)
                events[2].getPointer(1).verify(t, pointerId2, true, moveToPosition2)
            }
        }
    }

    @Test
    fun moveToWithoutDown() {
        expectError<IllegalStateException> {
            partialGesture { sendMoveTo(moveToPosition1) }
        }
    }

    @Test
    fun moveToWrongPointerId() {
        partialGesture { sendDown(1, downPosition1) }
        expectError<IllegalArgumentException> {
            partialGesture { sendMoveTo(2, moveToPosition1) }
        }
    }

    @Test
    fun moveToAfterUp() {
        partialGesture { sendDown(downPosition1) }
        partialGesture { sendUp() }
        expectError<IllegalStateException> {
            partialGesture { sendMoveTo(moveToPosition1) }
        }
    }

    @Test
    fun moveToAfterCancel() {
        partialGesture { sendDown(downPosition1) }
        partialGesture { sendCancel() }
        expectError<IllegalStateException> {
            partialGesture { sendMoveTo(moveToPosition1) }
        }
    }

    @Test
    fun movePointerToWithoutDown() {
        expectError<IllegalStateException> {
            partialGesture { movePointerTo(1, moveToPosition1) }
        }
    }

    @Test
    fun movePointerToWrongPointerId() {
        partialGesture { sendDown(1, downPosition1) }
        expectError<IllegalArgumentException> {
            partialGesture { movePointerTo(2, moveToPosition1) }
        }
    }

    @Test
    fun movePointerToAfterUp() {
        partialGesture { sendDown(1, downPosition1) }
        partialGesture { sendUp(1) }
        expectError<IllegalStateException> {
            partialGesture { movePointerTo(1, moveToPosition1) }
        }
    }

    @Test
    fun movePointerToAfterCancel() {
        partialGesture { sendDown(1, downPosition1) }
        partialGesture { sendCancel() }
        expectError<IllegalStateException> {
            partialGesture { movePointerTo(1, moveToPosition1) }
        }
    }
}
