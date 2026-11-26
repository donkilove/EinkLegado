package io.legado.app.ui.book.trash

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTrash
import io.legado.app.databinding.ActivityBookTrashBinding
import io.legado.app.help.sync.BookTrashHelper
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.EinkPager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书架回收站。
 *
 * 删除需要跨设备传播，但物理删除不可逆，所以删除先落到这里，保留 30 天。
 * 本页面提供三个出口：恢复、彻底删除单本、清空。
 *
 * 墨水屏：列表统一走 [EinkPager] 翻页，禁止滚动。
 */
class BookTrashActivity : VMBaseActivity<ActivityBookTrashBinding, BookTrashViewModel>(),
    BookTrashAdapter.CallBack {

    override val viewModel by viewModels<BookTrashViewModel>()
    override val binding by viewBinding(ActivityBookTrashBinding::inflate)

    private val adapter by lazy { BookTrashAdapter(this, this) }

    private val pager by lazy {
        EinkPager<BookTrash>(binding.recyclerView, binding.pageBar, getString(R.string.trash_count))
            .apply {
                onItemsChanged = { items ->
                    adapter.setItems(items, adapter.diffItemCallBack)
                    binding.tvEmpty.visibility =
                        if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
    }

    override fun onHorizontalSwipe(direction: Int): Boolean = pager.onSwipe(direction)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        observeData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_trash, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_clear_trash -> clearAll()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() = binding.run {
        recyclerView.setEdgeEffectColor(primaryColor)
        recyclerView.layoutManager = LinearLayoutManager(this@BookTrashActivity)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(VerticalDivider(this@BookTrashActivity))
    }

    private fun observeData() {
        lifecycleScope.launch {
            appDb.bookTrashDao.flowAll
                .flowOn(IO)
                .catch { it.printStackTrace() }
                .collect { pager.setAll(it) }
        }
    }

    override fun onRestore(item: BookTrash) {
        lifecycleScope.launch {
            withContext(IO) { BookTrashHelper.restore(item) }
            toastOnUi(R.string.trash_restore_success)
        }
    }

    override fun onPurge(item: BookTrash) {
        alert(R.string.delete) {
            setMessage(getString(R.string.trash_purge_confirm))
            yesButton {
                lifecycleScope.launch {
                    withContext(IO) { BookTrashHelper.purge(item) }
                }
            }
            noButton()
        }
    }

    private fun clearAll() {
        val count = adapter.itemCount
        if (count == 0) {
            toastOnUi(R.string.book_trash_empty)
            return
        }
        alert(R.string.clear_trash) {
            setMessage(getString(R.string.trash_clear_confirm, count))
            yesButton {
                lifecycleScope.launch {
                    // 必须逐条走 purge：直接 bookTrashDao.clear() 只清数据库，
                    // 本地文件与云端文件会全部残留
                    withContext(IO) { BookTrashHelper.purgeAll() }
                    toastOnUi(R.string.trash_cleared)
                }
            }
            noButton()
        }
    }
}
