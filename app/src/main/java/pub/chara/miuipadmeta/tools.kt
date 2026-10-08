package pub.chara.miuipadmeta

import android.app.ActivityManager
import android.content.Context
import android.text.TextUtils

class tools {
    /**
     * 判断某个Activity是否在前台
     *
     * @param context   Context
     * @param className 界面的类名
     * @return 是否在前台显示
     */
    public fun isForeground(context: Context?, className: String):Boolean {
        if (context == null || TextUtils.isEmpty(className)) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val list = am.getRunningTasks(10)
        if (list != null && list.size > 0) {
            val cpn = list[0].topActivity
            if (cpn != null) {
                if (className == cpn.className) return true
            }
        }
        return false
    }

    companion object {
        fun isForeground(context: Context?, className: String): Boolean {
            if (context == null || TextUtils.isEmpty(className)) return false
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val list = am.getRunningTasks(10)
            if (list != null && list.size > 0) {
                val cpn = list[0].topActivity
                if (cpn != null) {
                    if (className == cpn.className) return true
                }
            }
            return false
        }
    }
}