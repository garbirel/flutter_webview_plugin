package com.flutter_webview_plugin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.widget.FrameLayout;

import com.tencent.smtt.sdk.CookieManager;
import com.tencent.smtt.sdk.DownloadListener;
import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import androidx.core.content.FileProvider;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

import static android.app.Activity.RESULT_OK;
import static android.os.Build.VERSION_CODES.M;

/**
 * Created by lejard_h on 20/12/2017.
 */

class WebviewManager {

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArray;
    private final static int FILECHOOSER_RESULTCODE = 1;

    public static final int INPUT_FILE_REQUEST_CODE = 100;
    public static final int INPUT_VIDEO_CODE = 200;
    private String mCameraPhotoPath;
    private Uri photoURI;

    @TargetApi(7)
    class ResultHandler {
        public boolean handleResult(int requestCode, int resultCode, Intent intent) {
            boolean handled = false;
            if (Build.VERSION.SDK_INT >= 21) {
                if (requestCode == FILECHOOSER_RESULTCODE) {
                    Uri[] results = null;
                    if (resultCode == Activity.RESULT_OK && intent != null) {
                        String dataString = intent.getDataString();
                        if (dataString != null) {
                            results = new Uri[]{Uri.parse(dataString)};
                        }
                    }
                    if (mUploadMessageArray != null) {
                        mUploadMessageArray.onReceiveValue(results);
                        mUploadMessageArray = null;
                    }
                    handled = true;
                } else if(requestCode == INPUT_FILE_REQUEST_CODE || requestCode == INPUT_VIDEO_CODE){
                    if(resultCode == Activity.RESULT_OK){
                        Uri[] results = null;
                        Uri mUri = null;
                        if(requestCode == INPUT_FILE_REQUEST_CODE){
                            if(intent == null){
                                if(Build.VERSION.SDK_INT > M) {
                                    mUri=photoURI;
                                    results=new Uri[]{mUri};
                                } else{
                                    if (mCameraPhotoPath != null) {
                                        mUri = Uri.parse(mCameraPhotoPath);
                                        results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                                    }
                                }
                            } else {
                                Uri nUri = intent.getData();
                                if (nUri != null) {
                                    mUri =nUri;
                                    results = new Uri[]{nUri};
                                }
                            }
                        } else if(requestCode == INPUT_VIDEO_CODE){
                            mUri = intent.getData();
                            results = new Uri[]{mUri};
                        }

                        mUploadMessageArray.onReceiveValue(results);
                        mUploadMessageArray = null;
                        handled = true;
                    }
                }
            } else {
                if (requestCode == FILECHOOSER_RESULTCODE) {
                    Uri result = null;
                    if (resultCode == RESULT_OK && intent != null) {
                        result = intent.getData();
                    }
                    if (mUploadMessage != null) {
                        mUploadMessage.onReceiveValue(result);
                        mUploadMessage = null;
                    }
                    handled = true;
                } else if(requestCode == INPUT_FILE_REQUEST_CODE || requestCode == INPUT_VIDEO_CODE){
                    if(resultCode == Activity.RESULT_OK){
                        Uri mUri = null;
                        if(requestCode == INPUT_FILE_REQUEST_CODE){
                            if(intent == null){
                                if (mCameraPhotoPath != null) {
                                    mUri = Uri.parse(mCameraPhotoPath);
                                }
                            } else {
                                Uri nUri = intent.getData();
                                if (nUri != null) {
                                    mUri =nUri;
                                }
                            }
                        } else if(requestCode == INPUT_VIDEO_CODE){
                            mUri = intent.getData();
                        }
                        mUploadMessage.onReceiveValue(mUri);
                        mUploadMessage = null;
                        handled = true;
                    }
                }
            }
            return handled;
        }
    }

    boolean closed = false;
    WebView webView;
    Activity activity;
    ResultHandler resultHandler;

