package com.shidaifufen.magic2airpods5;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

import java.lang.reflect.Method;

/**
 * MagicToAirPods5
 *
 * 让荣耀 MagicOS 的「智慧空间」(com.hihonor.magichome) 把 AirPods 5 当作受支持的
 * Apple 耳机处理，从而在控制中心「设备互联中心」显示电量/噪声控制/查找耳机。
 *
 * 原理：
 *  荣耀在 com.hihonor.magichome.devicemanager.data.AppleAirDevManager 里用
 *  isSupportAirPodsDevice(deviceArr, prodIdList) 判断耳机型号是否在云端下发的
 *  prodIdList 白名单里；AirPods 5 不在旧白名单中，于是返回 false、整条 AirPods
 *  处理链路被跳过。本模块把该类的 boolean 判定方法强制返回 true，绕过白名单。
 *
 * 适用：MagicOS 11 / 智慧空间 10.60.x（LDY-AN00 实测）
 * 作用域：com.hihonor.magichome
 */
public class HookMain extends XposedModule {
    private static final String TAG = "MagicToAirPods5";
    private static final String TARGET_PKG = "com.hihonor.magichome";
    private static final String TARGET_CLS =
            "com.hihonor.magichome.devicemanager.data.AppleAirDevManager";

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (param == null) return;
        String pkg = param.getPackageName();
        if (!TARGET_PKG.equals(pkg)) return;
        ClassLoader cl = param.getDefaultClassLoader();
        log(4, TAG, "onPackageLoaded " + pkg);
        try {
            hookAirDevManager(cl);
        } catch (Throwable t) {
            log(6, TAG, "hook failed: " + t, t);
        }
    }

    private void hookAirDevManager(ClassLoader cl) throws Throwable {
        Class<?> cls = Class.forName(TARGET_CLS, false, cl);
        int hooked = 0;
        for (final Method m : cls.getDeclaredMethods()) {
            if (m.getReturnType() != boolean.class) continue;
            Class<?>[] pts = m.getParameterTypes();
            boolean match = (pts.length == 0) ||
                    (pts.length == 1 && pts[0] == String.class);
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
                log(4, TAG, "hooked " + m.getName() + java.util.Arrays.toString(pts));
            } catch (Throwable t) {
                log(5, TAG, "hook " + m.getName() + " failed: " + t);
            }
        }
        log(4, TAG, "AirDevManager hooks installed, count=" + hooked);
    }
}
