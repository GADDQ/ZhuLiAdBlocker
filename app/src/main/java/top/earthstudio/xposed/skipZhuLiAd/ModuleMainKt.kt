package top.earthstudio.xposed.skipZhuLiAd

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class ModuleMainKt : XposedModule() {

    companion object {
        const val TAG = "住理生活广告屏蔽"

        @Volatile
        private var hasHooked = false
    }

    override fun onPackageReady(param: PackageReadyParam) {
        try {
            // 1. 直接 Hook 系统的 Application.onCreate()，该方法必然存在于 Application 中
            val onCreateMethod = Application::class.java.getDeclaredMethod("onCreate")

            hook(onCreateMethod).intercept { chain ->
                // 先执行原 onCreate
                val result = chain.proceed()

                val app = chain.thisObject as Application

                if (!hasHooked) {
                    hasHooked = true
                    log(Log.INFO, TAG, "[+] 检测到 SecShell 启动: ${app.javaClass.name}")
                    // 获取壳解密完成后的真实 ClassLoader
                    val realClassLoader = app.classLoader
                    log(Log.INFO, TAG, "[✔] 成功获取脱壳后 ClassLoader : $realClassLoader")

                    // 执行业务 Hook
                    hookLogic(realClassLoader)
                }

                result
            }

        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "[-] 挂钩 Application.onCreate 异常", t)
        }
    }

    /**
     * 业务 Hook 逻辑区域
     */
    private fun hookLogic(realClassLoader: ClassLoader) {
        log(Log.INFO, TAG, "[+] 准备注入住理生活")
        try {
            val splashActivity =
                Class.forName("com.whxinna.zhuli.SplashActivity", true, realClassLoader)
            val interstitialAdActivity =
                Class.forName("com.whxinna.ad.InterstitialAdActivity", true, realClassLoader)

            val webViewActivity =
                Class.forName("com.whxinna.webview.WebViewActivity", true, realClassLoader)

            log(Log.INFO, TAG, "[✔] 获取到广告页: ${splashActivity.name}")
            log(Log.INFO, TAG, "[✔] 获取到广告页: ${interstitialAdActivity.name}")

            val methodWebViewOnWindowFocusChanged = webViewActivity.getDeclaredMethod(
                "onWindowFocusChanged",
                Boolean::class.java
            )

            val superOnWindowFocusChangedMethod = Activity::class.java.getDeclaredMethod(
                "onWindowFocusChanged",
                Boolean::class.java
            )

            hook(methodWebViewOnWindowFocusChanged).intercept { chain ->
                val activity = chain.thisObject as Activity
                val bool = chain.args[0] as? Boolean
                getInvoker(superOnWindowFocusChangedMethod).invokeSpecial(activity, bool)
                return@intercept null
            }


            val methodSplashOnCreate = splashActivity.getDeclaredMethod(
                "onCreate",
                Bundle::class.java
            )
            val methodSplashOnPause = splashActivity.getDeclaredMethod(
                "onPause",
            )
            val methodSplashOnResume = splashActivity.getDeclaredMethod(
                "onResume",
            )

            hook(methodSplashOnPause).intercept { chain ->
                return@intercept null
            }

            hook(methodSplashOnResume).intercept { chain ->
                return@intercept null
            }

            val superOnCreateMethod = Activity::class.java.getDeclaredMethod(
                "onCreate",
                Bundle::class.java
            )

            hook(methodSplashOnCreate).intercept { chain ->
                log(Log.INFO, TAG, "[✔] 已拦截广告页: ${splashActivity.name}")

                val activity = chain.thisObject as Activity
                val bundle = chain.args[0] as? Bundle

                getInvoker(superOnCreateMethod).invokeSpecial(activity, bundle)

                val intent = Intent(activity, webViewActivity).apply {
                    putExtra("url", "/home")
                    putExtra("type", "appOnLaunch")
                    // 添加 FLAG 确保新 Activity 干净启动
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }

                activity.startActivity(intent)
                activity.finish()
                activity.overridePendingTransition(0, 0)
                return@intercept null
            }

            log(Log.INFO, TAG, "[✔] Hook 广告页 Activity 成功: ${splashActivity.name}")

            val methodInterstitialOnCreate = interstitialAdActivity.getDeclaredMethod(
                "onCreate",
                Bundle::class.java
            )

            hook(methodInterstitialOnCreate).intercept { chain ->
                log(Log.INFO, TAG, "[✔] 已拦截广告页: ${interstitialAdActivity.name}")
                val activity = chain.thisObject as Activity
                activity.finish()
                return@intercept null
            }

            log(Log.INFO, TAG, "[✔] Hook 广告页 Activity 成功: ${interstitialAdActivity.name}")

            log(Log.INFO, TAG, "[✔] 模块已成功注入，广告屏蔽上线！")

        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "[-] 业务 Hook 执行异常", t)
        }
    }
}