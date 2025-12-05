package com.example.installer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class NativeInstaller {
    
    private static final String TAG = "NativeInstaller";
    private static final String ACTION_INSTALL = "com.example.installer.INSTALL";
    private static final int BUFFER_SIZE = 65536;
    
    public boolean installApk(Context context, String apkPath) {
        PackageInstaller.Session session = null;
        int sessionId = -1;
        
        try {
            File apkFile = new File(apkPath);
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found: " + apkPath);
                return false;
            }
            
            long fileSize = apkFile.length();
            if (fileSize == 0) {
                Log.e(TAG, "APK file is empty");
                return false;
            }
            
            Log.d(TAG, "Starting installation");
            Log.d(TAG, "APK path: " + apkPath);
            Log.d(TAG, "APK size: " + fileSize + " bytes");
            
            PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
            
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
            
            sessionId = packageInstaller.createSession(params);
            Log.d(TAG, "Session created: " + sessionId);
            
            session = packageInstaller.openSession(sessionId);
            Log.d(TAG, "Session opened successfully");
            
            OutputStream out = session.openWrite("package", 0, fileSize);
            InputStream in = new FileInputStream(apkFile);
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            long totalWritten = 0;
            
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalWritten += read;
            }
            
            session.fsync(out);
            out.close();
            in.close();
            
            Log.d(TAG, "APK written successfully, total bytes: " + totalWritten);
            
            Intent intent = new Intent(ACTION_INSTALL);
            intent.setPackage(context.getPackageName());
            
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    flags);
            
            Log.d(TAG, "Committing session: " + sessionId);
            session.commit(pendingIntent.getIntentSender());
            session.close();
            session = null;
            
            Log.d(TAG, "Session committed successfully, installation initiated");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Installation failed", e);
            
            if (session != null) {
                try {
                    session.abandon();
                    Log.d(TAG, "Session abandoned");
                } catch (Exception e2) {
                    Log.e(TAG, "Failed to abandon session", e2);
                }
            }
            
            return false;
        }
    }
}