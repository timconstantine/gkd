package li.gkd.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "selector_library",
)
data class SelectorLibraryItem(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = buildUniqueTimeMillisId(),
    @ColumnInfo(name = "selector") val selector: String,
    @ColumnInfo(name = "name") val name: String? = null,
    // null means the selector isn't scoped to a particular app and can be reused anywhere
    @ColumnInfo(name = "app_id") val appId: String? = null,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "create_time") val createTime: Long = System.currentTimeMillis(),
) {
    @Dao
    interface SelectorLibraryDao {

        @Query("SELECT * FROM selector_library ORDER BY create_time DESC")
        fun query(): Flow<List<SelectorLibraryItem>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg items: SelectorLibraryItem): List<Long>

        @Delete
        suspend fun delete(vararg items: SelectorLibraryItem): Int
    }
}
