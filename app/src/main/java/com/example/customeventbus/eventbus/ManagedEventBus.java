package com.example.customeventbus.eventbus;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.os.Bundle;

import com.example.customeventbus.util.CustomLog;
import com.example.customeventbus.util.Utils;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ManagedEventBus implements ActivityLifecycleCallbacks{
    final String TAG = getClass().getSimpleName();
    Set<Object>	mObservers ;
    Activity	mActivity;
    public ManagedEventBus(Activity activity){
        mActivity = Utils.getRootActivity(activity) ;
        mObservers = new CopyOnWriteArraySet<Object>();
        Application app = mActivity.getApplication();
        app.registerActivityLifecycleCallbacks(this);
    }

    /**
     * 往事件总线发一事件
     * @param event
     */
    public static void postEvent(Object event){
        postEvent(event, null);
    }

    /**
     * 往事件总线发一事件，并指定接收者收到后的处理事件的回调方法
     * @param event
     * @param callback
     */
    public static void postEvent(Object event, ObserverCallback callback){
        EventBus.postEvent(event, callback);
    }

    /**
     * 发一条粘性事件，用于接收者还没订阅事件，一旦订阅立刻可处理本条事件
     * @param stickEvent
     */
    public static void postStickyEvent(Object stickEvent ){
        EventBus.postStickyEvent(stickEvent, null);
    }

    /**
     * 注册事件观察者
     * @param observer
     * @param eventtype
     */
    public void subscribeEvent(Object observer, EventType eventtype){
        if (mActivity == null){
            return ;
        }
        EventBus.subscribeEvent(observer, eventtype);
        synchronized(this){
            mObservers.add(observer);
        }
    }

    public void subscribeEvent(Object observer, Class<?> eventclass, EventThread eventthread, ObserverCallback callback){
        EventType et = new EventType(eventclass, callback, eventthread);
        subscribeEvent(observer, et);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (mActivity == activity){
            synchronized(this){
                if (mObservers.size() > 0){
                    if (CustomLog.isPrintLog){
                        CustomLog.i(TAG, "unsubscriberEvent observers size="+mObservers.size());
                    }
                    Object[] observers = new Object[mObservers.size()];
                    mObservers.toArray(observers);
                    EventBus.unsubscriberEvent(observers);
                    mObservers.clear();
                }
            }
            Application app = mActivity.getApplication();
            app.unregisterActivityLifecycleCallbacks(this);
            mActivity = null ;
        }
    }
}
