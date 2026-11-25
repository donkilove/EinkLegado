package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookTrash
import kotlinx.coroutines.flow.Flow

@Dao
interface BookTrashDao {

    @get:Query("select * from book_trash order by deletedAt desc")
    val all: List<BookTrash>

    @get:Query("select * from book_trash order by deletedAt desc")
    val flowAll: Flow<List<BookTrash>>

    @Query("select * from book_trash where bookUrl = :bookUrl")
    fun get(bookUrl: String): BookTrash?

    @get:Query("select count(1) from book_trash")
    val count: Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg trash: BookTrash)

    @Delete
    fun delete(vararg trash: BookTrash)

    @Query("delete from book_trash")
    fun clear()
}
