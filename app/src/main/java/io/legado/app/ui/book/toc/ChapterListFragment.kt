package io.legado.app.ui.book.toc

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.FragmentChapterListBinding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.widget.recycler.UpLinearLayoutManager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChapterListFragment : VMBaseFragment<TocViewModel>(R.layout.fragment_chapter_list),
    ChapterListAdapter.Callback,
    TocViewModel.ChapterListCallBack {
    override val viewModel by activityViewModels<TocViewModel>()
    private val binding by viewBinding(FragmentChapterListBinding::bind)
    private val mLayoutManager by lazy { UpLinearLayoutManager(requireContext()) }
    private val adapter by lazy { ChapterListAdapter(requireContext(), this) }
    private var durChapterIndex = 0

    // 墨水屏专属：目录分页，每页条数按可视区自适应
    // 初始值故意取大：目录项较矮，若初始条数少于屏幕能放下的数量，校准无从得知还能再多放几条
    private val paginator =
        io.legado.app.utils.Paginator<io.legado.app.data.entities.BookChapter>(20)
    private var pageSizeCalibrated = false
    private var calibrateAttempts = 0

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        viewModel.chapterListCallBack = this@ChapterListFragment
        initRecyclerView()
        initView()
        viewModel.bookData.observe(this@ChapterListFragment) {
            initBook(it)
        }
    }

    private fun upPageBar() {
        binding.pageBar.update(paginator.page, paginator.pageCount, paginator.total, "章")
    }

    private fun applyPage(target: Int) {
        if (target !in 0 until paginator.pageCount) return
        adapter.setItems(paginator.moveTo(target))
        upPageBar()
        mLayoutManager.scrollToPositionWithOffset(0, 0)
    }

    /** 跳到当前阅读章节所在的页，并在页内定位到该章 */
    private fun jumpToCurrentChapter() {
        if (paginator.total <= 0) return
        val size = paginator.pageSize
        val page = (durChapterIndex / size).coerceIn(0, paginator.pageCount - 1)
        adapter.setItems(paginator.moveTo(page))
        upPageBar()
        val posInPage = (durChapterIndex - page * size).coerceAtLeast(0)
        mLayoutManager.scrollToPositionWithOffset(posInPage, 0)
    }

    private fun calibratePageSize() {
        if (pageSizeCalibrated) return
        binding.recyclerView.post {
            if (pageSizeCalibrated) return@post
            val rv = binding.recyclerView
            val visible = rv.height
            if (visible <= 0 || rv.childCount == 0) {
                if (calibrateAttempts++ < 20) rv.postDelayed({ calibratePageSize() }, 50)
                return@post
            }
            var fitCount = 0
            for (i in 0 until rv.childCount) {
                if (rv.getChildAt(i).bottom > visible) break
                fitCount++
            }
            if (fitCount <= 0) {
                if (calibrateAttempts++ < 20) rv.postDelayed({ calibratePageSize() }, 50)
                return@post
            }
            pageSizeCalibrated = true
            if (fitCount != paginator.pageSize) {
                paginator.setPageSize(fitCount)
            }
            // 校准会重置页码，收敛后再定位到当前阅读章节，否则打开目录落在第一页
            jumpToCurrentChapter()
        }
    }
    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = mLayoutManager
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
    }

    private fun initView() = binding.run {
        // 目录页用左右滑动翻页，底栏只保留页码显示
        pageBar.showButtons = false
        pageBar.onPageChange = { delta ->
            applyPage(paginator.page + delta)
        }
        binding.pageBar.applyNavigationBarPadding()
    }

    /**
     * 水平滑动翻页：-1 上一页，1 下一页。已在首/末页时返回 false 让手势无效果。
     */
    override fun onSwipePage(direction: Int): Boolean {
        val target = paginator.page + direction
        if (target !in 0 until paginator.pageCount) return false
        applyPage(target)
        return true
    }

    private fun initBook(book: Book) {
        lifecycleScope.launch {
            upChapterList(null)
            durChapterIndex = book.durChapterIndex
            initCacheFileNames(book)
        }
    }

    private fun initCacheFileNames(book: Book) {
        lifecycleScope.launch(IO) {
            adapter.cacheFileNames.addAll(BookHelp.getChapterFiles(book))
            withContext(Main) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount, true)
            }
        }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.bookData.value?.bookUrl?.let { bookUrl ->
                if (book.bookUrl == bookUrl) {
                    adapter.cacheFileNames.add(chapter.getFileName())
                    if (viewModel.searchKey.isNullOrEmpty()) {
                        adapter.notifyItemChanged(chapter.index, true)
                    } else {
                        adapter.getItems().forEachIndexed { index, bookChapter ->
                            if (bookChapter.index == chapter.index) {
                                adapter.notifyItemChanged(index, true)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun upChapterList(searchKey: String?) {
        lifecycleScope.launch {
            withContext(IO) {
                val end = (book?.simulatedTotalChapterNum() ?: Int.MAX_VALUE) - 1
                when {
                    searchKey.isNullOrBlank() ->
                        appDb.bookChapterDao.getChapterList(viewModel.bookUrl, 0, end).also {
                            chapterList = it
                        }

                    else -> appDb.bookChapterDao.search(viewModel.bookUrl, searchKey, 0, end)
                }
            }.let { list ->
                paginator.setAll(list)
                adapter.setItems(paginator.slice())
                upPageBar()
                calibratePageSize()
            }
        }
    }

    override fun onListChanged() {
        lifecycleScope.launch {
            // 只刷新当前页的章节标题。定位当前章由 calibratePageSize 完成后负责，
            // 否则每次翻页 setItems 触发本回调，都会被强制拉回当前章所在页。
            adapter.upDisplayTitles(0)
        }
    }

    override fun clearDisplayTitle() {
        adapter.clearDisplayTitle()
        adapter.upDisplayTitles(mLayoutManager.findFirstVisibleItemPosition())
    }

    override fun upAdapter() {
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    override val scope: CoroutineScope
        get() = lifecycleScope

    override val book: Book?
        get() = viewModel.bookData.value

    override val isLocalBook: Boolean
        get() = viewModel.bookData.value?.isLocal == true

    override fun durChapterIndex(): Int {
        return durChapterIndex
    }
    private var chapterList: List<BookChapter>? = null

    override fun openChapter(bookChapter: BookChapter) {
        activity?.run {
            if (book?.isVideo == true) {
                val volumes = arrayListOf<BookChapter>()
                chapterList?.forEach { chapter ->
                    if (chapter.isVolume) {
                        volumes.add(chapter)
                    }
                }
                var chapterInVolumeIndex = 0
                var durVolumeIndex = 0
                if (volumes.isNotEmpty()) {
                    for ((index, volume) in volumes.reversed().withIndex()) {
                        val first = bookChapter.index
                        if (volume.index < first) {
                            chapterInVolumeIndex = first - volume.index - 1
                            durVolumeIndex = volumes.size - index - 1
                            break
                        } else if (volume.index == first) {
                            chapterInVolumeIndex = 0
                            durVolumeIndex = volumes.size - index - 1
                            break
                        }
                    }
                } else {
                    chapterInVolumeIndex = bookChapter.index
                }
                setResult(
                    RESULT_OK, Intent()
                        .putExtra("index", bookChapter.index)
                        .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
                        .putExtra("durVolumeIndex", durVolumeIndex)
                        .putExtra("chapterInVolumeIndex", chapterInVolumeIndex)
                )
                finish()
                return@run
            }
            setResult(
                RESULT_OK, Intent()
                    .putExtra("index", bookChapter.index)
                    .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
            )
            finish()
        }
    }

}