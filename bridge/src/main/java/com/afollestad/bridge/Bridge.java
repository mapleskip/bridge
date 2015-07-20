package com.afollestad.bridge;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Bridge {

    private static Bridge mBridge;
    private static Config mConfig;

    protected static final Object LOCK = new Object();

    protected Handler mHandler;
    private Map<String, CallbackStack> mRequestMap;

    protected boolean pushCallback(Request request, Callback callback) {
        synchronized (LOCK) {
            if (mRequestMap == null)
                mRequestMap = new HashMap<>();
            final String key = CallbackStack.createKey(request);
            CallbackStack cbs = mRequestMap.get(key);
            if (cbs != null) {
                Log.d(this, "Pushing callback to EXISTING stack for %s", key);
                cbs.push(callback, request);
                return false;
            } else {
                Log.d(this, "Pushing callback to NEW stack for %s", key);
                cbs = new CallbackStack();
                cbs.push(callback, request);
                mRequestMap.put(key, cbs);
                return true;
            }
        }
    }

    protected void fireProgress(Request request, int current, int total) {
        synchronized (LOCK) {
            if (mRequestMap == null) return;
            final String key = CallbackStack.createKey(request);
            final CallbackStack cbs = mRequestMap.get(key);
            if (cbs != null)
                cbs.fireAllProgress(request, current, total);
        }
    }

    protected void fireCallbacks(Request request, Response response, RequestException error) {
        synchronized (LOCK) {
            final String key = CallbackStack.createKey(request);
            Log.d(this, "Attempting to fire callbacks for %s", key);
            if (mRequestMap == null) {
                Log.d(this, "Request map is null, can't fire callbacks.");
                return;
            }
            final CallbackStack cbs = mRequestMap.get(key);
            if (cbs != null) {
                Log.d(this, "Firing %d callback(s) for %s", cbs.size(), key);
                cbs.fireAll(request, response, error);
                mRequestMap.remove(key);
                if (mRequestMap.size() == 0)
                    mRequestMap = null;
            } else {
                Log.d(this, "No callback stack found for %s", key);
            }
        }
    }

    private Bridge() {
        mConfig = new Config();
    }

    @NonNull
    public static Bridge client() {
        if (mBridge == null)
            mBridge = new Bridge();
        return mBridge;
    }

    @NonNull
    public static Config config() {
        if (mConfig == null)
            mConfig = new Config();
        return mConfig;
    }

    private String processUrl(String url, @Nullable Object... formatArgs) {
        if (formatArgs != null) {
            for (int i = 0; i < formatArgs.length; i++) {
                if (formatArgs[i] instanceof String) {
                    try {
                        formatArgs[i] = URLEncoder.encode((String) formatArgs[i], "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        // this should never happen
                        throw new RuntimeException(e);
                    }
                }
            }
            return String.format(url, formatArgs);
        } else return url;
    }

    public RequestBuilder get(@NonNull String url, @Nullable Object... formatArgs) {
        return new RequestBuilder(processUrl(url, formatArgs), Method.GET, this);
    }

    public RequestBuilder post(@NonNull String url, @Nullable Object... formatArgs) {
        return new RequestBuilder(processUrl(url, formatArgs), Method.POST, this);
    }

    public RequestBuilder put(@NonNull String url, @Nullable Object... formatArgs) {
        return new RequestBuilder(processUrl(url, formatArgs), Method.PUT, this);
    }

    public RequestBuilder delete(@NonNull String url, @Nullable Object... formatArgs) {
        return new RequestBuilder(processUrl(url, formatArgs), Method.DELETE, this);
    }

    public void cancelAll(@NonNull final Method method, @NonNull final String url) {
        if (mRequestMap == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    for (String key : mRequestMap.keySet()) {
                        if (!key.startsWith(method.name() + ":" + url + ":")) continue;
                        mRequestMap.get(key).cancelAll();
                        mRequestMap.remove(key);
                    }
                    mRequestMap = null;
                }
            }
        }).start();
    }

    public void cancelAll() {
        if (mRequestMap == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    for (String key : mRequestMap.keySet()) {
                        mRequestMap.get(key).cancelAll();
                        mRequestMap.remove(key);
                    }
                    mRequestMap = null;
                }
            }
        }).start();
    }

    public void destroy() {
        mHandler = null;
        mConfig.destroy();
        cancelAll();
        Log.d(this, "Bridge singleton was destroyed.");
    }

    public static void cleanup() {
        if (mBridge != null) {
            mBridge.destroy();
            mBridge = null;
        }
    }
}