package com.example.customeventbus.eventbus;

public enum EventThread {
    MAIN_THREAD , //指定在UI线程处理总线事件
    WORK_THREAD  //在工作线程池时处理总线事件
}
