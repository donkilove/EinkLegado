package io.legado.app.ui.book.trash

import android.app.Application
import io.legado.app.base.BaseViewModel

/**
 * 回收站页面暂不需要额外状态：数据由 [io.legado.app.data.dao.BookTrashDao.flowAll] 直接驱动。
 * 保留 ViewModel 是为了与仓库其它列表页保持同一结构（VMBaseActivity 要求）。
 */
class BookTrashViewModel(application: Application) : BaseViewModel(application)
