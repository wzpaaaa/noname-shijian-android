/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */

package com.noname.shijian;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.noname.shijian.check.CheckUtils;
import com.norman.webviewup.lib.UpgradeCallback;
import com.norman.webviewup.lib.WebViewUpgrade;
import com.norman.webviewup.lib.source.UpgradeAssetSource;
import com.norman.webviewup.lib.source.UpgradePackageSource;
import com.norman.webviewup.lib.source.UpgradeSource;
import com.norman.webviewup.lib.util.ProcessUtils;
import com.norman.webviewup.lib.util.VersionUtils;

import org.apache.cordova.*;
import org.apache.cordova.engine.SystemWebView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;


public class MainActivity extends CordovaActivity {
    public final static int FILE_CHOOSER_RESULT_CODE = 1;

    public CordovaPreferences getPreferences() {
        return preferences;
    }

    private static boolean inited = false;

    private ProgressDialog WebViewUpgradeProgressDialog;

    private void ActivityOnCreate() {
        if (WebViewUpgradeProgressDialog != null) {
            WebViewUpgradeProgressDialog.hide();
            WebViewUpgradeProgressDialog.dismiss();
            WebViewUpgradeProgressDialog = null;
        }

        // Set by <content src="index.html" /> in config.xml
        loadUrl(launchUrl);

        View view = appView.getView();
        Log.e("webview", String.valueOf(view));
        SystemWebView webview = (SystemWebView) view;
        WebSettings settings = webview.getSettings();
        initWebviewSettings(webview, settings);
        Log.e("getUserAgentString", settings.getUserAgentString());
        CheckUtils.check(this, Executors.newFixedThreadPool(5));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LOG.e("onCreate", "111");
        LOG.e("onCreate", String.valueOf(savedInstanceState));
        super.onCreate(savedInstanceState);

        // enable Cordova apps to be started in the background
        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getBoolean("cdvStartInBackground", false)) {
            moveTaskToBack(true);
        }

        if (extras != null) {
            String ext = extras.getString("extensionImport");
            if (ext != null) {
                LOG.e("ext" ,ext);
                FinishImport.ext = ext;
            }
        }

