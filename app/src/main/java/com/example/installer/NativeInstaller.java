package com.example.installer;

import android.content.Context;

public class NativeInstaller {
    
    static {
        System.loadLibrary("installer");
    }
    
    private static final String TAG = "NativeInstaller";
    
    // Native methods
    private native String nativeGetString(String key);
    private native int nativeGetInt(String key);
    private native boolean nativeInstallApk(Context context, String apkPath);
    
    public boolean installApk(Context context, String apkPath) {
        return nativeInstallApk(context, apkPath);
    }
}