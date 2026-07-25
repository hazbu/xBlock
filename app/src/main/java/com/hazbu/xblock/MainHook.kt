package com.hazbu.xblock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.net.NetworkCapabilities
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
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.InetAddress

class MainHook : XposedModule() {

    private val modulePackage = "com.hazbu.xblock"
    private var dynamicDomains = mutableSetOf<String>()

    private val adPackages = listOf(
        "com.google.android.gms.ads",
        "com.google.unity.ads",
        "com.applovin",
        "com.mbridge.msdk",
        "com.facebook.ads",
        "com.unity3d.ads",
        "com.unity3d.services",
        "com.vungle",
        "com.ironsource",
        "com.adcolony",
        "com.chartboost",
        "com.fyber",
        "com.inmobi",
        "com.smaato",
        "com.tradplus"
    )

    private val voidKillMethods = listOf(
        "loadAd", "loadAds", "load", "show", "fetchAd", "initSDK", "initialize", "initializeSdk"
    )

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        
        if (param.packageName == modulePackage) return

        Log.d("XBlock", "Hooking ${param.packageName} via libxposed V4")

        val classLoader = param.classLoader
        hookApplication(classLoader)
        hookDns(classLoader)
        hookDeepDns(classLoader)
        hookOkHttp(classLoader)
        hookCronet(classLoader)
        hookUi(classLoader)
        hookWebView(classLoader)
        hookGameAds(classLoader)
        hookIntents(classLoader)
        hookSensors(classLoader)
        hookStealth(classLoader)
        hookStealthVPN(classLoader)
        hookAdMobIdentity(classLoader)
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
                Log.d("XBlock", "Successfully fetched ${dynamicDomains.size} domains")

