package com.shidaifufen.magic2airpods5;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

import java.lang.reflect.Method;

/**
 * MagicToAirPods5
 *
 * 1) 让荣耀「智慧空间」(com.hihonor.magichome) 把 AirPods 5 当作受支持的 Apple 耳机处理；
 * 2) 在 LibrePods 进程里建立桥接，让本模块自带的「降噪控制」桌面小组件可以切换 ANC 模式。
 *
 * 原理：
 *  - 荣耀：AppleAirDevManager 用云端下发的 prodIdList 白名单判断耳机型号，AirPods 5 不在
 *    旧白名单中 -> 强制其 boolean 判定返回 true 绕过；
 *  - LibrePods：其官方 NoiseControlWidget 用 ACTION_SET_ANC_MODE + ANC_MODE(1关闭/2降噪/3通透/4自适应)
 *    走 aacpManager.sendControlCommand(LISTENING_MODE)。该 receiver 未导出，普通 App 发不进去，
 *    所以本模块在 LibrePods 进程里注册一个受签名权限保护的桥接收器，收到点击后原样转发给官方
 *    NoiseControlWidget —— 完全复用 LibrePods 自己的控制通道。
 *
 * 适用：MagicOS 11 / 智慧空间 10.60.x + LibrePods；LDY-AN00 实测
 * 作用域：com.hihonor.magichome, me.kavishdevar.librepods
 */
public class HookMain extends XposedModule {
    private static final String TAG = "MagicToAirPods5";

    private static final String MAGICHOME_PKG = "com.hihonor.magichome";
    private static final String MAGICHOME_CLS =
            "com.hihonor.magichome.devicemanager.data.AppleAirDevManager";

    private static final String LIBREPODS_PKG = "me.kavishdevar.librepods";
    private static final String LIBREPODS_SERVICE =
            "me.kavishdevar.librepods.services.AirPodsService";
    private static final String LIBREPODS_RECEIVER =
            "me.kavishdevar.librepods.presentation.widgets.NoiseControlWidget";

    private static final String BRIDGE_ACTION = "com.shidaifufen.magic2airpods5.SET_ANC";
    private static final String ANC_PERMISSION = "com.shidaifufen.magic2airpods5.SEND_ANC";
    private static final String EXTRA_MODE = "mode";

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (param == null) return;
        String pkg = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();
        log(4, TAG, "onPackageLoaded " + pkg);
        try {
            if (MAGICHOME_PKG.equals(pkg)) {
                hookAirDevManager(cl);
            } else if (LIBREPODS_PKG.equals(pkg)) {
                hookLibrePodsBridge(cl);
            }
        } catch (Throwable t) {
            log(6, TAG, "hook failed for " + pkg + ": " + t, t);
        }
    }

    // ============ 1) 荣耀智慧空间：AirPods 5 白名单绕过 ============

    private void hookAirDevManager(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName(MAGICHOME_CLS, false, cl);
        int hooked = 0;
        for (final Method m : cls.getDeclaredMethods()) {
            if (m.getReturnType() != boolean.class) continue;
            Class<?>[] pts = m.getParameterTypes();
            boolean match = (pts.length == 0) || (pts.length == 1 && pts[0] == String.class);
            if (!match) continue;
            try {
                m.setAccessible(true);
                hook(m).intercept(new XposedInterface.Hooker() {
                    @Override public Object intercept(XposedInterface.Chain chain) {
                        log(4, TAG, "force true: " + chain.getExecutable().getName());
                        return Boolean.TRUE;
                    }
                });
                hooked++;
            } catch (Throwable t) {
                log(5, TAG, "hook " + m.getName() + " failed: " + t);
            }
        }
        log(4, TAG, "AirDevManager hooks installed, count=" + hooked);
    }

    // ============ 2) LibrePods：ANC 切换桥接 ============

    private void hookLibrePodsBridge(ClassLoader cl) throws Throwable {
        final Class<?> svc = Class.forName(LIBREPODS_SERVICE, false, cl);
        final Method onCreate = svc.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        hook(onCreate).intercept(new XposedInterface.Hooker() {
            @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
                Object result = chain.proceed();
                Object self = chain.getThisObject();
                if (self instanceof Context) {
                    registerBridge((Context) self);
                }
                return result;
            }
        });
        log(4, TAG, "LibrePods bridge hooked");
    }

    private void registerBridge(final Context ctx) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                int mode = intent.getIntExtra(EXTRA_MODE, 1);
                try {
                    Intent fwd = new Intent();
                    fwd.setClassName(LIBREPODS_PKG, LIBREPODS_RECEIVER);
                    fwd.setAction("ACTION_SET_ANC_MODE");
                    fwd.putExtra("ANC_MODE", mode);
                    context.sendBroadcast(fwd);
                    log(4, TAG, "forward ANC mode " + mode + " -> LibrePods");
                } catch (Throwable t) {
                    log(6, TAG, "forward ANC failed", t);
                }
            }
        };
        IntentFilter filter = new IntentFilter(BRIDGE_ACTION);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, ANC_PERMISSION, null, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(receiver, filter, ANC_PERMISSION, null);
            }
            log(4, TAG, "LibrePods bridge registered");
        } catch (Throwable t) {
            log(6, TAG, "register bridge failed", t);
        }
    }
}
