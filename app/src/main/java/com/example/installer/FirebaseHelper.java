package com.example.installer;

import android.content.Context;
import android.content.res.Resources;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;

public class FirebaseHelper {
    public static String getPackageNameFromGoogleServices(Context context) {
        String currentPackageName = context.getPackageName();
        
        try {
            Resources resources = context.getResources();
            int resourceId = resources.getIdentifier("google_services", "raw", context.getPackageName());
            if (resourceId != 0) {
                InputStream inputStream = resources.openRawResource(resourceId);
                return parseGoogleServicesJson(inputStream, currentPackageName);
            }
        } catch (Exception e) {
        }
        
        try {
            InputStream inputStream = context.getAssets().open("google-services.json");
            return parseGoogleServicesJson(inputStream, currentPackageName);
        } catch (Exception e) {
        }
        
        return currentPackageName;
    }
    
    private static String parseGoogleServicesJson(InputStream inputStream, String currentPackageName) {
        try {
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();
            
            String jsonString = new String(buffer, "UTF-8");
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray clients = jsonObject.getJSONArray("client");
            
            for (int i = 0; i < clients.length(); i++) {
                JSONObject client = clients.getJSONObject(i);
                JSONObject clientInfo = client.getJSONObject("client_info");
                JSONObject androidClientInfo = clientInfo.getJSONObject("android_client_info");
                String packageName = androidClientInfo.getString("package_name");
                
                if (packageName.equals(currentPackageName)) {
                    return packageName;
                }
            }
            
            if (clients.length() > 0) {
                JSONObject firstClient = clients.getJSONObject(0);
                JSONObject clientInfo = firstClient.getJSONObject("client_info");
                JSONObject androidClientInfo = clientInfo.getJSONObject("android_client_info");
                String fallbackPackage = androidClientInfo.getString("package_name");
                return fallbackPackage;
            }
            
        } catch (Exception e) {
        }
        
        return currentPackageName;
    }
    
    public static boolean validatePackageName(Context context) {
        try {
            String currentPackage = context.getPackageName();
            String googleServicesPackage = getPackageNameFromGoogleServices(context);
            boolean isValid = currentPackage.equals(googleServicesPackage);
            return isValid;
        } catch (Exception e) {
            return true;
        }
    }
}
