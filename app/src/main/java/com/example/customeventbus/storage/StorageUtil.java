package com.example.customeventbus.storage;


import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import com.example.customeventbus.reflect.ReflectHelper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * @author: robin
 * @description:sdcard存储工具类
 * @date: 2019/7/1
 **/
public class StorageUtil {
    public final static String TAG = StorageUtil.class.getSimpleName();
    private static StorageUtil mInstance = null;

    private String mWorkingDirectory;
    private HashMap<String, File> mDirectorys;

    public static final String DIR_ANDROID = "Android";
    public static final String DIR_DATA = "data";

    public static final String TYPE_DOWNLOAD = "download";
    public static final String TYPE_CACHE = "cache";
    public static final String TYPE_LOG = "log";

    public static final String DEFAULT_LOG_DIR = "/dev/null";

    private static String PACKAGENAME = "custom.download";
    private static String DIR_APP = "custom";

    public static void init(Context context, String app_name) {
        PACKAGENAME = context.getPackageName();
        if (TextUtils.isEmpty(app_name)) {
            app_name = PACKAGENAME;
        }
        int index = app_name.lastIndexOf('.');
        if (index >= 0) {
            app_name = app_name.substring(index + 1);
        }
        String oldAPP = DIR_APP;
        DIR_APP = app_name;
        if (mInstance != null && !TextUtils.equals(app_name, oldAPP)) {
            mInstance.initWorkingDirectory();
        }
    }

    protected StorageUtil() {
        mDirectorys = new HashMap<String, File>();
        initWorkingDirectory();
    }

    public static StorageUtil getInstance() {
        synchronized (TAG) {
            if (mInstance == null) {
                mInstance = new StorageUtil();
            }
            return mInstance;
        }
    }

    private void initWorkingDirectory() {
        File extStorageDirectory = Environment.getExternalStorageDirectory();
        boolean extStorageAvailable = false;
        if (extStorageDirectory != null && extStorageDirectory.exists()) {
            if (Environment.MEDIA_MOUNTED.equals(Environment
                    .getExternalStorageState())) {
                extStorageAvailable = true;
            }
        }
        if (extStorageAvailable) {
            boolean isRemovable = true;
            if (Build.VERSION.SDK_INT >= 10) {
                Object obj = ReflectHelper.callStaticMethod(Environment.class,
                        "isExternalStorageRemovable", null, null);
                if (obj != null && obj instanceof Boolean) {
                    isRemovable = ((Boolean) obj).booleanValue();
                }
            }

            if (isRemovable) {
                mWorkingDirectory = extStorageDirectory.getAbsolutePath()
                        + File.separator + DIR_ANDROID + File.separator
                        + DIR_DATA + File.separator + PACKAGENAME;
                ensureDirExists(mWorkingDirectory);
                File file = new File(mWorkingDirectory);
                if (!file.exists()) {
                    mWorkingDirectory = extStorageDirectory.getAbsolutePath()
                            + File.separator + DIR_APP;
                }

            } else {
                mWorkingDirectory = extStorageDirectory.getAbsolutePath()
                        + File.separator + DIR_APP;
            }
            ensureDirExists(mWorkingDirectory);
            ensureNomediaExists(mWorkingDirectory);
            File file = new File(mWorkingDirectory);
            if (!file.exists()) {
                mWorkingDirectory = getDataDir();
            }

        } else {
            mWorkingDirectory = getDataDir();
        }

    }

    private String getDataDir() {
        String dataDir = "/data/data/" + PACKAGENAME;
        boolean dataDirGot = false;
        if (!dataDirGot) {
            File file = Environment.getDataDirectory();
            if (file != null && file.exists()) {
                String dir = file.getAbsolutePath();
                dataDir = dir + File.separator + DIR_DATA + File.separator
                        + PACKAGENAME;
                dataDirGot = true;
            }
        }

        return dataDir;
    }

    private File buildWorkingDirectory(String type) {
        File file = null;
        if (!TextUtils.isEmpty(type)) {
            File match = mDirectorys.get(type);
            if (match != null) {
                file = match;
            } else {
                String dir = null;
                dir = mWorkingDirectory + File.separator + type;
                ensureDirExists(dir);
                file = new File(dir);
                if (file.exists()) {
                    mDirectorys.put(type, file);
                }
            }
        }
        return file;
    }

    public String getWorkingDirectory(String type) {
        String dir = null;
        File file = buildWorkingDirectory(type);
        if (file != null) {
            dir = file.getAbsolutePath();
        }
        return dir;
    }

    public String getWorkingDirectory() {
        return mWorkingDirectory;
    }

    public String getDownloadDirectory() {
        return getWorkingDirectory(TYPE_DOWNLOAD);
    }

    public String getCacheDirectory() {
        return getWorkingDirectory(TYPE_CACHE);
    }

    public String getLogDirectory() {
        File extStorageDirectory = Environment.getExternalStorageDirectory();
        boolean extStorageAvailable = false;
        if (extStorageDirectory != null && extStorageDirectory.exists()) {
            if (Environment.MEDIA_MOUNTED.equals(Environment
                    .getExternalStorageState())) {
                extStorageAvailable = true;
            }
        }
        if (extStorageAvailable) {
            return getWorkingDirectory(TYPE_LOG);
        } else {
            return DEFAULT_LOG_DIR;
        }
    }

    public static void ensureDirExists(String dir) {
        if (!TextUtils.isEmpty(dir)) {
            File file = new File(dir);
            if (!file.exists()) {
                if (file.isFile()) {
                    file = file.getParentFile();
                }
                if (file != null) {
                    boolean result = file.mkdirs();
                }
            }
        }
    }

    public static void ensureNomediaExists(String dir) {
        if (!TextUtils.isEmpty(dir)) {
            File file = new File(dir, ".nomedia");
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                }
            }
        }
    }

    public static int setPermissions(String file, int mode, int uid, int gid) {
        Integer val = (Integer) ReflectHelper.callStaticMethod(
                "android.os.FileUtils", "setPermissions", new Class<?>[]{
                        String.class, int.class, int.class, int.class},
                new Object[]{file, mode, uid, gid});
        if (val != null) {
            return val.intValue();
        } else {
            return -1;
        }
    }
}
