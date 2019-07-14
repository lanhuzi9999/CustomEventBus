package com.example.customeventbus.eventbus;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;

import com.example.customeventbus.util.ThreadUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;


public final class EventBus {
    //重新绑定事件总线服务
    final private static String ACTION_BIND_EventBusService = "action.bind.eventbusservice";
    //解绑
    final private static String ACTION_UNBIND_EventBusService = "action.unbind.eventbusservice";
    final private static EventBus gInstance = new EventBus();
    final private ConcurrentMap<Object, Set<EventType>> mEventTypesByObserver = new ConcurrentHashMap<Object, Set<EventType>>();
    private Messenger mServiceMessenger;
    private Messenger mClientMesseger;
    private MyConnection mConnection;
    private Context mAppContext;
    //用来保证EventBusService异常退出后再次启动时，要重新连接到EventBusService.
    private BroadcastReceiver mBindReceiver;
    private boolean mNeedRebind;
    //是否可以接收广播
    private boolean mCanReceiveBroadcast;
    /**
     * 记录粘性广播事件
     */
    private Map<Class<?>, Object> mStickyEvents;

    static final class ObserverEvent {
        Object observer;
        ObserverCallback callback;

        ObserverEvent(Object o, ObserverCallback func) {
            observer = o;
            callback = func;
        }
    }

    public static Intent createBindBusIntent(Context context) {
        Intent intent = new Intent(ACTION_BIND_EventBusService);
        intent.setPackage(context.getPackageName());
        return intent;
    }

    public static Intent createUnBindBusIntent(Context context) {
        Intent intent = new Intent(ACTION_UNBIND_EventBusService);
        intent.setPackage(context.getPackageName());
        return intent;
    }

    private EventBus() {
        mAppContext = null;
        mServiceMessenger = null;
        mClientMesseger = null;
        mConnection = null;
        mBindReceiver = null;
        mNeedRebind = false; //不需要重绑定
        mCanReceiveBroadcast = false;
    }

    private synchronized void addStickEvent(Object event) {
        if (event == null) {
            return;
        }
        if (mStickyEvents == null) {
            mStickyEvents = new Hashtable<Class<?>, Object>();
        }
        Class<?> clazz = event.getClass();
        mStickyEvents.remove(clazz);
        mStickyEvents.put(clazz, event);
    }

    private synchronized void removeStickEvent(Object event) {
        if (event == null || mStickyEvents == null) {
            return;
        }
        Class<?> clazz = event.getClass();
        mStickyEvents.remove(clazz);
    }

    private synchronized Object getStickEvent(Class<?> eventClass) {
        if (eventClass == null || mStickyEvents == null) {
            return null;
        }
        return mStickyEvents.get(eventClass);
    }

    /**
     * 子进程使用时，用它来绑定到主进程
     *
     * @param context
     */
    public static void bindMainEventBus(Context context) {
        gInstance.bind(context);
        gInstance.mNeedRebind = true;
        Context appcontext = gInstance.mAppContext;
        BroadcastReceiver receiver = gInstance.mBindReceiver;
        if (appcontext != null && receiver == null) {
            //没有注册过
            gInstance.mBindReceiver = gInstance.new BindReceiver();
            receiver = gInstance.mBindReceiver;
            IntentFilter filter = new IntentFilter(ACTION_BIND_EventBusService);
            filter.addAction(ACTION_UNBIND_EventBusService);
            appcontext.registerReceiver(receiver, filter);
        }
    }

    /**
     * 解绑
     *
     * @param context
     */
    public static void unbindMainEventBus(Context context) {
        gInstance.mNeedRebind = false;
        gInstance.unbind(context);
        Context appcontext = gInstance.mAppContext;
        if (appcontext == null) {
            appcontext = context.getApplicationContext();
        }
        BroadcastReceiver receiver = gInstance.mBindReceiver;
        if (receiver != null) {
            gInstance.mBindReceiver = null;
            try {
                appcontext.unregisterReceiver(receiver);
            } catch (Exception e) {

            }
        }
    }

    /**
     * 往事件总线发一事件
     *
     * @param event
     */
    public static void postEvent(Object event) {
        postEvent(event, (ObserverCallback) null);
    }

    static void postEvent(Object event, Messenger from) {
        gInstance.post(event, null, from);
    }

