package com.example.customeventbus.util;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Process;
import android.util.Log;


import com.example.customeventbus.storage.StorageUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * @author: robin
 * @description:日志打印工具类
 * @date: 2015/7/1
 **/
public class CustomLog {
    private static String FLAG_OPEN_LOGFUN_FILE = "/sdcard/debugversion_log";
    private static String FLAG_OPEN_LOGFUN_FILEX = "/sdcard/enable_debug";
    private static boolean isFirst = true;
    public static boolean isPrintLog = false;
    private static boolean isWriteToFile = false;
    private static AtomicBoolean gCheckingLog = new AtomicBoolean(false);
    private final static String LOG_FILENAME = "log";
    private final static String LOG_FILEEXT = ".txt";
    private static File mLogFile;
    private static LinkedBlockingQueue<LogItem>	gLogQueue ;
    private	static Thread						gWriteLogThread ;
    private final static long LOGFILE_LIMIT = 1000000L;
    private final static SimpleDateFormat DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final static SimpleDateFormat DATEFORMAT1 = new SimpleDateFormat("yyyyMMddHHmmss");

    public static void init(Context context){
        FLAG_OPEN_LOGFUN_FILE = StorageUtil.getInstance().getWorkingDirectory()
                + File.separator + "debugversion_log";
        FLAG_OPEN_LOGFUN_FILEX = StorageUtil.getInstance().getWorkingDirectory()
                + File.separator + "enable_debug";
    }

    static class LogItem{
        String	level;
        String	tag ;
        String	msg ;
        LogItem(String _level, String _tag, String _msg){
            level = _level ;
            tag = _tag ;
            msg = _msg ;
        }
    };

    public static void setPrintLog(boolean enable){
        setPrintLog(enable, false);
    }

    public static void setPrintLog(boolean enable, boolean writeToFile){
        isPrintLog = enable;
        isFirst = false;
        setIsPrintLog();
        if (writeToFile){
            createLogFile();
        }
    }

    public static void setIsPrintLog() {
        try {
            File openLogFunFile = new File(FLAG_OPEN_LOGFUN_FILE);
            File openLogFunFilex = new File(FLAG_OPEN_LOGFUN_FILEX);
            if (openLogFunFile.exists() || openLogFunFilex.exists()) {
                isPrintLog = true;
            }
            isFirst = false;
        } catch (Exception ex) {
        }
    }

    public static void setIsPrintLog(Activity acti) {
        try {
            Intent intent = acti.getIntent();
            // 文件是否存在
            File openLogFunFile = new File(FLAG_OPEN_LOGFUN_FILE);
            File openLogFunFilex = new File(FLAG_OPEN_LOGFUN_FILEX);
            if (openLogFunFile.exists() || openLogFunFilex.exists()) {
                isPrintLog = true;
            }
            // intent内是否有数据
            if (isPrintLog == false) {
                isPrintLog = intent.getBooleanExtra("custom.log.debug", false);

                if (isPrintLog == true) {
                    openLogFunFilex.createNewFile();
                }
            }

            isFirst = false;

        } catch (Exception ex) {

        }
    }

    private static void checkLog() {
        if (isFirst &&!gCheckingLog.get()){
            gCheckingLog.set(true);
            ThreadUtil.queueWork(new Runnable() {
                @Override
                public void run() {
                    checkingLog();
                    gCheckingLog.set(false);
                }
            });
        }
    }

    private static void checkingLog(){
        if (isFirst == true) {
            File openLogFunFile = new File(FLAG_OPEN_LOGFUN_FILE);
            File openLogFunFilex = new File(FLAG_OPEN_LOGFUN_FILEX);
            if (openLogFunFile.exists() || openLogFunFilex.exists()) {
                isPrintLog = true;
                isWriteToFile = true;
            } else {
                isWriteToFile = false;
            }
            isFirst = false;
        }
        createLogFile();
    }

    public static void print(String msg) {
        checkLog();
        if (isPrintLog) {
        }
        writeLogFile("", "", msg);
    }

