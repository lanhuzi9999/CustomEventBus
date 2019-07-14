package com.example.customeventbus.util;

import android.util.Log;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: robin
 * @description:
 * @date: 2015/7/01
 **/
public class CustomThreadFactory implements ThreadFactory {
    private static final String TAG = "AspThreadFactory";
    private final AtomicInteger mPoolSize = new AtomicInteger(1);
    private final String mName;

    public CustomThreadFactory(String name) {
        mName = name;
    }

    @Override
    public Thread newThread(Runnable r) {
        int size = mPoolSize.getAndIncrement();
        Log.d(TAG, "newThread--" + mName + ":" + size);
        return new Thread(r, mName + ":" + size);
    }
}
