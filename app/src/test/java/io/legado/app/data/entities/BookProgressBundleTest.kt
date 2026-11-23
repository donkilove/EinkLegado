package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BookProgressBundle] 的合并语义测试。
 *
 * 同步的冲突规则是「取最远位置、进度只前进不后退」。这条规则一旦被写错，
 * 用户在两台设备间切换时进度会倒退，且没有任何报错——只能靠测试守住。
 *
 * 说明：本测试刻意只覆盖不依赖 Android 的部分（[BookProgressBundle.merge] /
 * [BookProgressBundle.get] / [BookProgressBundle.put] / [BookProgressBundle.progressKey]
 * 与 [isAheadOf]）。`applyTo(book: Book)` 与 `isAheadOf(BookProgress, Book)` 依赖
 * `Book`（Room 注解 + Parcelable + appDb），JVM 单测无法加载，不在此覆盖。
 */
class BookProgressBundleTest {

    private fun progress(
        name: String = "绍宋",
        author: String = "榴弹怕水",
        chapterIndex: Int = 0,
        chapterPos: Int = 0,
        time: Long = 0L,
        title: String? = "第一章"
    ) = BookProgress(
        name = name,
        author = author,
        durChapterIndex = chapterIndex,
        durChapterPos = chapterPos,
        durChapterTime = time,
        durChapterTitle = title
    )

    // ---------------------------------------------------------------- 键

    @Test
    fun `progressKey 由书名与作者拼成`() {
        assertEquals("绍宋_榴弹怕水", BookProgressBundle.progressKey("绍宋", "榴弹怕水"))
    }

    @Test
    fun `同名不同作者是不同的书`() {
        val bundle = BookProgressBundle()
        bundle.put(progress(author = "作者甲", chapterIndex = 1))
        bundle.put(progress(author = "作者乙", chapterIndex = 2))

        assertEquals(2, bundle.books.size)
        assertEquals(1, bundle.get("绍宋", "作者甲")?.durChapterIndex)
        assertEquals(2, bundle.get("绍宋", "作者乙")?.durChapterIndex)
    }

    // ---------------------------------------------------------------- 读写

    @Test
    fun `get 对不存在的书返回 null`() {
        val bundle = BookProgressBundle()

        assertNull(bundle.get("不存在的书", "无名氏"))
    }

    @Test
    fun `put 同书重复写入时后者覆盖前者`() {
        val bundle = BookProgressBundle()
        bundle.put(progress(chapterIndex = 1))
        bundle.put(progress(chapterIndex = 2))

        assertEquals(1, bundle.books.size)
        assertEquals(2, bundle.get("绍宋", "榴弹怕水")?.durChapterIndex)
    }

    // ---------------------------------------------------------------- 合并：章节优先

    @Test
    fun `空快照合并任何远端进度都会接受`() {
        val bundle = BookProgressBundle()

        assertTrue(bundle.merge(progress(chapterIndex = 5)))
        assertEquals(5, bundle.get("绍宋", "榴弹怕水")?.durChapterIndex)
    }

    @Test
    fun `远端章节更靠后时接受`() {
        val bundle = BookProgressBundle()
        bundle.put(progress(chapterIndex = 5))

        assertTrue(bundle.merge(progress(chapterIndex = 6)))
        assertEquals(6, bundle.get("绍宋", "榴弹怕水")?.durChapterIndex)
    }

    @Test
    fun `远端章节更靠前时拒绝`() {
        val bundle = BookProgressBundle()
        bundle.put(progress(chapterIndex = 5))

        assertFalse("进度不应倒退", bundle.merge(progress(chapterIndex = 4)))
        assertEquals(5, bundle.get("绍宋", "榴弹怕水")?.durChapterIndex)
    }

    @Test
    fun `章节相同时接受章节内位置更靠后的`() {
        val bundle = BookProgressBundle()
        bundle.put(progress(chapterIndex = 5, chapterPos = 100))

        assertTrue(bundle.merge(progress(chapterIndex = 5, chapterPos = 200)))
        assertEquals(200, bundle.get("绍宋", "榴弹怕水")?.durChapterPos)
    }

    @Test
    fun `章节相同时拒绝章节内位置更靠前的`() {
        val bundle = BookProgressBundle()
        bundle.put(progress(chapterIndex = 5, chapterPos = 200))

        assertFalse(bundle.merge(progress(chapterIndex = 5, chapterPos = 100)))
        assertEquals(200, bundle.get("绍宋", "榴弹怕水")?.durChapterPos)
    }

    @Test
    fun `章节与位置都相同时拒绝`() {
        val bundle = BookProgressBundle()
        bundle.put(progress(chapterIndex = 5, chapterPos = 200))

        assertFalse("完全相同的进度不算前进", bundle.merge(progress(chapterIndex = 5, chapterPos = 200)))
    }

    @Test
    fun `章节更靠后时即使位置更小也接受`() {
        val bundle = BookProgressBundle()
        bundle.put(progress(chapterIndex = 5, chapterPos = 9999))

        assertTrue(
            "章节索引优先于章节内位置",
            bundle.merge(progress(chapterIndex = 6, chapterPos = 0))
        )
        assertEquals(6, bundle.get("绍宋", "榴弹怕水")?.durChapterIndex)
        assertEquals(0, bundle.get("绍宋", "榴弹怕水")?.durChapterPos)
    }

    @Test
    fun `合并只影响目标书，不碰其它书`() {
        val bundle = BookProgressBundle()
        bundle.put(progress(name = "书甲", chapterIndex = 10))
        bundle.put(progress(name = "书乙", chapterIndex = 20))

        bundle.merge(progress(name = "书甲", chapterIndex = 11))

        assertEquals(11, bundle.get("书甲", "榴弹怕水")?.durChapterIndex)
        assertEquals("其它书的进度不应被改动", 20, bundle.get("书乙", "榴弹怕水")?.durChapterIndex)
    }

    // ---------------------------------------------------------------- isAheadOf

    @Test
    fun `isAheadOf 章节更大者为靠后`() {
        assertTrue(progress(chapterIndex = 6).isAheadOf(progress(chapterIndex = 5)))
        assertFalse(progress(chapterIndex = 5).isAheadOf(progress(chapterIndex = 6)))
    }

    @Test
    fun `isAheadOf 同章节时比较章节内位置`() {
        assertTrue(progress(chapterIndex = 5, chapterPos = 200).isAheadOf(progress(chapterIndex = 5, chapterPos = 100)))
        assertFalse(progress(chapterIndex = 5, chapterPos = 100).isAheadOf(progress(chapterIndex = 5, chapterPos = 200)))
    }

    @Test
    fun `isAheadOf 完全相同不算靠后`() {
        assertFalse(progress(chapterIndex = 5, chapterPos = 100).isAheadOf(progress(chapterIndex = 5, chapterPos = 100)))
    }

    // ---------------------------------------------------------------- 版本

    @Test
    fun `默认版本号为 2`() {
        assertEquals(2, BookProgressBundle().version)
        assertEquals(2, BookProgressBundle.CURRENT_VERSION)
    }
}
