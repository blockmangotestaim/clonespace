package com.example.clonespace;

import android.app.Application;
import android.content.Context;

public class App extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        EngineBridge.attach(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EngineBridge.create();
    }
}
