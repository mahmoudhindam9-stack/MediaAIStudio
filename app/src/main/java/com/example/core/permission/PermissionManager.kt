package com.example.core.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

interface PermissionManager {
    fun hasPermission(permission: String): Boolean
}

class PermissionManagerImpl(private val context: Context) : PermissionManager {
    override fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
