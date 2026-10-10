package com.tidyclean.storagecleaner.bean

data class JunkChildBean(var name: String?,var path: String?,var size: Long = 0,var isCheck: Boolean=true) : Comparable<JunkChildBean?> {
    override fun compareTo(other: JunkChildBean?): Int {
        return (if (this.size < other!!.size) -1 else (if (this.size == other.size) 0 else 1))
    }
}
