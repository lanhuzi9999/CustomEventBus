package com.example.customeventbus.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class ThreadUtil {
    private static final String TAG = "ThreadUtil";
    private static final int THREADPOOL_QUEUE_SIZE = 30;
    private final static Object mLockQueue = new Object();// ,mLock=new
    // Object();
    private static ExecutorService gExecutorServiceQueue, gExecutorService;

    private synchronized static void ensureExecutorServiceQueue() {
        if (gExecutorServiceQueue == null) {
            synchronized (mLockQueue) {
                if (gExecutorServiceQueue == null) {
                    gExecutorServiceQueue = new ThreadPoolExecutor(THREADPOOL_QUEUE_SIZE / 10 + 1,
                            THREADPOOL_QUEUE_SIZE, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                            new CustomThreadFactory("customutil"), new ThreadPoolExecutor.DiscardPolicy());
                }
            }
        }
    }

    public static Executor getExecutorServiceQueue() {
        ensureExecutorServiceQueue();
        return gExecutorServiceQueue;
    }

    /**
     * 在线程池运行线程。若达到线程池最大限制(10)则排队等待执行
     *
     * @param task
     */
    public static void queueWork(Runnable task) {
        queueWork(task, false);
    }

    /**
     * 若指定的任务需要立刻执行时，用这个方法
     *
     * @param task
     * @param immediately :ture表示希望task任务立刻执行，不能排队
     */
    public static void queueWork(Runnable task, boolean immediately) {
        ensureExecutorServiceQueue();
        ThreadPoolExecutor executor = (ThreadPoolExecutor) gExecutorServiceQueue;
        BlockingQueue<Runnable> queue = executor.getQueue();
        if (!immediately || queue == null || queue.size() < executor.getCorePoolSize()) {
            executor.execute(task);
        } else {
            // Thread thread = new Thread(task);
            // thread.start();
            runWork(task);
        }
        Log.d(TAG, "queueWork task=" + task + ",immdeiately=" + immediately);
    }

    /**
     * 立刻在线程池运行线程
     *
     * @param task
     */
    private static void runWork(Runnable task) {
        if (gExecutorService == null) {
            synchronized (mLockQueue) {
                if (gExecutorService == null) {
                    gExecutorService = Executors.newCachedThreadPool(new CustomThreadFactory("customutil"));
                }
            }
        }
        gExecutorService.submit(task);
        Log.d(TAG, "runWork");
    }

    public static boolean isUIThread(Context context) {
        return Utils.isUIThread(context);
    }

    /**
     * 指定在UI线程执行任务
     *
     * @param context
     * @param action
     */
    public static void runOnUIThread(Context context, Runnable action) {
        runOnUIThread(context, action, 0);
    }

    /**
     * 指定在UI线程延时执行任务
     *
     * @param context
     * @param action
     * @param delay
     */
    public static void runOnUIThread(Context context, Runnable action, long delay) {
        if (delay <= 0) {
            if (Utils.isUIThread(context)) {
                action.run();
            } else {
                Handler handler = new Handler(context.getMainLooper());
                handler.post(action);
            }
        } else {
            Handler handler = new Handler(context.getMainLooper());
            handler.postDelayed(action, delay);
        }
    }

    /**
     * 在Activity恢复resume状态时执行action，只执行一次
     *
     * @param activity
     * @param action
     */
    public static void runOnActivityResume(Activity activity, Runnable action) {
        runOnActivityState(activity, ActivityState.ACTIVITY_RESUME, action);
    }


    /**
     * 在指定的Activity状态时执行action，只执行一次
     *
     * @param activity
     * @param state
     * @param action
     */
    public static Application.ActivityLifecycleCallbacks runOnActivityState(Activity activity, ActivityState state, Runnable action) {
        if (activity.isFinishing()) {
            if (state == ActivityState.ACTIVITY_DESTROY ||
                    state == ActivityState.ACTIVITY_PAUSE ||
                    state == ActivityState.ACTIVITY_STOPPED) {
                action.run();
            }
            return null;
        } else {
            return new ActivityStateCallbacks(activity, state, action);
        }
    }

    /**
     * 在指定的Activity状态时执行action，只执行一次
     *
     * @param activityClass
     * @param state
     * @param action
     */
    public static Application.ActivityLifecycleCallbacks runOnActivityState(Context context, Class<? extends Activity> activityClass, ActivityState state,
                                                                            Runnable action) {
        return new ActivityStateCallbacks(context, activityClass, state, action);
    }

    /**
     * 注册在指定状态下执行任务，不止一次，直至Activity销毁
     *
     * @param activity
     * @param state
     * @param action
     */
    public static Application.ActivityLifecycleCallbacks registerOnActivityState(Activity activity, ActivityState state, Runnable action) {
        if (activity.isFinishing()) {
            if (state == ActivityState.ACTIVITY_DESTROY) {
                action.run();
            }
            return null;
        } else {
            return new ActivityStateCallbacks(activity, state, action, false);
        }
    }

    /**
     * 注册在指定状态下执行任务，不止一次，直至Activity销毁
     *
     * @param activityClass
     * @param state
     * @param action
     */
    public static Application.ActivityLifecycleCallbacks registerOnActivityState(Context context, Class<? extends Activity> activityClass, ActivityState state,
                                                                                 Runnable action) {
        return new ActivityStateCallbacks(context, activityClass, state, action, false);
    }

    public static void unregisterOnActivityState(Context context, Application.ActivityLifecycleCallbacks callback) {
        if (callback == null) {
            return;
        }
        Application application = (Application) context.getApplicationContext();
        try {
            application.unregisterActivityLifecycleCallbacks(callback);
        } catch (Exception ignr) {
        }
    }

    public static void releaseThreadPool() {
        if (gExecutorService != null && !gExecutorService.isShutdown()) {
            gExecutorService.shutdownNow();
        }
        gExecutorService = null;

        if (gExecutorServiceQueue != null && !gExecutorServiceQueue.isShutdown()) {
            gExecutorServiceQueue.shutdownNow();
        }
        gExecutorServiceQueue = null;
    }

    private static class ActivityStateCallbacks extends AbsActivityLifecycleCallbacks {
        private Application mApplication;
        private Activity mTargetActivity;
        private Class<? extends Activity> mTargetActivityClass;
        private Runnable mAction;
        private ActivityState mActivityState;
        //标记任务是否只执行一次，默认为执行一次
        private boolean mIsRunOnce;

        private ActivityStateCallbacks(Activity activity, ActivityState state, Runnable action) {
            this(activity, state, action, true);
        }

        private ActivityStateCallbacks(Activity activity, ActivityState state, Runnable action, boolean runOnce) {
            mTargetActivity = activity;
            mTargetActivityClass = null;
            mAction = action;
            mActivityState = state;
            mIsRunOnce = runOnce;
            mApplication = mTargetActivity.getApplication();
            mApplication.registerActivityLifecycleCallbacks(this);
        }

        private ActivityStateCallbacks(Context context, Class<? extends Activity> activityClass,
                                       ActivityState state, Runnable action) {
            this(context, activityClass, state, action, true);
        }

        private ActivityStateCallbacks(Context context, Class<? extends Activity> activityClass,
                                       ActivityState state, Runnable action, boolean runOnce) {
            mTargetActivity = null;
            mTargetActivityClass = activityClass;
            mAction = action;
            mActivityState = state;
            mIsRunOnce = runOnce;
            mApplication = (Application) context.getApplicationContext();
            mApplication.registerActivityLifecycleCallbacks(this);
        }

        private void postRun() {
            Handler handler = new Handler(mApplication.getMainLooper());
            handler.post(mAction);
        }

        @Override
        public void onActivityResumed(Activity activity) {
            super.onActivityResumed(activity);
            if ((activity == mTargetActivity ||
                    (mTargetActivityClass != null && mTargetActivityClass.isInstance(activity))) &&
                    mActivityState == ActivityState.ACTIVITY_RESUME) {
                postRun();
                if (mIsRunOnce) {
                    mApplication.unregisterActivityLifecycleCallbacks(this);
                }
            }
        }

        @Override
        public void onActivityStarted(Activity activity) {
            super.onActivityStarted(activity);
            if ((activity == mTargetActivity ||
                    (mTargetActivityClass != null && mTargetActivityClass.isInstance(activity))) &&
                    mActivityState == ActivityState.ACTIVITY_STARTED) {
                postRun();
                if (mIsRunOnce) {
                    mApplication.unregisterActivityLifecycleCallbacks(this);
                }
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
            super.onActivityPaused(activity);
            if ((activity == mTargetActivity ||
                    (mTargetActivityClass != null && mTargetActivityClass.isInstance(activity)))
                    && mActivityState == ActivityState.ACTIVITY_PAUSE) {
                postRun();
                if (mIsRunOnce) {
                    mApplication.unregisterActivityLifecycleCallbacks(this);
                }
            }
        }

        @Override
        public void onActivityStopped(Activity activity) {
            super.onActivityStopped(activity);
            if ((activity == mTargetActivity ||
                    (mTargetActivityClass != null && mTargetActivityClass.isInstance(activity))) &&
                    mActivityState == ActivityState.ACTIVITY_STOPPED) {
                mAction.run();
                if (mIsRunOnce) {
                    mApplication.unregisterActivityLifecycleCallbacks(this);
                }
            }
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            super.onActivityDestroyed(activity);
            if (mTargetActivity == activity ||
                    (mTargetActivityClass != null && mTargetActivityClass.isInstance(activity))) {
                if (mActivityState == ActivityState.ACTIVITY_DESTROY) {
                    mAction.run();
                }
                if (mIsRunOnce || mTargetActivity == activity) {
                    mApplication.unregisterActivityLifecycleCallbacks(this);
                }
            }
        }
    }
}
