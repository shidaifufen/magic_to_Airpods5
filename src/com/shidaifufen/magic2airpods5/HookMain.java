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

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * MagicToAirPods5
 *
 * 设计原则：**官方支持优先**。
 *  - 如果检测到荣耀官方已经支持 AirPods 5（智慧空间的白名单里已有该型号），
 *    模块自动跳过白名单绕过，只保留桌面降噪小组件功能，避免与官方逻辑冲突；
 *  - 只有官方还不认识 AirPods 5 时，才 hook AppleAirDevManager 绕过型号白名单。
 *
 * 另外：在 LibrePods 进程里建立桥接，让模块自带的「降噪控制」桌面小组件可以切换 ANC 模式。
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

    /** 官方支持检测：DB 里出现 AirPods 5 的型号名即视为官方已支持 */
    private static final String[] OFFICIAL_MARKERS = {
            "AirPods 5", "AirPods5", "AirPods 5 (Wireless Charging Case)"
    };

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (param == null) return;
        String pkg = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();
        log(4, TAG, "onPackageLoaded " + pkg);
        try {
            if (MAGICHOME_PKG.equals(pkg)) {
                if (isOfficialSupported()) {
                    log(4, TAG, "官方已支持 AirPods 5，跳过白名单绕过（模块仅保留桌面小组件功能）");
                } else {
                    log(4, TAG, "官方尚未支持 AirPods 5，启用白名单绕过");
                    hookAirDevManager(cl);
                }
            } else if (LIBREPODS_PKG.equals(pkg)) {
                hookLibrePodsBridge(cl);
            }
        } catch (Throwable t) {
            log(6, TAG, "hook failed for " + pkg + ": " + t, t);
        }
    }

    // ============ 官方支持检测 ============

    private boolean isOfficialSupported() {
        try {
            Context ctx = currentAppContext();
            if (ctx == null) return false;
            String dataDir = ctx.getApplicationInfo().dataDir;
            File db = new File(dataDir, "databases/batteryServices.db");
            if (readAndMatch(db)) return true;
            File wal = new File(dataDir, "databases/batteryServices.db-wal");
            if (readAndMatch(wal)) return true;
        } catch (Throwable t) {
            log(5, TAG, "official support detection failed: " + t);
        }
        return false;
    }

    private boolean readAndMatch(File file) {
        if (file == null || !file.exists() || file.length() <= 0) return false;
        FileInputStream in = null;
        try {
            int len = (int) Math.min(file.length(), 4L * 1024 * 1024);
            byte[] buf = new byte[len];
            in = new FileInputStream(file);
            int off = 0, r;
            while (off < len && (r = in.read(buf, off, len - off)) > 0) off += r;
            // SQLite 里文本按 UTF-8 存，ASCII 子串直接按 UTF-8 解码即可匹配
            String s = new String(buf, 0, off, "UTF-8");
            for (String marker : OFFICIAL_MARKERS) {
                if (s.contains(marker)) {
                    log(4, TAG, "检测到官方白名单条目: " + marker);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
        }
        return false;
    }

    private Context currentAppContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method current = at.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            Object thread = current.invoke(null);
            if (thread == null) return null;
            Method getApp = at.getDeclaredMethod("getApplication");
            getApp.setAccessible(true);
            Object app = getApp.invoke(thread);
            return (app instanceof Context) ? (Context) app : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ============ 1) 荣耀智慧空间：AirPods 5 白名单绕过（仅官方未支持时启用） ============

    private void hookAirDevManager(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName(MAGICHOME_CLS, false, cl);

        // 已知 v10.60.08.300 的判定方法：o/p/q/u(String)->Z, r()->Z。
        // 若未来版本改名，则退化为 hook 全部 boolean 方法。
        List<Method> known = new ArrayList<>();
        for (String name : new String[]{"o", "p", "q", "u"}) {
            try {
                Method m = cls.getDeclaredMethod(name, String.class);
                if (m.getReturnType() == boolean.class) known.add(m);
            } catch (Throwable ignored) {}
        }
        try {
            Method m = cls.getDeclaredMethod("r");
            if (m.getReturnType() == boolean.class) known.add(m);
        } catch (Throwable ignored) {}

        List<Method> targets = known;
        if (targets.isEmpty()) {
            log(5, TAG, "未找到已知判定方法，退化为 hook 全部 boolean 方法");
            targets = new ArrayList<>();
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getReturnType() != boolean.class) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 0 || (pts.length == 1 && pts[0] == String.class)) {
                    targets.add(m);
                }
            }
        }

        int hooked = 0;
        for (final Method m : targets) {
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

    // ============ 2) LibrePods：ANC 切换桥接（始终保留） ============

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
