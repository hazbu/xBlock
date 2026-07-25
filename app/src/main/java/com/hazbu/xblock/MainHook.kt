package com.hazbu.xblock

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.Toast
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.io.ByteArrayInputStream
import java.net.InetAddress

class MainHook : XposedModule() {

    private val modulePackage = "com.hazbu.xblock"
    private var dynamicDomains = mutableSetOf<String>()

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        
        if (param.packageName == modulePackage) return

        Log.d("XBlock", "Hooking ${param.packageName} via libxposed")

        hookApplication(param.classLoader)
        hookDns(param.classLoader)
        hookUi(param.classLoader)
        hookWebView(param.classLoader)
        hookGameAds(param.classLoader)
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        Log.d("XBlock", "Hot reloading: Saving state...")
        val state = Bundle().apply {
            putStringArrayList("domains", ArrayList(dynamicDomains))
        }
        param.setSavedInstanceState(state)
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        super.onHotReloaded(param)
        Log.d("XBlock", "Hot reloading: Restoring state...")
        val savedInstanceState = param.savedInstanceState
        if (savedInstanceState is Bundle) {
            val savedDomains = savedInstanceState.getStringArrayList("domains")
            if (savedDomains != null) {
                dynamicDomains.clear()
                dynamicDomains.addAll(savedDomains)
                Log.d("XBlock", "Recovered ${dynamicDomains.size} domains")
            }
        }
    }

    private fun hookApplication(classLoader: ClassLoader) {
        try {
            val appClass = classLoader.loadClass("android.app.Application")
            val onCreateMethod = appClass.getDeclaredMethod("onCreate")
            
            hook(onCreateMethod).intercept { chain ->
                val result = chain.proceed()
                val context = chain.thisObject as Context
                
                // Fetch domains from ContentProvider in background
                Thread {
                    fetchDomainsFromProvider(context)
                }.start()

                result
            }
        } catch (e: Exception) {
            Log.e("XBlock", "Application Hook Error: ${e.message}")
        }
    }

    private fun fetchDomainsFromProvider(context: Context) {
        try {
            Log.d("XBlock", "Fetching domains from provider...")
            val uri = Uri.parse("content://com.hazbu.xblock.provider/domains")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            
            if (cursor != null) {
                val tempSet = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    val domain = cursor.getString(0)
                    tempSet.add(domain)
                }
                cursor.close()
                
                dynamicDomains.clear()
                dynamicDomains.addAll(tempSet)
                Log.d("XBlock", "Successfully fetched ${dynamicDomains.size} domains from provider")

                if (dynamicDomains.isNotEmpty()) {
                    showToast(context, "XBlock Active")
                } else {
                    showToast(context, "XBlock: Please open app to update filters")
                }
            } else {
                Log.e("XBlock", "Cursor is null, provider might be inaccessible")
                showToast(context, "XBlock: Provider error, check settings")
            }
        } catch (e: Exception) {
            Log.e("XBlock", "Failed to fetch domains: ${e.message}")
            showToast(context, "XBlock: IPC Error")
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hookDns(classLoader: ClassLoader) {
        try {
            val inetAddressClass = classLoader.loadClass("java.net.InetAddress")
            val getByNameMethod = inetAddressClass.getDeclaredMethod("getByName", String::class.java)
            val getAllByNameMethod = inetAddressClass.getDeclaredMethod("getAllByName", String::class.java)
            
            val sinkholeAddress = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))

            hook(getByNameMethod).intercept { chain ->
                val host = chain.args[0] as? String
                if (host != null && isAdDomain(host)) {
                    sinkholeAddress
                } else {
                    chain.proceed()
                }
            }

            hook(getAllByNameMethod).intercept { chain ->
                val host = chain.args[0] as? String
                if (host != null && isAdDomain(host)) {
                    arrayOf(sinkholeAddress)
                } else {
                    chain.proceed()
                }
            }
        } catch (e: Exception) {
            Log.e("XBlock", "DNS Hook Error: ${e.message}")
        }
    }

    private fun hookUi(classLoader: ClassLoader) {
        try {
            val viewGroupClass = classLoader.loadClass("android.view.ViewGroup")
            val addViewMethod = viewGroupClass.getDeclaredMethod("addView", View::class.java, ViewGroup.LayoutParams::class.java)
            
            hook(addViewMethod).intercept { chain ->
                val view = chain.args[0] as? View
                if (view != null && AdBlockUtils.isAdView(view.javaClass.name)) {
                    view.visibility = View.GONE
                    val lp = chain.args[1] as? ViewGroup.LayoutParams
                    if (lp != null) {
                        lp.width = 0
                        lp.height = 0
                    }
                }
                chain.proceed()
            }
        } catch (e: Exception) {
            Log.e("XBlock", "UI Hook Error: ${e.message}")
        }
    }

    private fun hookWebView(classLoader: ClassLoader) {
        try {
            val webViewClientClass = classLoader.loadClass("android.webkit.WebViewClient")
            
            // Modern shouldInterceptRequest
            try {
                val modernMethod = webViewClientClass.getDeclaredMethod("shouldInterceptRequest", 
                    classLoader.loadClass("android.webkit.WebView"), 
                    classLoader.loadClass("android.webkit.WebResourceRequest"))
                
                hook(modernMethod).intercept { chain ->
                    val request = chain.args[1] as? WebResourceRequest
                    val host = request?.url?.host
                    if (host != null && isAdDomain(host)) {
                        Log.d("XBlock", "WebView blocking $host")
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                    } else {
                        chain.proceed()
                    }
                }
            } catch (ignored: NoSuchMethodException) {}

            // Legacy shouldInterceptRequest
            try {
                val legacyMethod = webViewClientClass.getDeclaredMethod("shouldInterceptRequest", 
                    classLoader.loadClass("android.webkit.WebView"), 
                    String::class.java)
                
                hook(legacyMethod).intercept { chain ->
                    val urlString = chain.args[1] as? String
                    val host = if (urlString != null) Uri.parse(urlString).host else null
                    if (host != null && isAdDomain(host)) {
                        Log.d("XBlock", "WebView (legacy) blocking $host")
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                    } else {
                        chain.proceed()
                    }
                }
            } catch (ignored: NoSuchMethodException) {}

        } catch (e: Exception) {
            Log.e("XBlock", "WebView Hook Error: ${e.message}")
        }
    }

    private fun hookGameAds(classLoader: ClassLoader) {
        try {
            // 1. Unity Ads Initialization - Force Test Mode
            try {
                val unityAdsClass = classLoader.loadClass("com.unity3d.ads.UnityAds")
                val initMethod = unityAdsClass.methods.find { it.name == "initialize" }
                if (initMethod != null) {
                    hook(initMethod).intercept { chain ->
                        Log.d("XBlock", "UnityAds: Forcing Test Mode")
                        // Many signatures exist, we find the boolean parameter for testMode
                        val args = chain.args.toMutableList()
                        for (i in args.indices) {
                            if (args[i] is Boolean) {
                                args[i] = true // Set testMode = true
                            }
                        }
                        // Reassign args and proceed
                        // Note: In libxposed, we might need to call invoker or just modify chain
                        chain.proceed() 
                    }
                }
            } catch (ignored: ClassNotFoundException) {}

            // 2. Unity Ads Show - Prevent displaying
            try {
                val unityAdsClass = classLoader.loadClass("com.unity3d.ads.UnityAds")
                val showMethod = unityAdsClass.methods.find { it.name == "show" }
                if (showMethod != null) {
                    hook(showMethod).intercept {
                        Log.d("XBlock", "UnityAds: Blocked show() call")
                        null // Prevent showing
                    }
                }
            } catch (ignored: ClassNotFoundException) {}

            // 3. AdUnitActivity - Auto-close full-screen activities
            try {
                val adUnitClass = classLoader.loadClass("com.unity3d.services.ads.adunit.AdUnitActivity")
                val onCreateMethod = adUnitClass.getDeclaredMethod("onCreate", Bundle::class.java)
                hook(onCreateMethod).intercept { chain ->
                    val activity = chain.thisObject as android.app.Activity
                    Log.d("XBlock", "UnityAds: Closing AdUnitActivity instantly")
                    activity.finish()
                    chain.proceed()
                }
            } catch (ignored: Exception) {}

            // 4. AppLovin - Block initialization or show
            try {
                val alClass = classLoader.loadClass("com.applovin.sdk.AppLovinSdk")
                val initMethod = alClass.methods.find { it.name == "initializeSdk" }
                if (initMethod != null) {
                    hook(initMethod).intercept {
                        Log.d("XBlock", "AppLovin: Blocked initialization")
                        null
                    }
                }
            } catch (ignored: Exception) {}

        } catch (e: Exception) {
            Log.e("XBlock", "GameAds Hook Error: ${e.message}")
        }
    }

    private fun isAdDomain(host: String): Boolean {
        return dynamicDomains.any { host.contains(it, ignoreCase = true) }
    }
}
