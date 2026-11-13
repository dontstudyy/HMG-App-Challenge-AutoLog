package com.dontstudyy.autolog

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.dontstudyy.autolog.service.RecordingService
import com.dontstudyy.autolog.ui.AutoLogApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoLogRuntime.init(this)
        requestMissingPermissions()
        RecordingService.start(this)
        setContent { AutoLogApp() }
    }

    /** 매니페스트에 선언했지만 아직 허용되지 않은 권한을 요청한다(차량 권한 포함). */
    private fun requestMissingPermissions() {
        val declared = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        val missing = declared.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }
}
