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
        private const val TAG = "MainActivity"
        private const val APK_NAME = "plugin.apk"
        private const val REQUEST_INSTALL = 100
        private const val ACTION_INSTALL = "com.example.installer.INSTALL"
    }

    private lateinit var webView: WebView
    private var packageName: String? = null
    private var mainActivity: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isInstalling = false
    private var permissionRequested = false

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            Log.d(TAG, "installReceiver: Received status: $status")

            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    Log.d(TAG, "installReceiver: Pending user action")
                    val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirmIntent?.let { 
                        Log.d(TAG, "installReceiver: Starting confirmation intent")
                        startActivity(it) 
                    } ?: Log.w(TAG, "installReceiver: Confirm intent is null")
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    Log.d(TAG, "installReceiver: Installation successful")
                    handler.post {
                        toast("Installed!")
                    }
                    handler.postDelayed({
                        Log.d(TAG, "installReceiver: Opening installed app")
                        forceOpenApp()
                    }, 500)
                }

                else -> {
                    Log.e(TAG, "installReceiver: Installation failed with status: $status")
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    Log.e(TAG, "installReceiver: Error message: $message")
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
        Log.d(TAG, "onCreate: Starting")

        try {
            Log.d(TAG, "onCreate: Setting up system bars")
            setupSystemBars()
            Log.d(TAG, "onCreate: System bars set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error in setupSystemBars", e)
        }

        try {
            Log.d(TAG, "onCreate: Registering receiver")
            registerReceiver(installReceiver, IntentFilter(ACTION_INSTALL))
            Log.d(TAG, "onCreate: Receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error registering receiver", e)
        }

        try {
            Log.d(TAG, "onCreate: Creating WebView")
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
                        Log.d(TAG, "WebView: Page finished loading: $url")
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        Log.d(TAG, "WebView: URL loading: $url")
                        return false
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        Log.e(TAG, "WebView: Error loading page - Code: $errorCode, Desc: $description, URL: $failingUrl")
                    }
                }

                webChromeClient = WebChromeClient()
            }
            Log.d(TAG, "onCreate: WebView created successfully")

            Log.d(TAG, "onCreate: Adding JavaScript interface")
            webView.addJavascriptInterface(WebAppInterface(), "Android")
            
            Log.d(TAG, "onCreate: Setting content view")
            setContentView(webView)

            if (checkAssetExists("update/update.html")) {
                Log.d(TAG, "onCreate: Loading update.html")
                webView.loadUrl("file:///android_asset/update/update.html")
            } else {
                Log.e(TAG, "onCreate: update.html not found, showing error page")
                showErrorPage()
            }
            Log.d(TAG, "onCreate: Completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Critical error", e)
            finish()
        }
    }

    private fun checkAssetExists(assetPath: String): Boolean {
        return try {
            assets.open(assetPath).use { true }
        } catch (e: Exception) {
            Log.e(TAG, "checkAssetExists: Asset not found - $assetPath", e)
            false
        }
    }

    private fun showErrorPage() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { 
                        font-family: Arial, sans-serif; 
                        text-align: center; 
                        padding: 50px;
                        background: white;
                    }
                    h1 { color: #333; }
                    p { color: #666; }
                </style>
            </head>
            <body>
                <h1>Error</h1>
                <p>Application resources not found.</p>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun installPlugin() {
            Log.d(TAG, "WebAppInterface: installPlugin called")
            handler.post {
                if (!isInstalling) {
                    Log.d(TAG, "WebAppInterface: Starting installation")
                    checkPermission()
                } else {
                    Log.w(TAG, "WebAppInterface: Installation already in progress")
                }
            }
        }

        @JavascriptInterface
        fun onInstallComplete() {
            Log.d(TAG, "WebAppInterface: onInstallComplete called")
            handler.post {
                webView.postDelayed({
                    webView.loadUrl("file:///android_asset/update/update.html?installed=true")
                }, 1000)
            }
        }
    }

    private fun checkPermission() {
        Log.d(TAG, "checkPermission: Checking install permission")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Log.d(TAG, "checkPermission: Permission not granted, requesting")
                permissionRequested = true
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${this@MainActivity.packageName}")
                    }
                    startActivityForResult(intent, REQUEST_INSTALL)
                } catch (e: Exception) {
                    Log.e(TAG, "checkPermission: Error opening settings", e)
                    toast("Cannot open permission settings")
                    permissionRequested = false
                }
                return
            }
            Log.d(TAG, "checkPermission: Permission already granted")
        }
        Log.d(TAG, "checkPermission: Starting install")
        install()
    }

    private fun install() {
        Log.d(TAG, "install: Starting installation process")
        if (isInstalling) {
            Log.w(TAG, "install: Already installing, returning")
            return
        }
        isInstalling = true

        handler.post {
            if (checkAssetExists("update/installing.html")) {
                Log.d(TAG, "install: Loading installing.html")
                webView.loadUrl("file:///android_asset/update/installing.html")
            } else {
                Log.e(TAG, "install: installing.html not found")
            }
        }

        Thread {
            var session: PackageInstaller.Session? = null
            try {
                Log.d(TAG, "install: Reading APK info")
                readApkInfo()

                if (packageName == null) {
                    Log.e(TAG, "install: Failed to read package name")
                    handler.post {
                        isInstalling = false
                        toast("Cannot read APK")
                        webView.loadUrl("file:///android_asset/update/update.html?error=apk")
                    }
                    return@Thread
                }
                Log.d(TAG, "install: Package name: $packageName, Main activity: $mainActivity")

                Log.d(TAG, "install: Creating installer session")
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
                Log.d(TAG, "install: Session created with ID: $sessionId")
                session = installer.openSession(sessionId)

                if (!checkAssetExists(APK_NAME)) {
                    Log.e(TAG, "install: $APK_NAME not found in assets")
                    handler.post {
                        isInstalling = false
                        toast("APK file not found")
                        webView.loadUrl("file:///android_asset/update/update.html?error=apk")
                    }
                    return@Thread
                }

                Log.d(TAG, "install: Writing APK to session")
                session.openWrite("package", 0, -1).use { out ->
                    assets.open(APK_NAME).use { input ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        var totalBytes = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            totalBytes += read
                        }
                        Log.d(TAG, "install: Written $totalBytes bytes to session")
                        session.fsync(out)
                    }
                }

                Log.d(TAG, "install: Creating PendingIntent")
                val intent = Intent(ACTION_INSTALL)

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val sender = PendingIntent.getBroadcast(
                    this@MainActivity, sessionId, intent, flags
                )

                Log.d(TAG, "install: Committing session")
                session.commit(sender.intentSender)
                Log.d(TAG, "install: Session committed successfully")

            } catch (e: Exception) {
                Log.e(TAG, "install: Exception during installation", e)
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
            Log.d(TAG, "readApkInfo: Checking if $APK_NAME exists")
            if (!checkAssetExists(APK_NAME)) {
                Log.e(TAG, "readApkInfo: $APK_NAME not found")
                return
            }

            val tempFile = File(cacheDir, "temp.apk")
            Log.d(TAG, "readApkInfo: Copying APK to temp file: ${tempFile.absolutePath}")

            FileOutputStream(tempFile).use { out ->
                assets.open(APK_NAME).use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        totalBytes += read
                    }
                    Log.d(TAG, "readApkInfo: Copied $totalBytes bytes")
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e(TAG, "readApkInfo: Temp file is empty or doesn't exist")
                tempFile.delete()
                return
            }

            Log.d(TAG, "readApkInfo: Reading package info from temp file")
            val info = packageManager.getPackageArchiveInfo(
                tempFile.absolutePath,
                PackageManager.GET_ACTIVITIES
            )

            if (info == null) {
                Log.e(TAG, "readApkInfo: Failed to get package archive info")
            } else {
                packageName = info.packageName
                Log.d(TAG, "readApkInfo: Package name: $packageName")
                
                val activities = info.activities
                if (activities != null && activities.isNotEmpty()) {
                    mainActivity = activities[0].name
                    Log.d(TAG, "readApkInfo: Main activity: $mainActivity")
                } else {
                    Log.w(TAG, "readApkInfo: No activities found")
                }
            }

            tempFile.delete()

        } catch (e: Exception) {
            Log.e(TAG, "readApkInfo: Exception", e)
        }
    }

    private fun forceOpenApp() {
        Log.d(TAG, "forceOpenApp: Starting to open installed app")
        Thread {
            try {
                Log.d(TAG, "forceOpenApp: Waiting 800ms")
                Thread.sleep(800)
                
                Log.d(TAG, "forceOpenApp: Trying method 1 (ComponentName)")
                tryMethod1()
                Thread.sleep(300)
                
                Log.d(TAG, "forceOpenApp: Trying method 2 (monkey)")
                tryMethod2()
                Thread.sleep(300)
                
                Log.d(TAG, "forceOpenApp: Trying method 3 (am start)")
                tryMethod3()
                Thread.sleep(300)
                
                Log.d(TAG, "forceOpenApp: Trying method 4 (getLaunchIntent)")
                tryMethod4()

                handler.postDelayed({
                    Log.d(TAG, "forceOpenApp: Finishing MainActivity")
                    finishAndRemoveTask()
                }, 1000)

            } catch (e: Exception) {
                Log.e(TAG, "forceOpenApp: Exception", e)
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
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        if (requestCode == REQUEST_INSTALL) {
            Log.d(TAG, "onActivityResult: Checking install permission result")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    Log.d(TAG, "onActivityResult: Permission granted, starting install")
                    permissionRequested = false
                    install()
                } else {
                    Log.e(TAG, "onActivityResult: Permission denied by user")
                    permissionRequested = false
                    toast("Permission denied")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Checking if permission was granted")
        // Check if user came back from settings and granted permission
        if (permissionRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.canRequestPackageInstalls() && !isInstalling) {
                Log.d(TAG, "onResume: Permission granted, starting install")
                permissionRequested = false
                handler.postDelayed({
                    install()
                }, 300)
            } else if (!packageManager.canRequestPackageInstalls()) {
                Log.d(TAG, "onResume: Permission still not granted")
                permissionRequested = false
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
        Log.d(TAG, "onBackPressed: Called")
        try {
            if (::webView.isInitialized && webView.canGoBack()) {
                Log.d(TAG, "onBackPressed: WebView can go back")
                webView.goBack()
            } else {
                Log.d(TAG, "onBackPressed: Finishing activity")
                super.onBackPressed()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onBackPressed: Exception", e)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Starting")
        super.onDestroy()
        try {
            Log.d(TAG, "onDestroy: Unregistering receiver")
            unregisterReceiver(installReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: Error unregistering receiver", e)
        }
        try {
            if (::webView.isInitialized) {
                Log.d(TAG, "onDestroy: Destroying WebView")
                webView.destroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: Error destroying WebView", e)
        }
        Log.d(TAG, "onDestroy: Completed")
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