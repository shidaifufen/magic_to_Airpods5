package com.shidaifufen.magic2airpods5;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * 荣耀风格三档降噪小组件：降噪 / 通透 / 关闭
 * 点击后向 LibrePods 进程中的模块桥接广播，由桥接转发给 LibrePods 官方 NoiseControlWidget。
 */
public class NoiseWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "MagicToAirPods5";

    public static final String ACTION_CLICK = "com.shidaifufen.magic2airpods5.WIDGET_CLICK";
    public static final String ACTION_BRIDGE = "com.shidaifufen.magic2airpods5.SET_ANC";
    public static final String EXTRA_MODE = "mode";

    // LibrePods ANC_MODE: 1=关闭 2=降噪 3=通透 4=自适应
    private static final int MODE_OFF = 1;
    private static final int MODE_ANC = 2;
    private static final int MODE_TRANS = 3;

    private static final String PREFS = "noise_widget";
    private static final String KEY_MODE = "last_mode";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_CLICK.equals(action)) {
            int mode = intent.getIntExtra(EXTRA_MODE, MODE_OFF);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_MODE, mode).apply();

            Intent bridge = new Intent(ACTION_BRIDGE);
            bridge.putExtra(EXTRA_MODE, mode);
            context.sendBroadcast(bridge);
            Log.i(TAG, "widget click, mode=" + mode);

            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, NoiseWidgetProvider.class));
            for (int id : ids) {
                manager.updateAppWidget(id, buildViews(context));
            }
        }
    }

    private RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.w_noise);
        int mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MODE, MODE_OFF);

        views.setInt(R.id.btn_anc, "setBackgroundResource",
                mode == MODE_ANC ? R.drawable.w_btn_sel : R.drawable.w_btn);
        views.setInt(R.id.btn_trans, "setBackgroundResource",
                mode == MODE_TRANS ? R.drawable.w_btn_sel : R.drawable.w_btn);
        views.setInt(R.id.btn_off, "setBackgroundResource",
                mode == MODE_OFF ? R.drawable.w_btn_sel : R.drawable.w_btn);

        views.setOnClickPendingIntent(R.id.btn_anc, pending(context, MODE_ANC));
        views.setOnClickPendingIntent(R.id.btn_trans, pending(context, MODE_TRANS));
        views.setOnClickPendingIntent(R.id.btn_off, pending(context, MODE_OFF));
        return views;
    }

    private PendingIntent pending(Context context, int mode) {
        Intent intent = new Intent(context, NoiseWidgetProvider.class);
        intent.setAction(ACTION_CLICK);
        intent.putExtra(EXTRA_MODE, mode);
        return PendingIntent.getBroadcast(context, mode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
