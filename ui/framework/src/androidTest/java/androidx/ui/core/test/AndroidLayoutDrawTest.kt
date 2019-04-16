/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.ui.core.test

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.Handler
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.rule.ActivityTestRule
import androidx.ui.core.AndroidCraneView
import androidx.ui.core.Constraints
import androidx.ui.core.CraneWrapper
import androidx.ui.core.Draw
import androidx.ui.core.IntPx
import androidx.ui.core.Layout
import androidx.ui.core.MultiChildLayout
import androidx.ui.core.OnChildPositioned
import androidx.ui.core.ParentData
import androidx.ui.core.Ref
import androidx.ui.core.WithConstraints
import androidx.ui.core.coerceAtLeast
import androidx.ui.core.ipx
import androidx.ui.core.max
import androidx.ui.core.toRect
import androidx.ui.framework.test.TestActivity
import androidx.ui.painting.Color
import androidx.ui.painting.Paint
import com.google.r4a.Children
import com.google.r4a.Composable
import com.google.r4a.Model
import com.google.r4a.composeInto
import com.google.r4a.composer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Corresponds to ContainingViewTest, but tests single composition measure, layout and draw.
 * It also tests that layouts with both Layout and MeasureBox work.
 * TODO(popam): remove this comment and ContainingViewTest when ComplexMeasureBox is removed
 */
@SmallTest
@RunWith(JUnit4::class)
class AndroidLayoutDrawTest {
    @get:Rule
    val activityTestRule = ActivityTestRule<TestActivity>(
        TestActivity::class.java
    )
    private lateinit var activity: TestActivity
    private lateinit var instrumentation: Instrumentation
    private lateinit var handler: Handler
    private lateinit var drawLatch: CountDownLatch

    @Before
    fun setup() {
        activity = activityTestRule.activity
        activity.hasFocusLatch.await(5, TimeUnit.SECONDS)
        instrumentation = InstrumentationRegistry.getInstrumentation()
        runOnUiThread { handler = Handler() }
        drawLatch = CountDownLatch(1)
    }

    // Tests that simple drawing works with layered squares
    @Test
    fun simpleDrawTest() {
        val yellow = Color(0xFFFFFF00.toInt())
        val red = Color(0xFF800000.toInt())
        val model = SquareModel(outerColor = yellow, innerColor = red, size = 10.ipx)
        composeSquares(model)

        validateSquareColors(outerColor = yellow, innerColor = red, size = 10)
    }

    // Tests that recomposition works with models used within Draw components
    @Test
    fun recomposeDrawTest() {
        val white = Color(0xFFFFFFFF.toInt())
        val blue = Color(0xFF000080.toInt())
        val model = SquareModel(outerColor = blue, innerColor = white)
        composeSquares(model)
        validateSquareColors(outerColor = blue, innerColor = white, size = 10)

        drawLatch = CountDownLatch(1)
        val red = Color(0xFF800000.toInt())
        val yellow = Color(0xFFFFFF00.toInt())
        runOnUiThread {
            model.outerColor = red
            model.innerColor = yellow
        }

        validateSquareColors(outerColor = red, innerColor = yellow, size = 10)
    }

    // Tests that recomposition works with models used within Layout components
    @Test
    fun recomposeSizeTest() {
        val white = Color(0xFFFFFFFF.toInt())
        val blue = Color(0xFF000080.toInt())
        val model = SquareModel(outerColor = blue, innerColor = white)
        composeSquares(model)
        validateSquareColors(outerColor = blue, innerColor = white, size = 10)

        drawLatch = CountDownLatch(1)
        runOnUiThread { model.size = 20.ipx }
        validateSquareColors(outerColor = blue, innerColor = white, size = 20)
    }

    // The size and color are both changed in a simpler single-color square.
    @Test
    fun simpleSquareColorAndSizeTest() {
        val green = Color(0xFF00FF00.toInt())
        val model = SquareModel(size = 20.ipx, outerColor = green, innerColor = green)

        runOnUiThread {
            activity.composeInto {
                <CraneWrapper>
                    <Padding size=(model.size * 3)>
                        <Draw> canvas, parentSize ->
                            val paint = Paint().apply {
                                color = model.outerColor
                            }
                            canvas.drawRect(parentSize.toRect(), paint)
                            drawLatch.countDown()
                        </Draw>
                    </Padding>
                </CraneWrapper>
            }
        }
        validateSquareColors(outerColor = green, innerColor = green, size = 20)

        drawLatch = CountDownLatch(1)
        runOnUiThread {
            model.size = 30.ipx
        }
        validateSquareColors(outerColor = green, innerColor = green, size = 30)

        drawLatch = CountDownLatch(1)
        val blue = Color(0xFF0000FF.toInt())

        runOnUiThread {
            model.innerColor = blue
            model.outerColor = blue
        }
        validateSquareColors(outerColor = blue, innerColor = blue, size = 30)
    }

