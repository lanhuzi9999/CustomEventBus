package com.example.customeventbus.eventbus;

public class EventType {
	/**
	 * 事件类
	 */
    private Class<?>	mEventClass;
	/**
	 * 事件处理类
	 */
	private ObserverCallback mCallback;
    private EventThread	mEventThread;
    public EventType(Class<?> eventclass, ObserverCallback callback, EventThread eventthread){
	mEventClass = eventclass;
	mCallback = callback;
	mEventThread = eventthread;
    }
    
    public Class<?> getEventClass(){
	return mEventClass;
    }
    
    public ObserverCallback getCallback(){
	return mCallback ;
    }
    
    public EventThread	getEventThread(){
	return mEventThread;
    }

    @Override
    public boolean equals(Object o) {
	if (this == o){
	    return true;
	}
	if (o == null){
	    return false;
	}
	if (getClass() != o.getClass()){
	    return false;
	}
	EventType et = (EventType)o ;
	if (mEventClass != et.mEventClass){
	    return false;
	}
	if (mCallback != et.mCallback){
	    return false;
	}
	if (mEventThread != et.mEventThread){
	    return false;
	}
	return true;
    }
    
}