    /**
     * 往事件总线发一事件，并指定接收者收到后的处理事件的回调方法
     *
     * @param event
     * @param callback
     */
    public static void postEvent(Object event, ObserverCallback callback) {
        gInstance.post(event, callback, null);
    }

    public static void postStickyEvent(Object event, ObserverCallback callback) {
        gInstance.addStickEvent(event);
        postEvent(event, callback);
    }

    /**
     * 注册事件观察者
     *
     * @param observer
     * @param eventtype
     */
    public static void subscribeEvent(Object observer, EventType eventtype) {
        gInstance.subscribe(observer, eventtype);
    }

    public static void subscribeEvent(Object observer, Class<?> eventclass, EventThread eventthread, ObserverCallback callback) {
        EventType et = new EventType(eventclass, callback, eventthread);
        subscribeEvent(observer, et);
    }

    /**
     * 注销事件观察者
     *
     * @param observer
     */
    public static void unsubscribeEvent(Object observer) {
        gInstance.unsubscribe(observer);
    }

    public static void unsubscriberEvent(Object... observers) {
        if (observers != null) {
            for (Object o : observers) {
                unsubscribeEvent(o);
            }
        }
    }

    public static void unsubscriberAllEvent() {
        EventBus eventBus = gInstance;
        synchronized (eventBus) {
            eventBus.mEventTypesByObserver.clear();
            if (eventBus.mStickyEvents != null) {
                eventBus.mStickyEvents.clear();
            }
        }
    }


