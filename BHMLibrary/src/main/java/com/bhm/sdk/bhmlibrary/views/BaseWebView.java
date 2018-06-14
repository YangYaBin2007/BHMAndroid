
package com.bhm.sdk.bhmlibrary.views;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.http.SslError;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.bhm.sdk.bhmlibrary.R;
import com.bhm.sdk.bhmlibrary.interfaces.WebViewCallBack;
import com.bhm.sdk.bhmlibrary.utils.FileHelper;

import java.io.IOException;
import java.io.InputStream;


/**
 * Created by bhm on 2018/6/14.
 */


public class BaseWebView extends WebView {

    private ProgressBar progressbar;
    private boolean isProgressBar = false;

    public BaseWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public @DrawableRes int setProgressBarDrawable(){
        return 0;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void init(Context context, String baseUrl, boolean isProgressBar, WebViewCallBack callBack) {
        this.isProgressBar = isProgressBar;
        if(isProgressBar) {
            progressbar = new ProgressBar(context, null,
                    android.R.attr.progressBarStyleHorizontal);
            progressbar.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
                    8, 0, 0));
            if(setProgressBarDrawable() == 0) {
                Drawable drawable = context.getResources().getDrawable(R.drawable.bg_progressbar);
                progressbar.setProgressDrawable(drawable);
            }else {
                Drawable drawable = context.getResources().getDrawable(setProgressBarDrawable());
                progressbar.setProgressDrawable(drawable);
            }
            addView(progressbar);
        }
        WebSettings wvSettings = getSettings();
        if (Build.VERSION.SDK_INT > 9) {
            this.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {

            wvSettings.setAllowFileAccessFromFileURLs(true);
        }
        wvSettings.setAppCacheEnabled(true);
        //webview会block页面加载。这是android 4.4以来google对安全机制的提升。
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            wvSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        //初始化字体为正常大小
        wvSettings.setTextSize(WebSettings.TextSize.NORMAL);
        wvSettings.setBlockNetworkImage(false); // 是否阻止网络图像
        wvSettings.setBlockNetworkLoads(false); // 是否阻止网络请求
        wvSettings.setJavaScriptEnabled(true); // 是否加载JS
        wvSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        wvSettings.setCacheMode(WebSettings.LOAD_NO_CACHE); //覆盖方式启动缓存
        wvSettings.setUseWideViewPort(true); // 使用广泛视窗
        wvSettings.setLoadWithOverviewMode(true);
        wvSettings.setRenderPriority(WebSettings.RenderPriority.NORMAL); // 渲染优先级
        wvSettings.setDomStorageEnabled(true);
        wvSettings.setBuiltInZoomControls(true);//是否支持缩放
        wvSettings.setSupportZoom(true);
        wvSettings.setDisplayZoomControls(false);//不显示缩放按钮
        wvSettings.setAllowFileAccess(true);
        wvSettings.setUserAgentString(wvSettings.getUserAgentString() + ";native-android");// 获得浏览器的环境
        wvSettings.setDatabaseEnabled(true);
        String storageDbPath = context.getCacheDir().getAbsolutePath();
        wvSettings.setDatabasePath(storageDbPath + "/storage_db.db");
        setVerticalScrollBarEnabled(false); // 取消Vertical ScrollBar显示
        setHorizontalScrollBarEnabled(false);// 取消Horizontal
        setWebViewClient(getWebViewClient(callBack));
        setWebChromeClient(getWebChromeClient(callBack));
        loadUrl(baseUrl);
    }

    private WebViewClient getWebViewClient(final WebViewCallBack callBack){
        return new WebViewClient() {

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
//                if(error.getPrimaryError() == android.net.http.SslError.SSL_INVALID ){// 校验过程遇到了bug
//                }else{
//                    handler.cancel();
//                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                HitTestResult hitTestResult = view.getHitTestResult();
                //hitTestResult==null解决重定向问题
                if (!TextUtils.isEmpty(url) && hitTestResult == null) {
                    view.loadUrl(url);
                    return true;
                }
                return callBack.shouldOverrideUrlLoading(view,url);
            };
            @Override
            public void onPageFinished(WebView view, String url) {
                callBack.onPageFinished(view, url);
                super.onPageFinished(view, url);

            };
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                callBack.onPageStarted(view, url, favicon);
                super.onPageStarted(view, url, favicon);
            };
            @Override
            public void onLoadResource(WebView view, String url) {
                callBack.onLoadResource(view, url);
                super.onLoadResource(view, url);
            };
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                callBack.onReceivedError(view, errorCode, description, failingUrl);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                WebResourceResponse ret = super.shouldInterceptRequest(view, url);
                if (ret == null && (url.contains("?") || url.contains("#") ||
                        needsIceCreamSpaceInAssetUrlFix(url))) {

                    if (null != (Activity) view.getTag(view.getId())) {

                        ret = generateWebResourceResponse((Activity) view.getTag(view.getId()), url);
                    }
                }
                return ret;
            }
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            private WebResourceResponse generateWebResourceResponse(Activity activity, String url) {
                WebResourceResponse response = null;
                if (url.startsWith("file:///android_asset/")) {
                    String mimetype = FileHelper.getMimeType(url, activity);
                    InputStream stream = null;
                    try {
                        stream = FileHelper.getInputStreamFromUriString(url, activity);
                        response = new WebResourceResponse(mimetype, "UTF-8", stream);
                        return response;
                    } catch (IOException e) {
                        Log.e("BaseWebView","BaseWebView IOException：");
                    } finally {
                        if (stream != null) {
                            try {
                                stream.close();
                            } catch (IOException e) {
                                Log.e("BaseWebView","BaseWebView IOException：");
                            }
                        }
                    }
                }
                return response;
            }
        };
    }

    public WebChromeClient getWebChromeClient(final WebViewCallBack callBack) {

        WebChromeClient mWebChromeClient = new WebChromeClient() {

            public void onProgressChanged(WebView view, int newProgress) {
                if(isProgressBar) {
                    if (newProgress == 100) {
                        progressbar.setVisibility(GONE);
                    } else {
                        if (progressbar.getVisibility() == GONE)
                            progressbar.setVisibility(VISIBLE);
                        progressbar.setProgress(newProgress);
                    }
                }
                super.onProgressChanged(view, newProgress);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public void onConsoleMessage(String message, int lineNumber, String sourceID) {
                // Log.v(TAG, "sourceID" + sourceID + "lineNumber" + lineNumber);
                super.onConsoleMessage(message, lineNumber, sourceID);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return super.onJsAlert(view, url, message, result);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                callBack.onReceivedTitle(view, title);
                super.onReceivedTitle(view, title);
            }
        };
        return mWebChromeClient;
    }

    public static boolean needsIceCreamSpaceInAssetUrlFix(String url) {
        if (!url.contains("%20")) {
            return false;
        }
        switch (Build.VERSION.SDK_INT) {
            case Build.VERSION_CODES.ICE_CREAM_SANDWICH:
            case Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        if(isProgressBar) {
            LayoutParams lp = (LayoutParams) progressbar.getLayoutParams();
            lp.x = l;
            lp.y = t;
            progressbar.setLayoutParams(lp);
        }
        super.onScrollChanged(l, t, oldl, oldt);
    }
}

