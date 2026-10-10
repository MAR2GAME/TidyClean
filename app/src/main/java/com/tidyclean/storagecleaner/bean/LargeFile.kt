package com.tidyclean.storagecleaner.bean

import android.net.Uri
import com.chad.library.adapter.base.entity.MultiItemEntity
import kotlin.jvm.javaClass

data class LargeFile(
    val id: Long,
    val name: String,
    val path: String,
    val size: Long,
    val type: String,
    val date: Long,
    var thumbnailUri: Uri? = null,
    var isSelected: Boolean = false,
    override var itemType: Int = 0   // 默认普通类型
) : MultiItemEntity
{
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LargeFile

        if (id != other.id) return false
        if (size != other.size) return false
        if (date != other.date) return false
        if (name != other.name) return false
        if (path != other.path) return false
        if (type != other.type) return false
        if (thumbnailUri != other.thumbnailUri) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (thumbnailUri?.hashCode() ?: 0)
        return result
    }
}
