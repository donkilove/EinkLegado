package io.legado.app.ui.main.bookshelf

import android.annotation.SuppressLint
import android.widget.SeekBar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.indices
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.DialogBookshelfConfigBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.getCheckedIndex
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.postEvent
import io.legado.app.utils.readText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.help.AppWebDav
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseBookshelfFragment(layoutId: Int) : VMBaseFragment<BookshelfViewModel>(layoutId),
    MainFragmentInterface {

    override val position: Int? get() = arguments?.getInt("position")

    val activityViewModel by activityViewModels<MainViewModel>()
    override val viewModel by viewModels<BookshelfViewModel>()

    private val importBookshelf = registerForActivityResult(HandleFileContract()) {
        kotlin.runCatching {
            it.uri?.readText(requireContext())?.let { text ->
                viewModel.importBookshelf(text, groupId)
            }
        }.onFailure {
            toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    requireContext().sendToClip(uri.toString())
                }
            }
        }
    }
    abstract val groupId: Long
    abstract val books: List<Book>
    abstract var onlyUpdateRead: Boolean
    private var groupsLiveData: LiveData<List<BookGroup>>? = null
    /** 防止连点同步按钮并发触发多次同步 */
    private var syncing = false
    private val waitDialog by lazy {
        WaitDialog(requireContext()).apply {
            setOnCancelListener {
                viewModel.addBookJob?.cancel()
            }
        }
    }

    abstract fun gotoTop()

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_bookshelf, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_remote -> startActivity<RemoteBookActivity>()
            R.id.menu_sync -> syncNow()
            R.id.menu_update_toc -> activityViewModel.upToc(books, onlyUpdateRead)
            R.id.menu_bookshelf_layout -> configBookshelf()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_add_local -> startActivity<ImportBookActivity>()
            R.id.menu_bookshelf_manage -> startActivity<BookshelfManageActivity> {
                putExtra("groupId", groupId)
            }

            R.id.menu_download -> startActivity<CacheActivity> {
                putExtra("groupId", groupId)
            }
        }
    }

    /**
     * 手动触发一次完整同步。
     *
     * 与书架下拉刷新走同一条链路：先补传离线积压的进度，再拉云端进度，
     * 然后同步分组/书签/书源与排版配置。下拉刷新是隐式入口，
     * 这里给一个显式按钮——用户不必猜到「下拉」才知道能同步。
     */
    private fun syncNow() {
        if (syncing) return
        // 未配置 WebDAV 时直接提示，而不是跑一遍空同步再报「已是最新」——
        // 那会让用户以为同步成功，实际一次请求都没发出去。
        if (AppWebDav.authorization == null) {
            requireContext().toastOnUi(R.string.web_dav_not_configured)
            return
        }
        syncing = true
        waitDialog.apply {
            setText(getString(R.string.sync_now))
            setCancelable(false)
            show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val updated = withContext(IO) {
                val count = AppWebDav.downloadAllBookProgress(withUserData = true)
                AppWebDav.syncReadConfig()
                count
            }
            waitDialog.dismiss()
            syncing = false
            if (updated > 0) {
                requireContext().toastOnUi(getString(R.string.sync_progress_done, updated))
            } else {
                requireContext().toastOnUi(R.string.sync_now_no_change)
            }
        }
    }

    protected fun initBookGroupData() {
        groupsLiveData?.removeObservers(viewLifecycleOwner)
        groupsLiveData = appDb.bookGroupDao.show.apply {
            observe(viewLifecycleOwner) {
                upGroup(it)
            }
        }
    }

    abstract fun upGroup(data: List<BookGroup>)

    abstract fun upSort()

    override fun observeLiveBus() {
        viewModel.addBookProgressLiveData.observe(this) { count ->
            if (count < 0) {
                waitDialog.dismiss()
            } else {
                waitDialog.setText("添加中... ($count)")
            }
        }
    }

    @SuppressLint("InflateParams")
    fun showAddBookByUrlAlert() {
        alert(titleResource = R.string.add_book_url) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    waitDialog.setText("添加中...")
                    waitDialog.show()
                    viewModel.addBookByUrl(it)
                }
            }
            cancelButton()
        }
    }

    @SuppressLint("InflateParams")
    fun configBookshelf() {
        alert(titleResource = R.string.bookshelf_layout) {
            var bookshelfSort = AppConfig.bookshelfSort
            val alertBinding =
                DialogBookshelfConfigBinding.inflate(layoutInflater)
                    .apply {
                        if (bookshelfSort !in 0..<spSort.adapter.count) {
                            bookshelfSort = 0
                            AppConfig.bookshelfSort = 0
                        }
                        swShowUnread.isChecked = AppConfig.showUnread
                        swShowLastUpdateTime.isChecked = AppConfig.showLastUpdateTime
                        swShowWaitUpBooks.isChecked = AppConfig.showWaitUpCount
                        swShowBookshelfFastScroller.isChecked = AppConfig.showBookshelfFastScroller
                        spSort.setSelection(bookshelfSort)
                    }
            customView { alertBinding.root }
            okButton {
                alertBinding.apply {
                    if (AppConfig.showUnread != swShowUnread.isChecked) {
                        AppConfig.showUnread = swShowUnread.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showLastUpdateTime != swShowLastUpdateTime.isChecked) {
                        AppConfig.showLastUpdateTime = swShowLastUpdateTime.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showWaitUpCount != swShowWaitUpBooks.isChecked) {
                        AppConfig.showWaitUpCount = swShowWaitUpBooks.isChecked
                        activityViewModel.postUpBooksLiveData(true)
                    }
                    if (AppConfig.showBookshelfFastScroller != swShowBookshelfFastScroller.isChecked) {
                        AppConfig.showBookshelfFastScroller = swShowBookshelfFastScroller.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (bookshelfSort != spSort.selectedItemPosition) {
                        AppConfig.bookshelfSort = spSort.selectedItemPosition
                        upSort()
                    }
                }
            }
            cancelButton()
        }
    }

    private fun importBookshelfAlert(groupId: Long) {
        alert(titleResource = R.string.import_bookshelf) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url/json"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    viewModel.importBookshelf(it, groupId)
                }
            }
            cancelButton()
            neutralButton(R.string.select_file) {
                importBookshelf.launch {
                    mode = HandleFileContract.FILE
                    allowExtensions = arrayOf("txt", "json")
                }
            }
        }
    }

}
