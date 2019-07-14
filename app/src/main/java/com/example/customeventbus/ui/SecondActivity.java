package com.example.customeventbus.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.customeventbus.R;
import com.example.customeventbus.bean.UpdateEvent;
import com.example.customeventbus.eventbus.ManagedEventBus;

/**
 * @author: robin
 * @description:
 * @date: 2015/7/14
 **/
public class SecondActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);
        findViewById(R.id.second_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UpdateEvent event = new UpdateEvent();
                event.updateNum = 6;
                ManagedEventBus.postEvent(event);
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