                if (dynamicDomains.isNotEmpty()) {
                    showToast(context, "XBlock Active")
                } else {
                    showToast(context, "XBlock: Please open app to update filters")
                }
            }
        } catch (e: Exception) {
            Log.e("XBlock", "Failed to fetch domains: ${e.message}")
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
                    Log.d("XBlock", "DNS blocking: $host")
                    sinkholeAddress
                } else {
                    chain.proceed()
                }
            }

            hook(getAllByNameMethod).intercept { chain ->
                val host = chain.args[0] as? String
                if (host != null && isAdDomain(host)) {
                    Log.d("XBlock", "DNS (all) blocking: $host")
                    arrayOf(sinkholeAddress)
                } else {
                    chain.proceed()
                }
            }
        } catch (e: Exception) {
            Log.e("XBlock", "DNS Hook Error: ${e.message}")
        }
    }

    private fun hookDeepDns(classLoader: ClassLoader) {
        try {
            val dnsResolverClass = classLoader.loadClass("android.net.DnsResolver")
            val callbackClass = classLoader.loadClass("android.net.DnsResolver\$Callback")
            val dnsExceptionClass = classLoader.loadClass("android.net.DnsResolver\$DnsException")
            val dnsExceptionConstructor = dnsExceptionClass.getDeclaredConstructor(Int::class.java, Throwable::class.java)

            val queryMethods = dnsResolverClass.declaredMethods.filter { it.name == "query" || it.name == "rawQuery" }

            for (method in queryMethods) {
                hook(method).intercept { chain ->
                    val domain = chain.args.find { it is String } as? String
                    if (domain != null && isAdDomain(domain)) {
                        Log.d("XBlock", "Deep DNS blocking: $domain")
                        val callback = chain.args.find { callbackClass.isInstance(it) }
                        if (callback != null) {
                            val error = dnsExceptionConstructor.newInstance(1, null) // ERROR_NAME_NOT_FOUND = 1
                            val onErrorMethod = callbackClass.getDeclaredMethod("onError", dnsExceptionClass)
                            onErrorMethod.invoke(callback, error)
                        }
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("XBlock", "Deep DNS Hook Error: ${e.message}")
        }
    }

    private fun hookOkHttp(classLoader: ClassLoader) {
        try {
            val builderClass = classLoader.loadClass("okhttp3.OkHttpClient\$Builder")
            val dnsInterface = classLoader.loadClass("okhttp3.Dns")
            val dnsMethod = builderClass.getDeclaredMethod("dns", dnsInterface)
            val systemDns = dnsInterface.getDeclaredField("SYSTEM").get(null)

            hook(dnsMethod).intercept { chain ->
                Log.d("XBlock", "OkHttp DNS: Forcing system DNS")
                chain.args[0] = systemDns
                chain.proceed()
            }
        } catch (ignored: Exception) {}
    }

    private fun hookCronet(classLoader: ClassLoader) {
        try {
            val builderClass = classLoader.loadClass("org.chromium.net.CronetEngine\$Builder")
            val method = builderClass.declaredMethods.find { it.name == "setUseBuiltInDnsResolver" }
            if (method != null) {
                hook(method).intercept { chain ->
                    Log.d("XBlock", "Cronet DNS: Disabling built-in resolver")
                    chain.args[0] = false
                    chain.proceed()
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun hookUi(classLoader: ClassLoader) {
        try {
            val viewGroupClass = classLoader.loadClass("android.view.ViewGroup")
            val addViewMethod = viewGroupClass.getDeclaredMethod("addView", View::class.java, ViewGroup.LayoutParams::class.java)
            
            hook(addViewMethod).intercept { chain ->
                val view = chain.args[0] as? View
                if (view != null && AdBlockUtils.isAdView(view.javaClass.name)) {
                    Log.d("XBlock", "UI blocking: Hiding ${view.javaClass.name}")
                    recursiveHide(view)
                }
                chain.proceed()
            }
        } catch (e: Exception) {
            Log.e("XBlock", "UI Hook Error: ${e.message}")
        }
    }

    private fun recursiveHide(view: View) {
        view.visibility = View.GONE
        view.layoutParams?.let {
            it.width = 0
            it.height = 0
        }
        
        var current = view.parent as? ViewGroup
        while (current != null) {
            var visibleChildren = 0
            for (i in 0 until current.childCount) {
                val child = current.getChildAt(i)
                if (child != view && child.visibility == View.VISIBLE) {
                    visibleChildren++
                }
            }
            
            if (visibleChildren == 0) {
                Log.d("XBlock", "Recursive UI: Hiding parent ${current.javaClass.name}")
                current.visibility = View.GONE
                current = current.parent as? ViewGroup
            } else {
                break
            }
        }
    }

    private fun hookWebView(classLoader: ClassLoader) {
        try {
            val webViewClientClass = classLoader.loadClass("android.webkit.WebViewClient")
            
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
            // Aggressive Void Killing for ad SDKs
            for (pkg in adPackages) {
                try {
                    // This is a generic approach to find and block methods by name in ad packages
                    // We target methods that return void and are common ad triggers
                    val classes = listOf("Ads", "AdMob", "UnityAds", "AppLovin", "Vungle", "IronSource")
                    for (clsName in classes) {
                        try {
                            val cls = classLoader.loadClass("$pkg.$clsName")
                            for (method in cls.declaredMethods) {
                                if (voidKillMethods.contains(method.name) && method.returnType == Void.TYPE) {
                                    hook(method).intercept {
                                        Log.d("XBlock", "Void-killed: ${cls.name}.${method.name}")
                                        null
                                    }
                                }
                            }
                        } catch (ignored: Exception) {}
                    }
                } catch (ignored: Exception) {}
            }

            // Specific Unity Hooks
            try {
                val unityAdsClass = classLoader.loadClass("com.unity3d.ads.UnityAds")
                unityAdsClass.methods.find { it.name == "initialize" }?.let { method ->
                    hook(method).intercept { chain ->
                        Log.d("XBlock", "UnityAds: Forcing Test Mode")
                        val args = chain.args.toMutableList()
                        for (i in args.indices) {
                            if (args[i] is Boolean) args[i] = true
                        }
                        chain.proceed() 
                    }
                }
                unityAdsClass.methods.find { it.name == "show" }?.let { method ->
                    hook(method).intercept {
                        Log.d("XBlock", "UnityAds: Blocked show() call")
                        null 
                    }
                }
            } catch (ignored: Exception) {}

            // Specific AdMob AdActivity Auto-close
            try {
                val adActivityClass = classLoader.loadClass("com.google.android.gms.ads.AdActivity")
                adActivityClass.getDeclaredMethod("onCreate", Bundle::class.java).let { method ->
                    hook(method).intercept { chain ->
                        val activity = chain.thisObject as Activity
                        Log.d("XBlock", "AdMob: Auto-closing AdActivity")
                        activity.finish()
                        chain.proceed()
                    }
                }
            } catch (ignored: Exception) {}

        } catch (e: Exception) {
            Log.e("XBlock", "GameAds Hook Error: ${e.message}")
        }
    }

    private fun hookIntents(classLoader: ClassLoader) {
        try {
            val contextClass = classLoader.loadClass("android.content.Context")
            val startActivityMethod = contextClass.getDeclaredMethod("startActivity", Intent::class.java)
            
            hook(startActivityMethod).intercept { chain ->
                val intent = chain.args[0] as? Intent
                if (intent != null && isAdIntent(intent)) {
                    Log.d("XBlock", "Intent blocking: Redirect prevented")
                    null 
                } else {
                    chain.proceed()
                }
            }
        } catch (e: Exception) {
            Log.e("XBlock", "Intent Hook Error: ${e.message}")
        }
    }

    private fun hookSensors(classLoader: ClassLoader) {
        try {
            val sensorManagerClass = classLoader.loadClass("android.hardware.SensorManager")
            val registerMethod = sensorManagerClass.getDeclaredMethod("registerListener", 
                SensorEventListener::class.java, Sensor::class.java, Int::class.javaPrimitiveType)
            
            hook(registerMethod).intercept { chain ->
                val sensor = chain.args[1] as? Sensor
                if (sensor != null && (sensor.type == Sensor.TYPE_ACCELEROMETER || sensor.type == Sensor.TYPE_GYROSCOPE)) {
                    Log.d("XBlock", "Sensors: Blocking registerListener for movement ads")
                    return@intercept false 
                }
                chain.proceed()
            }
        } catch (e: Exception) {
            Log.e("XBlock", "Sensor Hook Error: ${e.message}")
        }
    }

    private fun hookStealth(classLoader: ClassLoader) {
        try {
            val fieldClass = classLoader.loadClass("java.lang.reflect.Field")
            val getMethod = fieldClass.getDeclaredMethod("get", Any::class.java)
            
            hook(getMethod).intercept { chain ->
                val field = chain.thisObject as? Field
                if (field != null) {
                    val name = field.name
                    if (name == "disableHooks" || name == "sHookedMethodCallbacks") {
                        Log.d("XBlock", "Stealth: Hiding Xposed field $name")
                        throw NoSuchFieldException(name)
                    }
                }
                chain.proceed()
            }
        } catch (ignored: Exception) {}
    }

    private fun hookStealthVPN(classLoader: ClassLoader) {
        try {
            val ncClass = classLoader.loadClass("android.net.NetworkCapabilities")
            val hasCapabilityMethod = ncClass.getDeclaredMethod("hasTransport", Int::class.java)
            
            hook(hasCapabilityMethod).intercept { chain ->
                val transport = chain.args[0] as? Int
                if (transport == NetworkCapabilities.TRANSPORT_VPN) {
                    Log.d("XBlock", "Stealth: Hiding VPN status")
                    return@intercept false
                }
                chain.proceed()
            }
        } catch (ignored: Exception) {}
    }

    private fun hookAdMobIdentity(classLoader: ClassLoader) {
        try {
            val bundleClass = classLoader.loadClass("android.os.BaseBundle")
            val getMethod = bundleClass.getDeclaredMethod("get", String::class.java)
            
            hook(getMethod).intercept { chain ->
                val key = chain.args[0] as? String
                if (key == "com.google.android.gms.ads.APPLICATION_ID") {
                    Log.d("XBlock", "Stealth: Spoofing AdMob Application ID")
                    return@intercept "ca-app-pub-0000000000000000~0000000000"
                }
                chain.proceed()
            }
        } catch (ignored: Exception) {}
    }

    private fun isAdDomain(host: String): Boolean {
        return dynamicDomains.any { host.contains(it, ignoreCase = true) }
    }

    private fun isAdIntent(intent: Intent): Boolean {
        val data = try { intent.dataString?.lowercase() ?: "" } catch (ignored: Exception) { "" }
        
        return data.contains("googleads") || 
               data.contains("doubleclick") ||
               data.contains("adservice") ||
               data.contains("pagead") ||
               data.contains("play.google.com/store/apps/details?id=") ||
               data.contains("market://details?id=") ||
               data.contains("ads")
    }
}
