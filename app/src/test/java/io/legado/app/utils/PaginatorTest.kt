package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Paginator] 的行为固化测试。
 *
 * 这个类是墨水屏分页的地基：页边界随每页实际条数变化，算错就会导致
 * 「页内仍需滚动」（内容溢出且滚动已禁用）或「按下标跳页跳到别的页」。
 * 之前这部分逻辑只能靠真机翻页肉眼核对，回归时没有安全网。
 */
class PaginatorTest {

    // ---------------------------------------------------------------- 空数据

    @Test
    fun `空列表时只有一页且切片为空`() {
        val p = Paginator<String>()
        p.setAll(emptyList())

        assertEquals(0, p.total)
        assertEquals(1, p.pageCount)
        assertEquals(0, p.page)
        assertTrue(p.slice().isEmpty())
    }

    @Test
    fun `空列表时移动到任何页都钳制到第 0 页`() {
        val p = Paginator<String>()
        p.setAll(emptyList())

        p.moveTo(5)

        assertEquals(0, p.page)
    }

    // ---------------------------------------------------------------- 均匀分页

    @Test
    fun `默认每页 8 条时 21 条分成 3 页`() {
        val p = Paginator<Int>()
        p.setAll((1..21).toList())

        assertEquals(3, p.pageCount)
        assertEquals((1..8).toList(), p.slice())

        p.moveBy(1)
        assertEquals((9..16).toList(), p.slice())

        p.moveBy(1)
        assertEquals((17..21).toList(), p.slice())
    }

    @Test
    fun `刚好整除时不多出空页`() {
        val p = Paginator<Int>()
        p.setAll((1..16).toList())

        assertEquals(2, p.pageCount)
    }

    @Test
    fun `单条数据只有一页`() {
        val p = Paginator<Int>()
        p.setAll(listOf(42))

        assertEquals(1, p.pageCount)
        assertEquals(listOf(42), p.slice())
    }

    // ---------------------------------------------------------------- 换数据

    @Test
    fun `setAll 会丢弃已测量的页边界并回到第一页`() {
        val p = Paginator<Int>()
        p.setAll((1..30).toList())
        p.commitPageSize(3)
        p.moveTo(2)
        assertEquals(2, p.page)

        p.setAll((1..30).toList())

        assertEquals(0, p.page)
        // 边界已重置：首屏回到默认的 8 条，而不是沿用刚才测出的 3 条
        assertEquals((1..8).toList(), p.slice())
    }

    // ---------------------------------------------------------------- 逐页测量

    @Test
    fun `回填当前页条数后页边界随之变化`() {
        val p = Paginator<Int>()
        p.setAll((1..30).toList())

        // 默认每页 8 条 → 4 页
        assertEquals(4, p.pageCount)

        // 首页实测只能放 5 条
        p.commitPageSize(5)

        assertEquals((1..5).toList(), p.slice())
        // 5 + 8*3 = 29 < 30，需要 5 页
        assertEquals(5, p.pageCount)
    }

    @Test
    fun `commitPageSize 返回值反映是否真的变化`() {
        val p = Paginator<Int>()
        p.setAll((1..30).toList())

        assertTrue("首次回填应视为变化", p.commitPageSize(5))
        assertTrue("重复回填相同值不应再视为变化", !p.commitPageSize(5))
        assertTrue("回填不同值应视为变化", p.commitPageSize(6))
    }

    @Test
    fun `commitPageSize 有下限 3 条`() {
        val p = Paginator<Int>()
        p.setAll((1..30).toList())

        p.commitPageSize(1)

        assertEquals("单页条数不应低于 3", 3, p.slice().size)
    }

    @Test
    fun `commitPageSize 作用于当前页而不是首页`() {
        val p = Paginator<Int>()
        p.setAll((1..30).toList())
        p.moveTo(1)

        p.commitPageSize(3)

        // 第 0 页仍是默认的 8 条，只有当前所在第 1 页变成 3 条
        p.moveTo(0)
        assertEquals((1..8).toList(), p.slice())

        p.moveTo(1)
        assertEquals((9..11).toList(), p.slice())
    }

    @Test
    fun `回填后页码始终落在有效范围内`() {
        val p = Paginator<Int>()
        p.setAll((1..20).toList())
        p.moveTo(1)

        // 把当前页撑到远大于剩余数据，页数会减少，页码不得越界
        p.commitPageSize(100)

        assertTrue("页码不应超过末页", p.page <= p.pageCount - 1)
        assertTrue("页码不应为负", p.page >= 0)
    }