    private class FileDownLoadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            activity.startActivity(intent);
        }
    }
    WebviewManager(final Activity activity, List<String> interceptUrls) {
        this.webView = new ObservableWebView(activity);
        this.activity = activity;
        this.resultHandler = new ResultHandler();
        WebViewClient webViewClient = new BrowserClient(interceptUrls);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webView.setDownloadListener(new FileDownLoadListener());

        webView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            if (webView.canGoBack()) {
                                webView.goBack();
                            } else {
                                close();
                            }
                            return true;
                    }
                }

                return false;
            }
        });

        ((ObservableWebView) webView).setOnScrollChangedCallback(new ObservableWebView.OnScrollChangedCallback(){
            public void onScroll(int x, int y, int oldx, int oldy){
                Map<String, Object> yDirection = new HashMap<>();
                yDirection.put("yDirection", (double)y);
                FlutterWebviewPlugin.channel.invokeMethod("onScrollYChanged", yDirection);
                Map<String, Object> xDirection = new HashMap<>();
                xDirection.put("xDirection", (double)x);
                FlutterWebviewPlugin.channel.invokeMethod("onScrollXChanged", xDirection);
            }
        });

        webView.setWebViewClient(webViewClient);
        webView.setWebChromeClient(new WebChromeClient()
        {
            //The undocumented magic method override
            //Eclipse will swear at you if you try to put @Override here
            // For Android 3.0+
            public void openFileChooser(ValueCallback<Uri> uploadMsg) {

                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);

            }

            // For Android 3.0+
            public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                activity.startActivityForResult(
                        Intent.createChooser(i, "File Browser"),
                        FILECHOOSER_RESULTCODE);
            }

            //For Android 4.1
            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }
                mUploadMessage = uploadMsg;

                if("iamge/*".equals(acceptType)){
                    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
                        File photoFile = null;
                        try {
                            photoFile = createImageFile();
                            takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath);
                        } catch (IOException ex) {
                            Log.e("TAG", "Unable to create Image File", ex);
                        }
                        if (photoFile != null) {
                            mCameraPhotoPath = "file:" +
                                    photoFile.getAbsolutePath();
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                                    Uri.fromFile(photoFile));
                        } else {
                            takePictureIntent = null;
                        }
                        activity.startActivityForResult(takePictureIntent, INPUT_FILE_REQUEST_CODE);
                    }
                } else if("video/*".equals(acceptType)) {
                    Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                    if (takeVideoIntent.resolveActivity(activity.getPackageManager()) != null) {
                        activity.startActivityForResult(takeVideoIntent, INPUT_VIDEO_CODE);
                    }
                }
