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
        return -1;
    }

    jobject packageManager = env->CallObjectMethod(context, g_getPackageManagerMethod);
    if (!packageManager) return -1;

    jobject installer = env->CallObjectMethod(packageManager, g_getPackageInstallerMethod);
    if (!installer) return -1;

    jmethodID openSessionMethod = env->GetMethodID(g_packageInstallerClass, "openSession", "(I)Landroid/content/pm/PackageInstaller$Session;");
    jobject session = env->CallObjectMethod(installer, openSessionMethod, sessionId);
    if (!session) return -1;

    jmethodID intentConstructor = env->GetMethodID(g_intentClass, "<init>", "(Ljava/lang/String;)V");
    const char* actionStr = env->GetStringUTFChars(action, nullptr);
    jstring actionJava = env->NewStringUTF(actionStr);
    jobject intent = env->NewObject(g_intentClass, intentConstructor, actionJava);

    const char* packageNameStr = env->GetStringUTFChars(packageName, nullptr);
    jmethodID setPackageMethod = env->GetMethodID(g_intentClass, "setPackage", "(Ljava/lang/String;)Landroid/content/Intent;");
    jstring packageNameJava = env->NewStringUTF(packageNameStr);
    env->CallObjectMethod(intent, setPackageMethod, packageNameJava);

    jint sdkVersion = env->GetStaticIntField(g_buildVersionClass, (jfieldID)g_getSdkIntMethod);
    jint flags = 0x08000000;
    if (sdkVersion >= 31) {
        flags |= 0x04000000;
    }

    jmethodID getBroadcastMethod = env->GetStaticMethodID(g_pendingIntentClass, "getBroadcast", 
        "(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;");
    
    jobject pendingIntent = env->CallStaticObjectMethod(g_pendingIntentClass, getBroadcastMethod, context, sessionId, intent, flags);
    if (!pendingIntent) {
        env->ReleaseStringUTFChars(action, actionStr);
        env->ReleaseStringUTFChars(packageName, packageNameStr);
        env->DeleteLocalRef(actionJava);
        env->DeleteLocalRef(packageNameJava);
        env->DeleteLocalRef(intent);
        return -1;
    }

    jmethodID getIntentSenderMethod = env->GetMethodID(g_pendingIntentClass, "getIntentSender", "()Landroid/content/IntentSender;");
    jobject intentSender = env->CallObjectMethod(pendingIntent, getIntentSenderMethod);

    jmethodID commitMethod = env->GetMethodID(g_sessionClass, "commit", "(Landroid/content/IntentSender;)V");
    env->CallVoidMethod(session, commitMethod, intentSender);

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
