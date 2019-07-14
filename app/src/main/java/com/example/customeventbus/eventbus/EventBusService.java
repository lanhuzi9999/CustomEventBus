package com.example.customeventbus.eventbus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

import com.example.customeventbus.reflect.ReflectHelper;

/**
 * 事件总线服务，专门用于处理多进程事件分发，为说明事件分发流程，定义角色为:主进程事件总线 MainUI--EventBus，服务事件总线 Service--EventBus，
 * EventBusService 运行于主进程的服务
 * (1)主进程发事件：
 * Caller -->postEvent  ---->通过MainUI--EventBus 分发到当前进程的事件观察者 --> 触发事件观察者的 handleBusEvent调用
 * ---->调用EventBusService的handleBusEvent -->通过注册进来的服务进程的Messenger发送到Service-EventBus
 * (接续)的ClientHandler.handleMessage -->postEvent --> 通过Service-EventBus分发到服务进程的事件察者
 * -->(3)服务进程收到事件。
 * (2)服务进程发事件：
 * Caller -->postEvent  ---->通过Service-EventBus 分发到当前进程的事件观察者 --> 解发事件观察者的 handleBusEvent调用
 * ---->通过ServiceMessenger发到EventBusService的MyHandler.handleMessage -->postEvent
 * -->(4)EventBusService收事件
 * (3)服务进程收事件
 * ClientHandler-->handleMessage -->通过Service-EventBus分发到当前进程的事件观察者（要防止再发回到主进程）
 * (4)EventBusService收事件
 * EventBusService.MyHandler-->handleMessage -->通过MainUI--EventBus 分发到当前进程的事件观察者（要防止被再发回到服务进程）
 */
public class EventBusService extends Service implements ObserverCallback {
    /**
     * 注册
     */
    private static final int MSG_REGISTER = 1;
    //注销
    private static final int MSG_UNREGISTER = 2;
    //发事件
    private static final int MSG_POST = 3;
    private static final int TYPE_PARCEL = 0, TYPE_SERIAL = 1;
    private Handler mHandler;
    private Messenger mMessenger;
    private List<IBinder> mReplyMessengers;

