package com.tidyclean.storagecleaner.bean


/**
 * Created by Administrator on 2017/11/30 0030.
 */
data class JunkMotherBean(var name: String, var size: Long = 0, var junkChildrenItems: MutableList<JunkChildBean>, var isChecked: Boolean=true, var isShowCheck: Boolean=false){

}
