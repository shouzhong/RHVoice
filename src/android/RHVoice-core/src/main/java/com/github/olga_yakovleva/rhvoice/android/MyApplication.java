/* Copyright (C) 2018, 2021  Olga Yakovleva <olga@rhvoice.org> */

/* This program is free software: you can redistribute it and/or modify */
/* it under the terms of the GNU Lesser General Public License as published by */
/* the Free Software Foundation, either version 3 of the License, or */
/* (at your option) any later version. */

/* This program is distributed in the hope that it will be useful, */
/* but WITHOUT ANY WARRANTY; without even the implied warranty of */
/* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the */
/* GNU Lesser General Public License for more details. */

/* You should have received a copy of the GNU Lesser General Public License */
/* along with this program.  If not, see <https://www.gnu.org/licenses/>. */

package com.github.olga_yakovleva.rhvoice.android;

import androidx.multidex.MultiDexApplication;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Provider;
import java.security.Security;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import org.conscrypt.Conscrypt;

public final class MyApplication extends MultiDexApplication {
    private static final String TAG = "RHVoice.MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        copyModel(this);
        try {
            Provider provider = Conscrypt.newProvider();
            Security.insertProviderAt(provider, 1);
            SSLContext context = SSLContext.getInstance("TLS", provider);
            context.init(null, null, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Replaced default ssl socket factory");
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                Log.e(TAG, "Error", e);
        }
        Repository.initialize(this);
    }

    private void copyModel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("model", Context.MODE_PRIVATE);
        if (prefs.getBoolean("model_init", false)) {
            return;
        }
        copyAssetsToInternalStorage(context, "app_data", "app_data");
        copyAssetsToInternalStorage(context, "shared_prefs", "shared_prefs");
        prefs.edit().putBoolean("model_init", true).apply();
    }

    private void copyAssetsToInternalStorage(Context context, String assetsPath, String destinationPath) {
        AssetManager assetManager = context.getAssets();
        String[] files;
        try {
            files = assetManager.list(assetsPath);
        } catch (Throwable e) {
            Log.e(TAG, "Error while getting files from Assets: " + e.getMessage());
            return;
        }

        if (files != null) {
            File fileDir = context.getDataDir();
            File destDir = TextUtils.isEmpty(destinationPath) ? fileDir : new File(fileDir, destinationPath);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            for (String fileName : files) {
                String assetsFilePath = assetsPath + "/" + fileName;
                File destFile = new File(destDir, fileName);
                try (InputStream in = assetManager.open(assetsFilePath);
                     OutputStream out = new FileOutputStream(destFile)) {
                    Log.d(TAG, "Copy " + assetsFilePath + " to " + destFile.getAbsolutePath() + " ...");
                    copyFile(in, out);
                    Log.d(TAG, "Copy " + assetsFilePath + " to " + destFile.getAbsolutePath() + " end");
                } catch (Throwable e) {
                    Log.e(TAG, "Error while copying file: " + e.getMessage());
                    copyAssetsToInternalStorage(context, assetsFilePath, destinationPath + "/" + fileName);
                }
            }
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