    private synchronized void subscribe(final Object observer, EventType eventtype) {
        if (eventtype == null) {
            return;
        }
        Set<EventType> eventset = mEventTypesByObserver.get(observer);
        if (eventset == null) {
            eventset = new CopyOnWriteArraySet<EventType>();
            mEventTypesByObserver.put(observer, eventset);
        }
        eventset.add(eventtype);
        Class<?> eventClass = eventtype.getEventClass();
        final Object stickEvent = getStickEvent(eventClass);
        if (stickEvent != null) {
            //表示已有一条粘性事件，立刻处理它
            EventThread eventthread = eventtype.getEventThread();
            final ObserverCallback ocallback = eventtype.getCallback();
            Runnable action = new Runnable() {
                @Override
                public void run() {
                    ocallback.handleBusEvent(observer, stickEvent);
                }
            };
            if (eventthread == EventThread.MAIN_THREAD) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(action);
            } else {
                ThreadUtil.queueWork(action);
            }
            removeStickEvent(stickEvent);
        }
    }

    private synchronized void unsubscribe(Object observer) {
        mEventTypesByObserver.remove(observer);
    }

    private synchronized void post(final Object event, final ObserverCallback callback, final Messenger from) {
        if (event == null) {
            return;
        }
        if (mServiceMessenger != null && from != null && mClientMesseger.getBinder() == from.getBinder()) {
            //从EventBusService发回来的，不能再处理，否则发送端会收到两次
            return;
        }
        Set<Entry<Object, Set<EventType>>> set = mEventTypesByObserver.entrySet();
        if (set == null || set.size() == 0) {
            return;
        }
        Object observer;
        Set<EventType> eventset;
        EventThread eventthread;
        ObserverCallback ocallback;
        List<ObserverEvent> mainthread_events = null;
        List<ObserverEvent> workthread_events = null;
        List<ObserverEvent> eventslist = null;
        ObserverEvent oe;
        Class<?> eventclass = null;
        EventBusService service = null;
        for (Entry<Object, Set<EventType>> entry : set) {
            observer = entry.getKey();
            if (observer instanceof EventBusService) {
                service = (EventBusService) observer;
            }
            eventset = entry.getValue();
            for (EventType et : eventset) {
                eventclass = et.getEventClass();
                if (eventclass != null && eventclass.isInstance(event)) {
                    eventthread = et.getEventThread();
                    ocallback = et.getCallback();
                    oe = new ObserverEvent(observer, ocallback);
                    if (eventthread == EventThread.MAIN_THREAD) {
                        if (mainthread_events == null) {
                            mainthread_events = new ArrayList<ObserverEvent>();
                        }
                        eventslist = mainthread_events;
                    } else {
                        if (workthread_events == null) {
                            workthread_events = new ArrayList<ObserverEvent>();
                        }
                        eventslist = workthread_events;
                    }
                    eventslist.add(oe);
                }
            }
        }
        Runnable action = null;
        if (workthread_events != null) {
            final List<ObserverEvent> _workthread_events = workthread_events;
            action = new Runnable() {
                @Override
                public void run() {
                    for (ObserverEvent e : _workthread_events) {
                        if (e.callback != null) {
                            e.callback.handleBusEvent(e.observer, event);
                        }
                        if (callback != null) {
                            callback.handleBusEvent(e.observer, event);
                        }
                    }
                }
            };
            ThreadUtil.queueWork(action);
            removeStickEvent(event);
        }
        if (mainthread_events != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            for (final ObserverEvent e : mainthread_events) {
                if (e.callback == null && callback == null) {
                    continue;
                }
                action = new Runnable() {
                    @Override
                    public void run() {
                        if (e.callback != null) {
                            e.callback.handleBusEvent(e.observer, event);
                        }
                        if (callback != null) {
                            callback.handleBusEvent(e.observer, event);
                        }
                    }
                };
                handler.post(action);
            }
            removeStickEvent(event);
        }

        if (!(event instanceof Message) && (event instanceof Parcelable || event instanceof Serializable)) {
            if (service == null) {
                if (mServiceMessenger != null && (from == null || mClientMesseger.getBinder() != from.getBinder())) {// 表明这是在工作线程的EventBus，需要将它的事件发到主进程
                    try {
                        Message msg = EventBusService.obtainPostMessage(mClientMesseger, event);
                        mServiceMessenger.send(msg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                        bindAgain(); // 服务可能断了，需要重新绑定
                    }
                }
            } else if (service != null) { //当前为主进程，并且被其他子进程绑定了服务，因此需要将事件分发到其他子进程。
                service.handleBusEventFrom(service, event, from);
            }
        }

    }

    private void bindAgain() {
        ServiceConnection connection = mConnection;
        if (connection != null) {
            final Context context = mAppContext;
            Handler handler = new Handler(context.getMainLooper());
            Runnable action = new Runnable() {
                @Override
                public void run() {
                    mServiceMessenger = null;
                    mClientMesseger = null;
                    mConnection = null;
                    bind(context);
                }
            };
            unbind(context);
            handler.postDelayed(action, 2000);
        }
    }

    private void bind(Context context) {
        if (mServiceMessenger != null) {
            return;
        }
        mAppContext = context.getApplicationContext();
        if (mConnection == null) {
            mConnection = new MyConnection();
        }
        mCanReceiveBroadcast = false;
        ServiceConnection connection = mConnection;
        Intent intent = new Intent(mAppContext, EventBusService.class);
        mAppContext.bindService(intent, connection, Service.BIND_AUTO_CREATE);
    }

    private void unbind(Context context) {
        if (mAppContext == null) {
            mAppContext = context.getApplicationContext();
        }
        if (mServiceMessenger != null) {
            Message msg = EventBusService.obtainUnregisterMessage(mClientMesseger, null);
            try {
                mServiceMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        mCanReceiveBroadcast = true;
        ServiceConnection connection = mConnection;
        if (connection != null) {
            try {
                mConnection = null;
                mAppContext.unbindService(connection);
            } catch (Exception e) {

            }
        }
    }

    class MyConnection implements ServiceConnection {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceMessenger = null;
            mClientMesseger = null;
            if (mNeedRebind) {
                mCanReceiveBroadcast = true;
                bindAgain();
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceMessenger = new Messenger(service);
            mClientMesseger = new Messenger(new ClientHandler());
            Message msg = EventBusService.obtainRegisterMessage(mClientMesseger, null);
            mCanReceiveBroadcast = true;
            try {
                mServiceMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    class ClientHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            if (EventBusService.isPostMessage(msg)) {
                //收到主进程或其他子进程的事件，需要将事件放到总线上，并且不能再分到到主进程及其他子进程
                Object obj = EventBusService.createParcelableObject(msg);
                if (obj != null) {
                    EventBus.postEvent(obj, mClientMesseger);
                }
            } else {
                super.handleMessage(msg);
            }
        }
    }

    class BindReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (ACTION_BIND_EventBusService.equals(action)) {
                if (mCanReceiveBroadcast) {
                    bindAgain();
                }
            } else if (ACTION_UNBIND_EventBusService.equals(action)) {
                unbind(context);
            }
        }

    }
}
