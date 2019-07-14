package com.example.customeventbus.util;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;

/**
 * @author: robin
 * @description:
 * @date: 2015/7/01
 **/
public class Utils {
    /**
     * 是否ui线程
     * @param context
     * @return
     */
    public static boolean isUIThread(Context context){
        Thread curthread = Thread.currentThread() ;
        Looper curloop = context.getMainLooper() ;
        Thread loopthread = curloop.getThread() ;
        return curthread.getId() == loopthread.getId();
    }

    public static Activity getRootActivity(Activity activity){
        Activity root = activity ;
        while(root != null && root.getParent() != null){
            root = root.getParent();
        }
        return root ;
    }
}
