package com.temporizadorciclico.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPlugin(TimerPlugin.class);
        try {
            getBridge()
                .getWebView()
                .addJavascriptInterface(new TimerJsBridge(this), "TimerAndroid");
        } catch (Exception ignored) {
        }
    }
}
