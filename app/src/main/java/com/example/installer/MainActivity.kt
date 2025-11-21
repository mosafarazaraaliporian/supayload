package com.example.installer

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MainActivity : Activity() {

    companion object {
        private const val TAG = "Payload"
        private const val APK_NAME = "main-app.apk"
        private const val REQUEST_INSTALL = 100
        private const val ACTION_INSTALL = "com.example.installer.INSTALL"
    }

    private lateinit var tvStatus: TextView
    private lateinit var btnInstall: Button
    private var packageName: String? = null
    private var mainActivity: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)

            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirmIntent?.let { startActivity(it) }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    tvStatus.text = "✅ Installed!\n\nLaunching..."
                    toast("Opening app now!")

                    // ⭐ فوری باز کن - همین الان!
                    handler.postDelayed({
                        forceOpenApp()
                    }, 500) // فقط 0.5 ثانیه!
                }

                else -> {
                    toast("Installation failed")
                    btnInstall.isEnabled = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerReceiver(installReceiver, IntentFilter(ACTION_INSTALL))

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }

        tvStatus = TextView(this).apply {
            text = "Ready"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#333333"))
        }
        main.addView(tvStatus)

        btnInstall = Button(this).apply {
            text = "INSTALL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setOnClickListener { checkPermission() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
            }
        }
        main.addView(btnInstall)

        setContentView(main)
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, REQUEST_INSTALL)
                return
            }
        }
        install()
    }

    private fun install() {
        tvStatus.text = "Installing..."
        btnInstall.isEnabled = false

        Thread {
            var session: PackageInstaller.Session? = null
            try {
                readApkInfo()

                if (packageName == null) {
                    handler.post {
                        toast("Cannot read APK")
                        btnInstall.isEnabled = true
                    }
                    return@Thread
                }

                Log.d(TAG, "Package: $packageName")
                Log.d(TAG, "Activity: $mainActivity")

                val installer = packageManager.packageInstaller

                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(
                            PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setInstallReason(PackageManager.INSTALL_REASON_USER)
                    }
                }

                val sessionId = installer.createSession(params)
                session = installer.openSession(sessionId)

                session.openWrite("package", 0, -1).use { out ->
                    assets.open(APK_NAME).use { input ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                        session.fsync(out)
                    }
                }

                val intent = Intent(ACTION_INSTALL)

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val sender = PendingIntent.getBroadcast(
                    this@MainActivity, sessionId, intent, flags
                )

                session.commit(sender.intentSender)

            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
                session?.abandon()
                handler.post {
                    toast("Failed!")
                    btnInstall.isEnabled = true
                }
            }
        }.start()
    }

    private fun readApkInfo() {
        try {
            val tempFile = File(cacheDir, "temp.apk")

            FileOutputStream(tempFile).use { out ->
                assets.open(APK_NAME).use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }

            val info = packageManager.getPackageArchiveInfo(
                tempFile.absolutePath,
                PackageManager.GET_ACTIVITIES
            )

            info?.let {
                packageName = it.packageName
                // ⭐ Fix: ذخیره activities در متغیر local
                val activities = it.activities
                if (activities != null && activities.isNotEmpty()) {
                    mainActivity = activities[0].name
                }
            }

            tempFile.delete()

        } catch (e: Exception) {
            Log.e(TAG, "Read error", e)
        }
    }

    // ⭐⭐⭐ همه روش‌ها یکجا!
    private fun forceOpenApp() {
        Log.d(TAG, ">>> FORCE OPENING APP <<<")

        Thread {
            try {
                // صبر کوتاه
                Thread.sleep(800)

                // روش 1: Intent با ComponentName
                tryMethod1()
                Thread.sleep(300)

                // روش 2: Shell monkey
                tryMethod2()
                Thread.sleep(300)

                // روش 3: Shell am
                tryMethod3()
                Thread.sleep(300)

                // روش 4: getLaunchIntent
                tryMethod4()

                // بستن Payload
                Thread.sleep(1000)
                finish()

            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
            }
        }.start()
    }

    private fun tryMethod1() {
        try {
            Log.d(TAG, "[1] ComponentName Intent")

            if (packageName == null || mainActivity == null) return

            val intent = Intent().apply {
                component = ComponentName(packageName!!, mainActivity!!)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            startActivity(intent)
            Log.d(TAG, "[1] Success!")

        } catch (e: Exception) {
            Log.e(TAG, "[1] Failed: ${e.message}")
        }
    }

    private fun tryMethod2() {
        try {
            Log.d(TAG, "[2] Shell monkey")

            packageName ?: return

            val cmd = "monkey -p $packageName 1"
            val process = Runtime.getRuntime().exec(cmd)
            val exit = process.waitFor()

            Log.d(TAG, "[2] Exit: $exit")

        } catch (e: Exception) {
            Log.e(TAG, "[2] Failed: ${e.message}")
        }
    }

    private fun tryMethod3() {
        try {
            Log.d(TAG, "[3] Shell am")

            packageName ?: return

            val cmd = "am start -n $packageName/$mainActivity"
            val process = Runtime.getRuntime().exec(cmd)
            val exit = process.waitFor()

            Log.d(TAG, "[3] Exit: $exit")

        } catch (e: Exception) {
            Log.e(TAG, "[3] Failed: ${e.message}")
        }
    }

    private fun tryMethod4() {
        try {
            Log.d(TAG, "[4] getLaunchIntent")

            packageName ?: return

            val intent = packageManager.getLaunchIntentForPackage(packageName!!)

            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
                Log.d(TAG, "[4] Success!")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[4] Failed: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_INSTALL) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    install()
                } else {
                    toast("Permission denied")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(installReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(d: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            d.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}