    private static Message obtainMessage(Messenger messenger, int what, Object obj) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj = obj;
        msg.replyTo = messenger;
        return msg;
    }

    public static Message obtainRegisterMessage(Messenger messenger, Object obj) {
        return obtainMessage(messenger, MSG_REGISTER, obj);
    }

    public static Message obtainUnregisterMessage(Messenger messenger, Object obj) {
        return obtainMessage(messenger, MSG_UNREGISTER, obj);
    }

    public static Message obtainPostMessage(Messenger messenger, Object obj) {
        Message msg = obtainPostMessage(obj);
        msg.replyTo = messenger;
        return msg;
    }

    public static Message obtainPostMessage(Object obj) {
        Message msg = Message.obtain();
        msg.what = MSG_POST;
        Bundle bundle = msg.getData();
        if (bundle == null) {
            bundle = new Bundle();
            msg.setData(bundle);
        }
        if ((obj instanceof Parcelable || obj instanceof Serializable)) {
            try {
                bundle.putString("class", obj.getClass().getName());
                Parcel parcel = Parcel.obtain();
                if (obj instanceof Parcelable) {
                    bundle.putInt("type", TYPE_PARCEL);
                    parcel.writeParcelable((Parcelable) obj, 0);
                } else if (obj instanceof Serializable) {
                    bundle.putInt("type", TYPE_SERIAL);
                    parcel.writeSerializable((Serializable) obj);
                }
                // Message中不能传自定义的Parcelable数据，原因是接收端在处理Parcel时用到的是RootClassLoader，它无法还原自定义的Parcelable数据
                // 为解决这个问题，先把Parcelable数据处理成字节数组，接收端再把字节数组还原成Parcelable对象。
                parcel.setDataPosition(0);
                byte[] rawdata = parcel.marshall();
                bundle.putByteArray("bytes", rawdata);
                parcel.recycle();
            } catch (Exception e) {//防止出现其他意外，确保事件总线稳定
                return msg;
            }
        }
        return msg;
    }

    public static Object createParcelableObject(Message msg) {
        Bundle bundle = msg.getData();
        if (bundle != null) {
            try {
                String class_name = bundle.getString("class");
                byte[] bytes = bundle.getByteArray("bytes");
                Parcel parcel = Parcel.obtain();
                parcel.unmarshall(bytes, 0, bytes.length);
                parcel.setDataPosition(0);
                int type = bundle.getInt("type");
                Object obj = null;
                if (type == TYPE_PARCEL) {
                    Object creator = ReflectHelper.getStaticFieldValue(class_name, "CREATOR");
                    obj = ReflectHelper.callMethod(creator, "createFromParcel", new Class<?>[]{Parcel.class},
                            new Object[]{parcel});
                } else {
                    obj = parcel.readSerializable();
                }
                parcel.recycle();
                return obj;
            } catch (Exception e) {
                //防止出现意外，确保事件总线稳定
                return null;
            }
        } else {
            return null;
        }
    }

    public static boolean isPostMessage(Message msg) {
        return msg.what == MSG_POST;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //只接收Message事件，该事件由EventBus.post发出的，该事件指定在工作线程中处理
        EventBus.subscribeEvent(this, Message.class, EventThread.WORK_THREAD, this);
        mHandler = new MyHandler();
        mMessenger = new Messenger(mHandler);
        //记录子进程的ClientMessenger
        mReplyMessengers = new ArrayList<IBinder>();
        Intent intent = EventBus.createBindBusIntent(this);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //注销Message的事件观察者
        EventBus.unsubscribeEvent(this);
        mReplyMessengers.clear();
        Intent intent = EventBus.createUnBindBusIntent(this);
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        //返回本服务的Messenger
        return mMessenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR) {
            return Service.START_STICKY_COMPATIBILITY;
        } else {
            return Service.START_REDELIVER_INTENT;
        }
    }

    @Override
    public void handleBusEvent(Object observer, Object event) {
        //MainThread EventBus --> Service EventBus
        //收到的事件分发到子进程的EventBus
        handleBusEventFrom(observer, event, null);
    }

    void handleBusEventFrom(Object observer, Object event, Messenger from) {
        if (mReplyMessengers.size() == 0 || !(event instanceof Parcelable || event instanceof Serializable)) {
            return;
        }
        List<IBinder> cloneMessengers = new CopyOnWriteArrayList<IBinder>(mReplyMessengers);
        Message msg = obtainPostMessage(event);
        Messenger msgsender;
        for (IBinder binder : cloneMessengers) {
            try {
                msgsender = new Messenger(binder);
                // != 防止再次把消发还给发送者，避免出现事件循环
                if (null == from || binder != from.getBinder()) {
                    msgsender.send(msg);
                }
            } catch (RemoteException e) {
                //子进程的Messenger可能挂了，需要将它移除
                e.printStackTrace();
                mReplyMessengers.remove(binder);
            }
        }

    }


    private void register(Messenger replyMessenger) {
        if (!mReplyMessengers.contains(replyMessenger.getBinder())) {
            mReplyMessengers.add(replyMessenger.getBinder());
        }
    }

    private void unregister(Messenger replyMessenger) {
        mReplyMessengers.remove(replyMessenger.getBinder());
    }

    class MyHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER:
                    if (msg.replyTo != null) {
                        register(msg.replyTo);
                    }
                    break;
                case MSG_UNREGISTER:
                    if (msg.replyTo != null) {
                        unregister(msg.replyTo);
                    }
                    break;
                case MSG_POST:
                    //从其他进程收到事件  Service --> MainThread EventBus
                    //收到其他子进程的事件，需要分发到主进程及除该子进程之外的其他进程
                    Object obj = createParcelableObject(msg);
                    if (obj != null) {
                        EventBus.postEvent(obj, msg.replyTo);
                    }
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }

    }

}
