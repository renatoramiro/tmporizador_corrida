package com.temporizadorciclico.app;

import android.os.Bundle;
import android.util.Log;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "TimerNative";

    /**
     * load() cria o Bridge a partir do bridgeBuilder.
     * O plugin precisa ser registrado ANTES de super.load(), senão já é tarde.
     */
    @Override
    protected void load() {
        try {
            registerPlugin(TimerPlugin.class);
        } catch (Throwable t) {
            Log.e(TAG, "Falha ao registrar TimerPlugin", t);
        }
        super.load();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().addJavascriptInterface(new TimerJsBridge(this), "TimerAndroid");
                Log.i(TAG, "TimerAndroid bridge registrado");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Falha ao registrar TimerAndroid bridge", t);
        }
    }

    /**
     * Ao fechar de verdade (back / swipe em Recentes), encerra o TimerService.
     * Ir para home ou bloquear a tela NÃO passa por aqui — o treino continua.
     */
    @Override
    public void onDestroy() {
        if (isFinishing()) {
            try {
                TimerService.stopAll(this);
                Log.i(TAG, "Activity finalizando — timer interrompido");
            } catch (Throwable t) {
                Log.e(TAG, "Falha ao parar timer no onDestroy", t);
            }
        }
        super.onDestroy();
    }
}
