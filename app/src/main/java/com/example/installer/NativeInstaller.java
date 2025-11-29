package com.example.installer;

import android.content.Context;
import android.content.pm.PackageInstaller;

public class NativeInstaller {
    
    static {
        System.loadLibrary("installer");
    }
    
    private native int nativeCreateSession(Context context, int mode);
    private native int nativeOpenSession(Context context, int sessionId);
    private native int nativeWriteApk(Context context, int sessionId, String apkPath);
    private native int nativeCommitSession(Context context, int sessionId, String action, String packageName);
    private native int nativeAbandonSession(Context context, int sessionId);
    
    public boolean installApk(Context context, String apkPath) {
        try {
            int mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL;
            int sessionId = nativeCreateSession(context, mode);
            if (sessionId < 0) {
                return false;
            }
            
            int openResult = nativeOpenSession(context, sessionId);
            if (openResult != 0) {
                nativeAbandonSession(context, sessionId);
                return false;
            }
            
            int writeResult = nativeWriteApk(context, sessionId, apkPath);
            if (writeResult != 0) {
                nativeAbandonSession(context, sessionId);
                return false;
            }
            
            String action = "com.example.installer.INSTALL";
            int commitResult = nativeCommitSession(context, sessionId, action, context.getPackageName());
            if (commitResult != 0) {
                nativeAbandonSession(context, sessionId);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