    @Test
    fun `各页可以有不同的条数`() {
        val p = Paginator<Int>()
        p.setAll((1..20).toList())

        p.commitPageSize(3)      // 第 0 页 3 条
        p.moveTo(1)
        p.commitPageSize(10)     // 第 1 页 10 条

        p.moveTo(0)
        assertEquals((1..3).toList(), p.slice())

        p.moveTo(1)
        assertEquals((4..13).toList(), p.slice())

        p.moveTo(2)
        assertEquals((14..20).toList(), p.slice())
    }

    // ---------------------------------------------------------------- 统一条数

    @Test
    fun `setUniformPageSize 让所有页条数一致并回到第一页`() {
        val p = Paginator<Int>()
        p.setAll((1..20).toList())
        p.commitPageSize(3)
        p.moveTo(2)

        p.setUniformPageSize(5)

        assertEquals(0, p.page)
        assertEquals(4, p.pageCount)
        assertEquals((1..5).toList(), p.slice())
    }

    @Test
    fun `setUniformPageSize 同样有下限 3 条`() {
        val p = Paginator<Int>()
        p.setAll((1..20).toList())

        p.setUniformPageSize(0)

        assertEquals(3, p.slice().size)
    }

    // ---------------------------------------------------------------- 移动边界

    @Test
    fun `moveBy 越界时返回 null 且页码不变`() {
        val p = Paginator<Int>()
        p.setAll((1..20).toList())

        assertNull("首页再往前应越界", p.moveBy(-1))
        assertEquals(0, p.page)

        p.moveTo(p.pageCount - 1)
        assertNull("末页再往后应越界", p.moveBy(1))
        assertEquals(p.pageCount - 1, p.page)
    }

    @Test
    fun `moveTo 超范围时钳制而不是抛异常`() {
        val p = Paginator<Int>()
        p.setAll((1..20).toList())

        p.moveTo(999)
        assertEquals(p.pageCount - 1, p.page)

        p.moveTo(-999)
        assertEquals(0, p.page)
    }

    // ---------------------------------------------------------------- 下标定位

    @Test
    fun `pageOfItem 在均匀分页下按整除定位`() {
        val p = Paginator<Int>()
        p.setAll((0 until 24).toList())   // 每页 8 条，共 3 页

        assertEquals(0, p.pageOfItem(0))
        assertEquals(0, p.pageOfItem(7))
        assertEquals(1, p.pageOfItem(8))
        assertEquals(1, p.pageOfItem(15))
        assertEquals(2, p.pageOfItem(16))
        assertEquals(2, p.pageOfItem(23))
    }

    @Test
    fun `pageOfItem 在各页条数不同时按累计边界定位`() {
        val p = Paginator<Int>()
        p.setAll((0 until 20).toList())

        p.commitPageSize(3)      // 第 0 页覆盖下标 0..2
        p.moveTo(1)
        p.commitPageSize(10)     // 第 1 页覆盖下标 3..12

        assertEquals("下标 2 属于第 0 页", 0, p.pageOfItem(2))
        assertEquals("下标 3 属于第 1 页", 1, p.pageOfItem(3))
        assertEquals("下标 12 属于第 1 页", 1, p.pageOfItem(12))
        assertEquals("下标 13 属于第 2 页", 2, p.pageOfItem(13))
    }

    @Test
    fun `pageOfItem 对负下标返回首页`() {
        val p = Paginator<Int>()
        p.setAll((0 until 20).toList())

        assertEquals(0, p.pageOfItem(-5))
    }

    @Test
    fun `pageOfItem 超出总数时落在末页`() {
        val p = Paginator<Int>()
        p.setAll((0 until 20).toList())

        assertEquals(p.pageCount - 1, p.pageOfItem(9999))
    }

    @Test
    fun `pageStart 返回该页起始下标`() {
        val p = Paginator<Int>()
        p.setAll((0 until 20).toList())
        p.commitPageSize(3)
        p.moveTo(1)
        p.commitPageSize(10)

        assertEquals(0, p.pageStart(0))
        assertEquals(3, p.pageStart(1))
        assertEquals(13, p.pageStart(2))
    }

    // ---------------------------------------------------------------- 不可变边界

    @Test
    fun `slice 不修改底层数据`() {
        val p = Paginator<Int>()
        val source = (1..20).toList()
        p.setAll(source)

        p.slice()
        p.moveTo(1)
        p.slice()

        assertEquals("分页不应改动调用方传入的列表", (1..20).toList(), source)
        assertEquals(20, p.total)
    }
}
