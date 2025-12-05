package com.example.installer;

import android.content.Context;
import android.content.pm.PackageInstaller;
import android.util.Log;

public class NativeInstaller {
    
    private static final String TAG = "NativeInstaller";
    
    static {
        System.loadLibrary("installer");
    }
    
    private native int nativeCreateSession(Context context, int mode);
    private native int nativeOpenSession(Context context, int sessionId);
    private native int nativeWriteApk(Context context, int sessionId, String apkPath);
    private native int nativeCommitSession(Context context, int sessionId, String action, String packageName);
    private native int nativeAbandonSession(Context context, int sessionId);
    
    public boolean installApk(Context context, String apkPath) {
        int sessionId = -1;
        try {
            Log.d(TAG, "Starting installation, APK: " + apkPath);
            
            int mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL;
            sessionId = nativeCreateSession(context, mode);
            if (sessionId < 0) {
                Log.e(TAG, "Failed to create session");
                return false;
            }
            Log.d(TAG, "Session created: " + sessionId);
            
            int openResult = nativeOpenSession(context, sessionId);
            if (openResult != 0) {
                Log.e(TAG, "Failed to open session: " + openResult);
                if (sessionId >= 0) {
                    nativeAbandonSession(context, sessionId);
                }
                return false;
            }
            Log.d(TAG, "Session opened successfully");
            
            int writeResult = nativeWriteApk(context, sessionId, apkPath);
            if (writeResult != 0) {
                Log.e(TAG, "Failed to write APK: " + writeResult);
                if (sessionId >= 0) {
                    nativeAbandonSession(context, sessionId);
                }
                return false;
            }
            Log.d(TAG, "APK written successfully");
            
            String action = "com.example.installer.INSTALL";
            String packageName = context.getPackageName();
            Log.d(TAG, "Committing session: " + sessionId + ", action: " + action + ", package: " + packageName);
            
            int commitResult = nativeCommitSession(context, sessionId, action, packageName);
            Log.d(TAG, "Commit result: " + commitResult);
            
            if (commitResult != 0) {
                Log.e(TAG, "Failed to commit session: " + commitResult);
                if (sessionId >= 0) {
                    Log.d(TAG, "Abandoning session: " + sessionId);
                    nativeAbandonSession(context, sessionId);
                }
                return false;
            }
            
            Log.d(TAG, "Session committed successfully, installation initiated");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Exception during installation", e);
            if (sessionId >= 0) {
                try {
                    nativeAbandonSession(context, sessionId);
                } catch (Exception e2) {
                    Log.e(TAG, "Failed to abandon session", e2);
                }
            }
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "Throwable during installation", t);
            if (sessionId >= 0) {
                try {
                    nativeAbandonSession(context, sessionId);
                } catch (Exception e2) {
                    Log.e(TAG, "Failed to abandon session", e2);
                }
            }
            return false;
        }
    }
}

