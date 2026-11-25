package io.legado.app.ui.book.trash

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookTrash
import io.legado.app.databinding.ItemBookTrashBinding
import io.legado.app.help.sync.BookTrashPolicy

class BookTrashAdapter(context: Context, private val callBack: CallBack) :
    RecyclerAdapter<BookTrash, ItemBookTrashBinding>(context) {

    interface CallBack {
        fun onRestore(item: BookTrash)
        fun onPurge(item: BookTrash)
    }

    private val now: Long = System.currentTimeMillis()

    val diffItemCallBack = object : DiffUtil.ItemCallback<BookTrash>() {
        override fun areItemsTheSame(oldItem: BookTrash, newItem: BookTrash): Boolean =
            oldItem.bookUrl == newItem.bookUrl

        override fun areContentsTheSame(oldItem: BookTrash, newItem: BookTrash): Boolean =
            oldItem.name == newItem.name &&
                    oldItem.author == newItem.author &&
                    oldItem.deletedAt == newItem.deletedAt
    }

    override fun getViewBinding(parent: ViewGroup): ItemBookTrashBinding =
        ItemBookTrashBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookTrashBinding,
        item: BookTrash,
        payloads: MutableList<Any>
    ) = binding.run {
        tvName.text = item.name
        val days = BookTrashPolicy.remainingDays(item.deletedAt, now)
        tvInfo.text = context.getString(R.string.trash_item_info, item.author, days)
        ivRestore.setOnClickListener { callBack.onRestore(item) }
        ivDelete.setOnClickListener { callBack.onPurge(item) }
        root.setOnClickListener { callBack.onRestore(item) }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookTrashBinding) = Unit
}
