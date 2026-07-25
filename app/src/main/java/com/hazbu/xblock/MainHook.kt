package com.hazbu.xblock

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.ByteArrayInputStream
import java.net.InetAddress

class MainHook : IXposedHookLoadPackage {

    private val modulePackage = "com.hazbu.xblock"
    private val prefs = XSharedPreferences(modulePackage, AdBlockUtils.PREFS_NAME)
    private var dynamicDomains = emptySet<String>()

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == modulePackage) return

        XposedBridge.log("XBlock: Hooking ${lpparam.packageName}")

        hookApplication()
        hookDns()
        hookUi()
        hookWebView(lpparam)
    }

    private fun hookApplication() {
        XposedHelpers.findAndHookMethod(
            Application::class.java, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.thisObject as Context
                    
                    prefs.makeWorldReadable()
                    val loaded = prefs.reload()
                    dynamicDomains = prefs.getStringSet(AdBlockUtils.KEY_DOMAINS, emptySet()) ?: emptySet()
                    
                    XposedBridge.log("XBlock Hook: Loaded=${dynamicDomains.size}, ReloadStatus=$loaded")

                    if (dynamicDomains.isEmpty()) {
                        showToast(context, "XBlock: Please open app to update filters")
                    } else {
                        showToast(context, "XBlock Active")
                    }
                }
            }
        )
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hookDns() {
        try {
            val sinkholeAddress = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))

            XposedHelpers.findAndHookMethod(
                InetAddress::class.java, "getByName", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val host = param.args[0] as? String ?: return
                        if (isAdDomain(host)) {
                            param.result = sinkholeAddress
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                InetAddress::class.java, "getAllByName", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val host = param.args[0] as? String ?: return
                        if (isAdDomain(host)) {
                            param.result = arrayOf(sinkholeAddress)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("XBlock: DNS Hook Error: ${e.message}")
        }
    }

    private fun hookUi() {
        try {
            XposedHelpers.findAndHookMethod(
                ViewGroup::class.java, "addView", View::class.java, ViewGroup.LayoutParams::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.args[0] as? View ?: return
                        if (AdBlockUtils.isAdView(view.javaClass.name)) {
                            view.visibility = View.GONE
                            val lp = param.args[1] as? ViewGroup.LayoutParams
                            if (lp != null) {
                                lp.width = 0
                                lp.height = 0
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("XBlock: UI Hook Error: ${e.message}")
        }
    }

    private fun hookWebView(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.webkit.WebViewClient", lpparam.classLoader,
                "shouldInterceptRequest", "android.webkit.WebView", "android.webkit.WebResourceRequest",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val request = param.args[1] as? WebResourceRequest ?: return
                        val host = request.url?.host ?: return

                        if (isAdDomain(host)) {
                            XposedBridge.log("XBlock: WebView blocking $host")
                            param.result = WebResourceResponse(
                                "text/plain", "UTF-8", ByteArrayInputStream("".toByteArray())
                            )
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                "android.webkit.WebViewClient", lpparam.classLoader,
                "shouldInterceptRequest", "android.webkit.WebView", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val urlString = param.args[1] as? String ?: return
                        val host = Uri.parse(urlString)?.host ?: return

                        if (isAdDomain(host)) {
                            XposedBridge.log("XBlock: WebView (legacy) blocking $host")
                            param.result = WebResourceResponse(
                                "text/plain", "UTF-8", ByteArrayInputStream("".toByteArray())
                            )
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("XBlock: WebView Hook Error: ${e.message}")
        }
    }

    private fun isAdDomain(host: String): Boolean {
        return dynamicDomains.any { host.contains(it, ignoreCase = true) }
    }
}