//                activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
            }

            //For Android 5.0+
            public boolean onShowFileChooser(
                    WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (mUploadMessageArray != null) {
                    mUploadMessageArray.onReceiveValue(null);
                }
                mUploadMessageArray = filePathCallback;

                String[] acceptTypes = fileChooserParams.getAcceptTypes();
                if (acceptTypes[0].equals("image/*")) {
                    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
                        File photoFile = null;
                        try {
                            photoFile = createImageFile();
                            takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath);
                        } catch (IOException ex) {
                            Log.e("TAG", "Unable to create Image File", ex);
                        }
                        //适配7.0
                        if (Build.VERSION.SDK_INT > M) {
                            if (photoFile != null) {
                                photoURI = FileProvider.getUriForFile(activity, "ymkj.hugeRich.com.fileprovider", photoFile);
                                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                            }
                        } else {
                            if (photoFile != null) {
                                mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                            } else {
                                takePictureIntent = null;
                            }
                        }
                    }
                    activity.startActivityForResult(takePictureIntent,INPUT_FILE_REQUEST_CODE);
                } else if (acceptTypes[0].equals("video/*")) {
                    Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                    if (takeVideoIntent.resolveActivity(activity.getPackageManager()) != null) {
                        activity.startActivityForResult(takeVideoIntent, INPUT_VIDEO_CODE);
                    }
                }
                return true;
            }
        });
    }
    public  synchronized String getPackageName(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    context.getPackageName(), 0);
            return packageInfo.packageName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void clearCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(new ValueCallback<Boolean>() {
                @Override
                public void onReceiveValue(Boolean aBoolean) {

                }
            });
        } else {
            CookieManager.getInstance().removeAllCookie();
        }
    }

    private void clearCache() {
        webView.clearCache(true);
        webView.clearFormData();
    }

    void openUrl(
            boolean withJavascript,
            boolean clearCache,
            boolean hidden,
            boolean clearCookies,
            String userAgent,
            String url,
            Map<String, String> headers,
            boolean withZoom,
            boolean withLocalStorage,
            boolean scrollBar,
            boolean supportMultipleWindows,
            boolean appCacheEnabled,
            boolean allowFileURLs
    ) {
        webView.getSettings().setJavaScriptEnabled(withJavascript);
        webView.getSettings().setBuiltInZoomControls(withZoom);
        webView.getSettings().setSupportZoom(withZoom);
        webView.getSettings().setDomStorageEnabled(withLocalStorage);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(supportMultipleWindows);

        webView.getSettings().setSupportMultipleWindows(supportMultipleWindows);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAppCacheEnabled(appCacheEnabled);

        webView.getSettings().setAllowFileAccessFromFileURLs(allowFileURLs);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(allowFileURLs);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.getSettings().setDisplayZoomControls(false);

        if (clearCache) {
            clearCache();
        }

        if (hidden) {
            webView.setVisibility(View.INVISIBLE);
        }

        if (clearCookies) {
            clearCookies();
        }

        if (userAgent != null) {
            webView.getSettings().setUserAgentString(userAgent);
        }

        if (!scrollBar) {
            webView.setVerticalScrollBarEnabled(false);
        }

        if (headers != null) {
            webView.loadUrl(url, headers);
        } else {
            webView.loadUrl(url);
        }
    }

    void reloadUrl (String url){
        webView.loadUrl(url);
    }

    void close (MethodCall call, MethodChannel.Result result){
        if (webView != null) {
            ViewGroup vg = (ViewGroup) (webView.getParent());
            vg.removeView(webView);
        }
        webView = null;
        if (result != null) {
            result.success(null);
        }

        closed = true;
        FlutterWebviewPlugin.channel.invokeMethod("onDestroy", null);
    }

    void close () {
        close(null, null);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    void eval (MethodCall call,final MethodChannel.Result result){
        String code = call.argument("code");

        webView.evaluateJavascript(code, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                result.success(value);
            }
        });
    }
    /**
     * Reloads the Webview.
     */
    void reload (MethodCall call, MethodChannel.Result result){
        if (webView != null) {
            webView.reload();
        }
    }
    /**
     * Navigates back on the Webview.
     */
    void back (MethodCall call, MethodChannel.Result result){
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            result.success(false);
        }else{
            close();
            result.success(true);
        }
    }
    /**
     * Navigates forward on the Webview.
     */
    void forward (MethodCall call, MethodChannel.Result result){
        if (webView != null && webView.canGoForward()) {
            webView.goForward();
        }
    }

    void resize (FrameLayout.LayoutParams params){
        webView.setLayoutParams(params);
    }
    /**
     * Checks if going back on the Webview is possible.
     */
    boolean canGoBack () {
        return webView.canGoBack();
    }
    /**
     * Checks if going forward on the Webview is possible.
     */
    boolean canGoForward () {
        return webView.canGoForward();
    }
    void hide (MethodCall call, MethodChannel.Result result){
        if (webView != null) {
            webView.setVisibility(View.INVISIBLE);
        }
    }
    void show (MethodCall call, MethodChannel.Result result){
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
        }
    }

    void stopLoading (MethodCall call, MethodChannel.Result result){
        if (webView != null) {
            webView.stopLoading();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName, /* 前级 */
                ".jpg",    /* 后级 */
                storageDir    /* 文件夹 */
        );
        mCameraPhotoPath = image.getAbsolutePath();
        return image;
    }

}
