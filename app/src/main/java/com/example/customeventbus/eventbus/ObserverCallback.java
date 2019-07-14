package com.example.customeventbus.eventbus;

public interface ObserverCallback {
    public void handleBusEvent(Object observer, Object event);
}