    private static void createLogFile() {
        if (isWriteToFile || isPrintLog) {
            synchronized (LOG_FILENAME) {
                if (mLogFile == null) {
                    try {
                        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                            return;
                        }
                        mLogFile = new File (StorageUtil.getInstance().getLogDirectory()
                                + File.separator + LOG_FILENAME +"-"+Process.myPid()
                                + LOG_FILEEXT);
                        if (!mLogFile.exists()) {
                            Log.d("TestFile", "Create the file:" + LOG_FILENAME);
                            mLogFile.createNewFile();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    if (mLogFile.isFile()) {
                        if (mLogFile.length() > LOGFILE_LIMIT) {
                            StringBuffer sb = new StringBuffer(StorageUtil.getInstance().getLogDirectory()
                                    + File.separator);
                            sb.append(LOG_FILENAME+"-"+Process.myPid()+"-");
                            sb.append(DATEFORMAT1.format(new Date()));
                            sb.append(LOG_FILEEXT);
                            mLogFile.renameTo(new File(sb.toString()));
                            sb = null;
                            sb = new StringBuffer(StorageUtil.getInstance().getLogDirectory()
                                    + File.separator);
                            sb.append(LOG_FILENAME);
                            sb.append(LOG_FILEEXT);
                            mLogFile = new File(sb.toString());
                            sb = null;
                            if (!mLogFile.exists()) {
                                Log.d("TestFile", "Create the file:" + LOG_FILENAME + LOG_FILEEXT);
                                try {
                                    mLogFile.createNewFile();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
                if (gLogQueue == null && mLogFile != null){
                    gLogQueue = new LinkedBlockingQueue<CustomLog.LogItem>();
                    gWriteLogThread = new WriteLogThread();
                    gWriteLogThread.start() ;
                }
            }
        }
    }

    private static void writeLogFile(String level, String tag, String msg) {
        if (isWriteToFile || isPrintLog) {
            if (gLogQueue != null){
                try {
                    gLogQueue.put(new LogItem(level, tag, msg));
                } catch (InterruptedException e) {
                    Log.e(tag, "writeLogFile error,reason="+e);
                }
            }
        }
    }

    public static void println(String msg) {
        checkLog();
        if (isPrintLog) {
        }
        writeLogFile("", "", msg);
    }

    public static void i(String tag, String msg) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.i(tag, msg == null ? "" : msg);
        }

        writeLogFile("INFO", tag, msg);
    }

    public static void i(String tag, String msg, Throwable tr) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.i(tag, msg == null ? "" : msg, tr);
        }
        writeLogFile("INFO", tag, msg);
    }

    public static void d(String tag, String msg) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.d(tag, msg == null ? "" : msg);
        }
        writeLogFile("DEBUG", tag, msg);
    }

    public static void d(String tag, String msg, Throwable tr) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.d(tag, msg == null ? "" : msg, tr);
        }
        writeLogFile("DEBUG", tag, msg);
    }

    public static void e(String tag, String msg) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.e(tag, msg == null ? "" : msg);
        }
        writeLogFile("ERROR", tag, msg);
    }

    public static void e(String tag, String msg, Throwable tr) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.e(tag, msg == null ? "" : msg, tr);
        }
        writeLogFile("ERROR", tag, msg);
    }

    public static void v(String tag, String msg) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.v(tag, msg == null ? "" : msg);
        }
        writeLogFile("VERBOSE", tag, msg);
    }

    public static void v(String tag, String msg, Throwable tr) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.v(tag, msg == null ? "" : msg, tr);
        }
        writeLogFile("VERBOSE", tag, msg);
    }

    public static void w(String tag, String msg) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.w(tag, msg == null ? "" : msg);
        }
        writeLogFile("WARN", tag, msg);
    }

    public static void w(String tag, String msg, Throwable tr) {
        checkLog();
        if (isPrintLog) {
            android.util.Log.w(tag, msg == null ? "" : msg, tr);
        }
        writeLogFile("WARN", tag, msg);
    }

    public static void saveToSd(String filename, String data) {
        String workingDir = StorageUtil.getInstance().getWorkingDirectory() + File.separator;
        File file = new File(workingDir + filename);
        int index = -1;
        index = filename.lastIndexOf('/');
        if (index > 0) {
            filename = filename.substring(index + 1);
        }
        index = filename.lastIndexOf('.');
        String basename, extname;
        if (index > 0) {
            basename = filename.substring(0, index);
            extname = filename.substring(index);
        } else {
            basename = filename;
            extname = "";
        }
        index = 0;
        while (file.exists()) {
            file = null;
            file = new File(workingDir + basename + (index++) + extname);
        }
        FileOutputStream fos = null;
        try {
            file.createNewFile();
            fos = new FileOutputStream(file, true);
            fos.write(data.getBytes());
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                fos = null;
            }
        }
    }


    public static void ln(String tag, String lines){
        if (CustomLog.isPrintLog) {
            if (lines == null) {
                return;
            }
            try {
                int presize = 3 * 1024;
                for (int i = 0; i < (lines.length() / presize) + 1; i++) {
                    int startpos = presize * i;
                    int endpos = Math.min(startpos + presize, lines.length());
                    CustomLog.v(tag, lines.substring(startpos, endpos));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static class WriteLogThread extends Thread{
        @Override
        public void run() {
            if (mLogFile == null || gLogQueue == null){
                return ;
            }
            setName("logthread");
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(mLogFile, "rw");
                raf.seek(mLogFile.length());
                StringBuffer sb = new StringBuffer();
                LogItem	item = null ;
                while(!isInterrupted()){
                    item = gLogQueue.take();
                    sb.setLength(0);
                    sb.append(DATEFORMAT.format(new Date()));
                    sb.append(": ");
                    sb.append(item.level);
                    sb.append(": ");
                    sb.append(item.tag);
                    sb.append(": ");
                    sb.append(item.msg);
                    sb.append("\n");
                    raf.write(sb.toString().getBytes("UTF-8"));
                    if (raf.length() > LOGFILE_LIMIT){
                        File oldFile = mLogFile ;
                        createLogFile() ;
                        if (oldFile != mLogFile){
                            raf.close();
                            raf = new RandomAccessFile(mLogFile, "rw");
                        }
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (raf != null) {
                    try {
                        raf.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