        // 要申请的权限列表
        ArrayList<String> permissions = new ArrayList<>();
        String[] requestPermissions = getRequestPermissions();
        Log.e("permissions", Arrays.toString(requestPermissions));
        for (String permission: requestPermissions) {
            if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(permission)) {
                permissions.add(permission);
            }
        }
        Log.e("permissions", permissions.toString());

        if (!permissions.isEmpty()) {
            StringBuilder permissionBuilder = new StringBuilder();
            for(String s:permissions){
                permissionBuilder.append(s);
                permissionBuilder.append(' ');
            }
            (new Handler(Looper.getMainLooper())).postDelayed(new Runnable() {
                @Override
                public void run() {
                    requestPermissions(permissions.toArray(new String[permissions.size()]), 999);
                }
            },100);
        } else {
            afterHasPermissions();
        }
    }

    @NonNull
    private static String[] getRequestPermissions() {
        String [] requestPermissions;
        if (Build.VERSION.SDK_INT < 33) {
            requestPermissions = new String[] {
                    // 读取文件权限
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    // 写入文件权限
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        else {
            requestPermissions = new String[] {
                    // 读取图片权限
                    Manifest.permission.READ_MEDIA_IMAGES,
                    // 读取视频权限
                    Manifest.permission.READ_MEDIA_VIDEO,
                    // 读取音频权限
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        }
        return requestPermissions;
    }

    @Override
    /** 权限请求回调 */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 999) {
            boolean hasDenied = false;
            StringBuilder text = new StringBuilder("您未授予");
            for (int index = 0; index < grantResults.length; index++) {
                int ret = grantResults[index];
                Log.e(TAG, permissions[index]);
                Log.e(TAG, String.valueOf(ret));
                Log.e(TAG, "______________");
                if (ret != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT > 29) {
                        if (permissions[index].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                                permissions[index].equals(Manifest.permission.READ_EXTERNAL_STORAGE)) continue;
                    }
                    text.append(permissions[index]).append(",");
                    hasDenied = true;
                }
            }
            if (hasDenied &&
                    !getSharedPreferences("nonameshijian", MODE_PRIVATE)
                            .getBoolean("showFirstPermissionsDialog", false)) {
                text.append("权限。\n");
                text.append("如果您没有弹出窗口询问权限，可能是被系统的安全策略禁止，请在应用设置中手动开启权限。");
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setCancelable(false);
                TextView textView = new TextView(this);
                textView.setText(text);
                textView.setTextSize(25);
                textView.setTextColor(Color.WHITE);
                builder.setView(textView);
                ToastUtils.show(this, text.toString());
                builder.setNegativeButton("知道了", (dialog, which) -> {
                    afterHasPermissions();
                });
                builder.create().show();
                getSharedPreferences("nonameshijian", MODE_PRIVATE)
                        .edit()
                        .putBoolean("showFirstPermissionsDialog", true)
                        .apply();
            }
            else {
                afterHasPermissions();
            }
        }
    }

    private void afterHasPermissions() {
        boolean is64Bit = ProcessUtils.is64Bit();
        String[] supportBitAbis = is64Bit ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;

        // 内置的apk只有这两种，如果都不包含，就不触发升级内核操作（例如: 虚拟机需要x86）
        int indexOfArm64 = Arrays.binarySearch(supportBitAbis,"arm64-v8a");
        int indexOfArmeabi = Arrays.binarySearch(supportBitAbis,"armeabi-v7a");

        Log.e(TAG, Arrays.toString(supportBitAbis));

        if (inited || (indexOfArm64 < 0 && indexOfArmeabi < 0)) {
            ActivityOnCreate();
        }
        else {
            inited = true;

            if (WebViewUpgradeProgressDialog == null) {
                WebViewUpgradeProgressDialog = new ProgressDialog(this);
                WebViewUpgradeProgressDialog.setTitle("正在更新Webview内核");
                WebViewUpgradeProgressDialog.setCancelable(false);
                WebViewUpgradeProgressDialog.setIndeterminate(false);
                WebViewUpgradeProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                WebViewUpgradeProgressDialog.setMax(100);
                WebViewUpgradeProgressDialog.setProgress(0);
                if (WebViewUpgradeProgressDialog.isShowing()) WebViewUpgradeProgressDialog.hide();
            }

            WebViewUpgrade.addUpgradeCallback(new UpgradeCallback() {
                @Override
                public void onUpgradeProcess(float percent) {
                    if (percent <= 0.9 && !WebViewUpgradeProgressDialog.isShowing()) {
                        WebViewUpgradeProgressDialog.show();
                    }
                    WebViewUpgradeProgressDialog.setProgress((int) (percent * 100));
                }

                @Override
                public void onUpgradeComplete() {
                    Log.e(TAG, "onUpgradeComplete");
                    WebViewUpgradeProgressDialog.setProgress(100);
                    ActivityOnCreate();
                }

                @Override
                public void onUpgradeError(Throwable throwable) {
                    Log.e(TAG, "onUpgradeError: " + throwable.getMessage());
                    ActivityOnCreate();
                }
            });

            try {
                // 添加webview
                UpgradeSource upgradeSource;

                // 兼容版需要内置webview
                UpgradeAssetSource webviewUpgradeSource = new UpgradeAssetSource(
                        getApplicationContext(),
                        "com.google.android.webview_119.0.6045.194.apk",
                        new File(getApplicationContext().getFilesDir(), "com.google.android.webview/119.0.6045.194.apk")
                );

                // 其他的使用chrome就行
                UpgradePackageSource chromeUpgradeSource = new UpgradePackageSource(
                        getApplicationContext(),
                        "com.android.chrome"
                );

                if ("yuri.nakamura.noname".equals(getPackageName())) {
                    upgradeSource = webviewUpgradeSource;
                } else {
                    upgradeSource = chromeUpgradeSource;
                }

                String SystemWebViewPackageName = WebViewUpgrade.getSystemWebViewPackageName();
                // 如果webview就是chrome
                if ("com.android.chrome".equals(SystemWebViewPackageName)) {
                    ActivityOnCreate();
                    return;
                }

                PackageInfo upgradePackageInfo = getPackageManager().getPackageInfo(chromeUpgradeSource.getPackageName(), 0);
                if (upgradePackageInfo != null) {
                    // google webview应当等同于chrome
                    if (upgradeSource == chromeUpgradeSource && "com.google.android.webview".equals(SystemWebViewPackageName) && "com.android.chrome".equals(chromeUpgradeSource.getPackageName())) {
                        SystemWebViewPackageName = "com.android.chrome";
                    }
                    if (SystemWebViewPackageName.equals(chromeUpgradeSource.getPackageName())
                            && VersionUtils.compareVersion( WebViewUpgrade.getSystemWebViewPackageVersion(), upgradePackageInfo.versionName) >= 0) {
                        Toast.makeText(getApplicationContext(), "系统Webview版本较新，无需升级", Toast.LENGTH_LONG).show();
                        ActivityOnCreate();
                        return;
                    }
                    WebViewUpgrade.upgrade(upgradeSource);
                } else {
                    ActivityOnCreate();
                }
            } catch (Exception e) {
                Log.e(TAG, String.valueOf(e));
                ActivityOnCreate();
            }
        }
    }

    private void initWebviewSettings(SystemWebView webview, WebSettings settings) {
        int textZoom = settings.getTextZoom();
        Log.e("textZoom", "WebView当前的字体变焦百分比是: " + textZoom + "%");
        settings.setTextZoom(100);
        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent + " WebViewFontSize/100% 无名杀诗笺版/" + FinishImport.getAppVersion(MainActivity.this));
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webview.addJavascriptInterface(new JavaScriptInterface(MainActivity.this, MainActivity.this, webview) , "noname_shijianInterfaces");
        WebView.setWebContentsDebuggingEnabled(true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LOG.e("onNewIntent" ,"111");
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getExtras() != null) {
            String ext = intent.getExtras().getString("extensionImport");
            if (ext != null) {
                LOG.e("ext" ,ext);
                FinishImport.ext = ext;
            }
        }
    }

    @Override
    public void onDestroy() {
        // 获取缓存目录
        File tempDir = getExternalCacheDir();
        File[] tempFiles = tempDir.listFiles();
        if (tempFiles != null) {
            for (File tempFile : tempFiles) {
                tempFile.delete();
            }
        }

        if (WebViewUpgradeProgressDialog != null) {
            WebViewUpgradeProgressDialog.hide();
            WebViewUpgradeProgressDialog.dismiss();
            WebViewUpgradeProgressDialog = null;
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (appView != null) {
            SystemWebView webview = (SystemWebView) appView.getView();
            if (webview != null && webview.canGoBack()) {
                webview.goBack();
            }
            else {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }


    @SuppressLint("LongLogTag")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.e("onActivityResult-requestCode", String.valueOf(requestCode));
        Log.e("onActivityResult-resultCode", String.valueOf(resultCode));
        Log.e("onActivityResult-intent", String.valueOf(intent));
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri result = intent.getData();
                // 处理文件Uri对象
                Intent newIntent = new Intent(this, NonameImportActivity.class);
                newIntent.setData(result);
                newIntent.setAction(Intent.ACTION_VIEW);
                startActivity(newIntent);
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }
}