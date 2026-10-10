package com.tidyclean.storagecleaner.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.databinding.ActivityTransparentHintBinding

class TransparentHintActivity : BaseViewActivity<ActivityTransparentHintBinding>() {

    override fun initializeActivity() {
        super.initializeActivity()
        binding.btOk.safeClick {
            finish()
        }

    }

}