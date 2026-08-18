package com.eddyizm.tempus.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object BugReportUtil {

    const val tag = "BugReportUtil"

    @JvmStatic
    fun getAppVersionCode(context: Context): String {
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(tag, "We tried to get versionCode but Android wouldn't let us.", e)
        }
        // Kotlin wanted this
        return if (packageInfo != null) {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                packageInfo.versionCode.toString()
            })
        } else {
            "Unkown"
        }
    }

    @JvmStatic
    fun getAppVersionName(context: Context): String {
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(tag, "We tried to get versionName but Android wouldn't let us.", e)
        }
        // Kotlin wanted this
        return (if (packageInfo != null) {
            packageInfo.versionName.toString()
        } else {
            "Unknown"
        })
    }

    @JvmStatic
    fun getAndroidOS(): String {
        return Build.VERSION.RELEASE
    }

    @JvmStatic
    fun getAndroidSDK(): Int {
        return Build.VERSION.SDK_INT
    }

    @JvmStatic
    fun getDeviceBrand(): String {
        return Build.BRAND
    }

    @JvmStatic
    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER
    }

    @JvmStatic
    fun getDeviceName(): String {
        return Build.DEVICE
    }

    @JvmStatic
    fun getDeviceModel(): String {
        return Build.MODEL
    }

    @JvmStatic
    fun getDeviceABIs(): String {
        return Build.SUPPORTED_ABIS.contentToString()
    }

    @JvmStatic
    fun getPackagename(context: Context): String {
        return context.packageName;
    }

    @JvmStatic
    fun getDeviceInformation(context: Context): String {
        var s = ""
        s += "\nApp version name   : " + getAppVersionName(context)
        s += "\nApp version code   : " + getAppVersionCode(context)
        s += "\nAndroid OS version : " + getAndroidOS()
        s += "\nAndroid SDK version: " + getAndroidSDK()
        s += "\nDevice Brand       : " + getDeviceBrand()
        s += "\nDevice Manufacturer: " + getDeviceManufacturer()
        s += "\nDevice Name        : " + getDeviceName()
        s += "\nDevice Model       : " + getDeviceModel()
        s += "\n" + getDeviceABIs()
        s += "\n" + getPackagename(context)
        return s
    }

}