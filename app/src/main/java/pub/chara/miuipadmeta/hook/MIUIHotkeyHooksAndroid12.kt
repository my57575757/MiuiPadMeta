package pub.chara.miuipadmeta.hook

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.view.KeyEvent
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object MIUIHotkeyHooksAndroid12 : BaseHook() {

    // 月光串流包名前缀（release: com.limelight.unofficialA, debug: com.limelight.debug, 官方: com.limelight）
    private const val LIMELIGHT_PKG = "com.limelight"
    private const val LIMELIGHT_PREFIX = "com.limelight."

    override fun init() {
        // Win+D 等 MIUI/AOSP 组合键在入队前由 KeyCombinationManager 消费
        // （早于辅助功能过滤器），命中后触发 launch_home 等系统动作。
        // 月光前台时直接返回未消费，让事件透传给应用 -> 月光辅助功能 -> PC。
        try {
            findMethod("com.android.server.policy.KeyCombinationManager") {
                name == "interceptKey"
            }.hookBefore { param ->
                if (isLimelightForeground()) {
                    param.result = false
                }
            }
            XposedBridge.log("MiuiPadMeta: hooked KeyCombinationManager.interceptKey")
        } catch (e: Throwable) {
            XposedBridge.log("MiuiPadMeta: hook KeyCombinationManager.interceptKey failed!")
            XposedBridge.log(e)
        }

        // 保证组合键的 up / repeat 也不被系统吞掉（否则 PC 端修饰键无法抬起）
        try {
            findMethod("com.android.server.policy.KeyCombinationManager") {
                name == "isKeyConsumed"
            }.hookBefore { param ->
                if (isLimelightForeground()) {
                    param.result = false
                }
            }
            XposedBridge.log("MiuiPadMeta: hooked KeyCombinationManager.isKeyConsumed")
        } catch (e: Throwable) {
            XposedBridge.log("MiuiPadMeta: hook KeyCombinationManager.isKeyConsumed failed!")
            XposedBridge.log(e)
        }

        // Alt+Tab / Win+Tab 在分发前被 PhoneWindowManager 拦截并弹出系统最近任务。
        // 月光前台命中时返回 0（不拦截，正常分发给应用）。
        try {
            findMethod("com.android.server.policy.BaseMiuiPhoneWindowManager") {
                name == "interceptKeyBeforeDispatching"
            }.hookBefore { param ->
                run {
                    if (!isLimelightForeground()) return@hookBefore
                    val event = param.args[1] as KeyEvent
                    val isAltTab = event.keyCode == KeyEvent.KEYCODE_TAB &&
                        (event.isAltPressed || event.isMetaPressed)
                    // META 单键默认被 MIUI 判定消费（只用于系统 Dock），
                    // 抑制 Dock 后必须显式放行，否则 Win 变成死键。
                    val isMetaOnly = event.keyCode == KeyEvent.KEYCODE_META_LEFT ||
                        event.keyCode == KeyEvent.KEYCODE_META_RIGHT
                    if (isAltTab || isMetaOnly) {
                        param.result = 0L
                    }
                }
            }
            XposedBridge.log("MiuiPadMeta: hooked BaseMiuiPhoneWindowManager.interceptKeyBeforeDispatching")
        } catch (e: Throwable) {
            XposedBridge.log("MiuiPadMeta: hook interceptKeyBeforeDispatching failed!")
            XposedBridge.log(e)
        }

        // Meta 单键在入队前会 post 系统快捷键菜单 / Dock 的 Runnable。
        // 月光前台时跳过，避免单按 Win 被系统 UI 干扰。
        try {
            findMethod("com.android.server.policy.BaseMiuiPhoneWindowManager") {
                name == "handleMetaKey"
            }.hookBefore { param ->
                if (isLimelightForeground()) {
                    param.result = null
                }
            }
            XposedBridge.log("MiuiPadMeta: hooked BaseMiuiPhoneWindowManager.handleMetaKey")
        } catch (e: Throwable) {
            XposedBridge.log("MiuiPadMeta: hook handleMetaKey failed!")
            XposedBridge.log(e)
        }

        XposedBridge.log("MiuiPadMeta: MIUIHotkeyHooksAndroid12 done!")
    }

    // 模块代码运行在 system_server（uid 1000）。KeyCombinationManager 自身无 Context 字段，
    // 而 system_server 的 ActivityThread 在 systemMain() 中已创建 mInitialApplication，
    // 故直接静态获取；getRunningTasks(1) 取栈顶即真实前台包名。
    private fun isLimelightForeground(): Boolean {
        val app = systemApplication() ?: return false
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val pkg = try {
            am.getRunningTasks(1)?.firstOrNull()?.topActivity?.packageName
        } catch (e: Throwable) {
            null
        }
        return pkg == LIMELIGHT_PKG || (pkg != null && pkg.startsWith(LIMELIGHT_PREFIX))
    }

    private fun systemApplication(): Application? = try {
        val at = XposedHelpers.findClass("android.app.ActivityThread", null)
        XposedHelpers.callStaticMethod(at, "currentApplication") as? Application
    } catch (e: Throwable) {
        XposedBridge.log("MiuiPadMeta: get system application failed!")
        XposedBridge.log(e)
        null
    }
}
