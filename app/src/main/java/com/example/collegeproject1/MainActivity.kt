package com.example.collegeproject1

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


//        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
//        val navController = navHostFragment.navController

    }

    override fun onResume() {
        super.onResume()

        if (!havePermissions(this)) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), permissionRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode != permissionRequestCode && havePermissions(this) {
//            finish()
//        }

        if (requestCode != permissionRequestCode && havePermissions(this)) {
            finish()
        }

    }

    private fun havePermissions(context: Context) = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private val permissions = listOf(android.Manifest.permission.CAMERA)
    private val permissionRequestCode = 123


}