    @Test
    fun withConstraintsTest() {
        val size = 20.ipx

        val countDownLatch = CountDownLatch(2)
        val topConstraints = Ref<Constraints>()
        val paddedConstraints = Ref<Constraints>()
        val firstChildConstraints = Ref<Constraints>()
        val secondChildConstraints = Ref<Constraints>()
        runOnUiThread {
            activity.composeInto {
                <CraneWrapper>
                    <WithConstraints> constraints ->
                        topConstraints.value = constraints
                        <Padding size>
                            <WithConstraints> constraints ->
                                paddedConstraints.value = constraints
                                <OnChildPositioned onPositioned={
                                    countDownLatch.countDown()
                                }>
                                    <Layout layoutBlock={ _, constraints ->
                                        firstChildConstraints.value = constraints
                                        layout(size, size) {}
                                    } children={} />
                                </OnChildPositioned>
                                <OnChildPositioned onPositioned={
                                    countDownLatch.countDown()
                                }>
                                    <Layout layoutBlock={ _, constraints ->
                                        secondChildConstraints.value = constraints
                                        layout(size, size) {}
                                    } children={} />
                                </OnChildPositioned>
                            </WithConstraints>
                        </Padding>
                    </WithConstraints>
                </CraneWrapper>
            }
        }
        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        val expectedPaddedConstraints = Constraints(
            topConstraints.value!!.minWidth - size * 2,
            topConstraints.value!!.maxWidth - size * 2,
            topConstraints.value!!.minHeight - size * 2,
            topConstraints.value!!.maxHeight - size * 2
        )
        assertEquals(expectedPaddedConstraints, paddedConstraints.value)
        assertEquals(paddedConstraints.value, firstChildConstraints.value)
        assertEquals(paddedConstraints.value, secondChildConstraints.value)
    }

    @Test
    fun multiChildLayoutTest() {
        val childrenCount = 3
        val childConstraints = arrayOf(
            Constraints(),
            Constraints.tightConstraintsForWidth(50.ipx),
            Constraints.tightConstraintsForHeight(50.ipx)
        )
        val headerChildrenCount = 1
        val footerChildrenCount = 2

        runOnUiThread {
            activity.composeInto {
                <CraneWrapper>
                    val header = @Composable {
                        <Layout layoutBlock={ measurables, constraints ->
                            assertEquals(childConstraints[0], constraints)
                        } children={} />
                    }
                    val footer = @Composable {
                        <Layout layoutBlock={ measurables, constraints ->
                            assertEquals(childConstraints[1], constraints)
                        } children={} />
                        <Layout layoutBlock={ measurables, constraints ->
                            assertEquals(childConstraints[2], constraints)
                        } children={} />
                    }

                    <MultiChildLayout
                        childrenArray=arrayOf(header, footer)> measurables, constraints ->
                        assertEquals(childrenCount, measurables.size)
                        measurables.forEachIndexed { index, measurable ->
                            measurable.measure(childConstraints[index])
                        }
                        assertEquals(headerChildrenCount, measurables[header].size)
                        measurables[header][0].measure(childConstraints[0])
                        assertEquals(footerChildrenCount, measurables[footer].size)
                        measurables[footer][0].measure(childConstraints[1])
                        measurables[footer][1].measure(childConstraints[2])
                    </MultiChildLayout>
                </CraneWrapper>
            }
        }
    }

    @Test
    fun multiChildLayoutTest_doesNotOverrideChildrenParentData() {
        runOnUiThread {
            activity.composeInto {
                <CraneWrapper>
                    val header = @Composable {
                        <ParentData data=0>
                            <Layout layoutBlock={ measurables, constraints -> } children={} />
                        </ParentData>
                    }
                    val footer = @Composable {
                        <ParentData data=1>
                            <Layout layoutBlock={ measurables, constraints -> } children={} />
                        </ParentData>
                    }

                    <MultiChildLayout
                        childrenArray=arrayOf(header, footer)> measurables, constraints ->
                        assertEquals(0, measurables[0].parentData)
                        assertEquals(1, measurables[1].parentData)
                    </MultiChildLayout>
                </CraneWrapper>
            }
        }
    }

    // We only need this because IR compiler doesn't like converting lambdas to Runnables
    private fun runOnUiThread(block: () -> Unit) {
        val runnable: Runnable = object : Runnable {
            override fun run() {
                block()
            }
        }
        activityTestRule.runOnUiThread(runnable)
    }

    private fun composeSquares(model: SquareModel) {
        runOnUiThread {
            activity.composeInto {
                <CraneWrapper>
                    <Draw> canvas, parentSize ->
                        val paint = Paint()
                        paint.color = model.outerColor
                        canvas.drawRect(parentSize.toRect(), paint)
                    </Draw>
                    <Padding size=model.size>
                        <AtLeastSize size=model.size>
                            <Draw> canvas, parentSize ->
                                drawLatch.countDown()
                                val paint = Paint()
                                paint.color = model.innerColor
                                canvas.drawRect(parentSize.toRect(), paint)
                            </Draw>
                        </AtLeastSize>
                    </Padding>
                </CraneWrapper>
            }
        }
    }

