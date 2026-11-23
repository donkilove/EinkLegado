package io.legado.app.help.sync

import io.legado.app.data.entities.BookSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SyncDecision] 的决策逻辑测试。
 *
 * 这条链路的失败模式是静默的：判错分支会用云端覆盖本地（或反过来），
 * 用户数据消失且没有任何报错。因此每个分支都必须被钉死。
 *
 * 判定顺序是敏感的，测试也按顺序组织：
 *   1. remote == null        → UploadLocal
 *   2. localHash == lastHash → ReplaceLocal
 *   3. remoteHash == lastHash → UploadLocalLocalWins
 *   4. 其余                   → SkipToProtectSources 或 UploadLocalBothChanged
 */
class SyncDecisionTest {

    private val empty = SyncData()
    private val withSource = SyncData(
        sources = listOf(BookSource(bookSourceUrl = "https://a.com", bookSourceName = "a"))
    )

    private fun decide(
        localHash: String = "h-local",
        remoteHash: String = "h-remote",
        lastHash: String? = "h-last",
        local: SyncData = empty,
        remote: SyncData? = empty
    ) = SyncDecision.decide(localHash, remoteHash, lastHash, local, remote)

    // ---------------------------------------------------------------- 分支 1：云端无数据

    @Test
    fun `云端为 null 时上传本地`() {
        assertEquals(SyncDecision.UploadLocal, decide(remote = null))
    }

    @Test
    fun `云端为 null 时即使本地也空仍上传本地`() {
        assertEquals(SyncDecision.UploadLocal, decide(local = empty, remote = null))
    }

    @Test
    fun `云端为 null 时不考虑指纹是否相同`() {
        // 三个指纹全相同也不影响：云端没有数据这一条优先
        assertEquals(
            SyncDecision.UploadLocal,
            decide(localHash = "same", remoteHash = "same", lastHash = "same", remote = null)
        )
    }

    // ---------------------------------------------------------------- 分支 2：本地未改，云端为准

    @Test
    fun `本地指纹等于上次同步指纹时以云端为准`() {
        assertEquals(
            SyncDecision.ReplaceLocal,
            decide(localHash = "h1", remoteHash = "h2", lastHash = "h1")
        )
    }

    @Test
    fun `本地未改时即使云端也未改仍以云端为准`() {
        assertEquals(
            SyncDecision.ReplaceLocal,
            decide(localHash = "same", remoteHash = "same", lastHash = "same")
        )
    }

    @Test
    fun `本地未改时即使本地书源为空也不走保护分支`() {
        // 书源空保护只作用于「双改」分支，不应误伤本分支。
        // 此处的语义是：本地没动过，就该老实地接受云端（含清空）。
        assertEquals(
            SyncDecision.ReplaceLocal,
            decide(localHash = "h1", remoteHash = "h2", lastHash = "h1", local = empty, remote = withSource)
        )
    }

    // ---------------------------------------------------------------- 分支 3：云端未改，上传本地

    @Test
    fun `云端指纹等于上次同步指纹时上传本地`() {
        assertEquals(
            SyncDecision.UploadLocalLocalWins,
            decide(localHash = "h1", remoteHash = "h2", lastHash = "h2")
        )
    }

    @Test
    fun `云端未改时即使本地书源为空也不走保护分支`() {
        assertEquals(
            SyncDecision.UploadLocalLocalWins,
            decide(localHash = "h1", remoteHash = "h2", lastHash = "h2", local = empty, remote = withSource)
        )
    }

    // ---------------------------------------------------------------- 分支 4：双改

    @Test
    fun `两边都改动且本地有书源时以本地为准上传`() {
        assertEquals(
            SyncDecision.UploadLocalBothChanged,
            decide(localHash = "h1", remoteHash = "h2", lastHash = "h0", local = withSource, remote = withSource)
        )
    }

    @Test
    fun `两边都改动且本地书源为空云端有书源时跳过上传`() {
        assertEquals(
            SyncDecision.SkipToProtectSources,
            decide(localHash = "h1", remoteHash = "h2", lastHash = "h0", local = empty, remote = withSource)
        )
    }

    @Test
    fun `两边都改动且两端书源都为空时仍上传本地`() {
        // 保护只针对「云端有而本地没有」，两端都空时没有可保护的东西
        assertEquals(
            SyncDecision.UploadLocalBothChanged,
            decide(localHash = "h1", remoteHash = "h2", lastHash = "h0", local = empty, remote = empty)
        )
    }

    @Test
    fun `两边都改动且本地有书源云端为空时上传本地`() {
        assertEquals(
            SyncDecision.UploadLocalBothChanged,
            decide(localHash = "h1", remoteHash = "h2", lastHash = "h0", local = withSource, remote = empty)
        )
    }

    // ---------------------------------------------------------------- 边界：首次同步

    @Test
    fun `首次同步 lastHash 为 null 时走双改分支`() {
        // 从没同步过，没有任何一端可以认定为「未改动」
        assertEquals(
            SyncDecision.UploadLocalBothChanged,
            decide(localHash = "h1", remoteHash = "h2", lastHash = null, local = withSource, remote = withSource)
        )
    }

    @Test
    fun `首次同步且本地书源为空云端有书源时跳过上传`() {
        assertEquals(
            SyncDecision.SkipToProtectSources,
            decide(localHash = "h1", remoteHash = "h2", lastHash = null, local = empty, remote = withSource)
        )
    }

    @Test
    fun `首次同步且两端内容相同会上传本地`() {
        // 已知的多余上传：lastHash 为 null 使「本地未改」判定不成立，
        // 即使两端数据一致也会走双改分支。不丢数据，但多一次上传。
        assertEquals(
            SyncDecision.UploadLocalBothChanged,
            decide(localHash = "same", remoteHash = "same", lastHash = null, local = empty, remote = empty)
        )
    }

    // ---------------------------------------------------------------- 边界：旧版指纹

    @Test
    fun `旧版指纹不会被判定为本地未改`() {
        // 旧版存的是 32 位数字，与新版 v2: 前缀不可能相等
        assertEquals(
            SyncDecision.UploadLocalBothChanged,
            decide(localHash = "v2:abc", remoteHash = "v2:def", lastHash = "123456789", local = withSource, remote = withSource)
        )
    }

    @Test
    fun `旧版指纹且本地书源为空时走保护分支`() {
        assertEquals(
            SyncDecision.SkipToProtectSources,
            decide(localHash = "v2:abc", remoteHash = "v2:def", lastHash = "123456789", local = empty, remote = withSource)
        )
    }
}