    private fun validateSquareColors(
        outerColor: Color,
        innerColor: Color,
        size: Int
    ) {
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        val bitmap = waitAndScreenShot()
        val totalSize = 3 * size
        assertEquals(totalSize, bitmap.width)
        assertEquals(totalSize, bitmap.height)
        val squareStart = size
        val squareEnd = size * 2
        for (x in 0 until totalSize) {
            for (y in 0 until totalSize) {
                val pixel = bitmap.getPixel(x, y)
                val pixelString = Color(pixel).toString()
                if (x < squareStart || x >= squareEnd || y < squareStart || y >= squareEnd) {
                    assertEquals("Pixel within drawn rect[$x, $y] is $outerColor, " +
                            "but was ($pixelString)", outerColor.value, pixel)
                } else {
                    assertEquals("Pixel within drawn rect[$x, $y] is $innerColor, " +
                            "but was ($pixelString)", innerColor.value, pixel)
                }
            }
        }
    }

    private fun findAndroidCraneView(): AndroidCraneView {
        val contentViewGroup = activity.findViewById<ViewGroup>(android.R.id.content)
        return findAndroidCraneView(contentViewGroup)!!
    }

    private fun findAndroidCraneView(parent: ViewGroup): AndroidCraneView? {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child is AndroidCraneView) {
                return child
            } else if (child is ViewGroup) {
                val craneView = findAndroidCraneView(child)
                if (craneView != null) {
                    return craneView
                }
            }
        }
        return null
    }

    private fun waitAndScreenShot(): Bitmap {
        val view = findAndroidCraneView()
        val flushListener = DrawCounterListener(view)
        val offset = intArrayOf(0, 0)
        runOnUiThread {
            view.getLocationInWindow(offset)
            view.viewTreeObserver.addOnPreDrawListener(flushListener)
            view.invalidate()
        }

        assertTrue(flushListener.latch.await(1, TimeUnit.SECONDS))
        val width = view.width
        val height = view.height

        val dest =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val srcRect = android.graphics.Rect(0, 0, width, width)
        srcRect.offset(offset[0], offset[1])
        val latch = CountDownLatch(1)
        var copyResult = 0
        val onCopyFinished = object : PixelCopy.OnPixelCopyFinishedListener {
            override fun onPixelCopyFinished(result: Int) {
                copyResult = result
                latch.countDown()
            }
        }
        PixelCopy.request(activity.window, srcRect, dest, onCopyFinished, handler)
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, copyResult)
        return dest
    }
}

@Composable
fun AtLeastSize(size: IntPx, @Children children: @Composable() () -> Unit) {
    <Layout layoutBlock = { measurables, constraints ->
        val newConstraints = Constraints(
            minWidth = max(size, constraints.minWidth),
            maxWidth = max(size, constraints.maxWidth),
            minHeight = max(size, constraints.minHeight),
            maxHeight = max(size, constraints.maxHeight)
        )
        val placeables = measurables.map { m ->
            m.measure(newConstraints)
        }
        var maxWidth = size
        var maxHeight = size
        placeables.forEach { child ->
            maxHeight = max(child.height, maxHeight)
            maxWidth = max(child.width, maxWidth)
        }
        layout(maxWidth, maxHeight) {
            placeables.forEach { child ->
                child.place(0.ipx, 0.ipx)
            }
        }
    } children />
}

@Composable
fun Padding(size: IntPx, @Children children: @Composable() () -> Unit) {
    <Layout layoutBlock = { measurables, constraints ->
        val totalDiff = size * 2
        val newConstraints = Constraints(
            minWidth = (constraints.minWidth - totalDiff).coerceAtLeast(0.ipx),
            maxWidth = (constraints.maxWidth - totalDiff).coerceAtLeast(0.ipx),
            minHeight = (constraints.minHeight - totalDiff).coerceAtLeast(0.ipx),
            maxHeight = (constraints.maxHeight - totalDiff).coerceAtLeast(0.ipx)
        )
        val placeables = measurables.map { m ->
            m.measure(newConstraints)
        }
        var maxWidth = size
        var maxHeight = size
        placeables.forEach { child ->
            maxHeight = max(child.height + totalDiff, maxHeight)
            maxWidth = max(child.width + totalDiff, maxWidth)
        }
        layout(maxWidth, maxHeight) {
            placeables.forEach { child ->
                child.place(size, size)
            }
        }
    } children />
}

class DrawCounterListener(private val view: View) :
    ViewTreeObserver.OnPreDrawListener {
    val latch = CountDownLatch(5)

    override fun onPreDraw(): Boolean {
        latch.countDown()
        if (latch.count > 0) {
            view.postInvalidate()
        } else {
            view.getViewTreeObserver().removeOnPreDrawListener(this)
        }
        return true
    }
}

@Model
class SquareModel(
    var size: IntPx = 10.ipx,
    var outerColor: Color = Color(0xFF000080.toInt()),
    var innerColor: Color = Color(0xFFFFFFFF.toInt())
)