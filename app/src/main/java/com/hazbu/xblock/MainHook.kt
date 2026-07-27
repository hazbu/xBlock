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
import java.io.IOException
import java.lang.reflect.Field
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress

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
        "com.tradplus",
        "com.bytedance.sdk.openadsdk",
        "com.anythink",
        "com.pangle.global",
        "com.moloco.sdk",
        "com.mintegral.msdk",
        "com.google.android.gms.ads.admanager"
    )

    private val systemSkipList = listOf(
        "android",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.providers.downloads",
        "com.google.android.apps.docs",
        "com.google.android.webview",
        "com.google.android.syncadapters.contacts",
        "com.google.android.finsky",
        "com.google.android.play.games"
    )

    private val voidKillMethods = listOf(
        "loadAd", "loadAds", "load", "show", "fetchAd", "initSDK", "initialize", "initializeSdk", "init", "start", "showAd", "loadInterstitial", "loadRewardedAd", "startAutoRefresh"
    )

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        
        if (param.packageName == modulePackage) return
        
        // Safety: Never hook critical system processes
        if (systemSkipList.contains(param.packageName)) {
            Log.d("XBlock", "Skipping critical system package: ${param.packageName}")
            return
        }

        Log.d("XBlock", "Hooking ${param.packageName} via libxposed V7.0 (API 102 Hot-Reload)")

        val classLoader = param.classLoader
        hookApplication(classLoader)
        hookDns(classLoader)
        hookDeepDns(classLoader)
        hookSocket(classLoader)
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
        hookNuclear(classLoader)
        hookEconomic(classLoader)
        hookFlickerPro(classLoader)
        hookAutoReward(classLoader)
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        Log.d("XBlock", "Hot reloading: Saving domain state...")
        val state = Bundle().apply {
            putStringArrayList("domains", ArrayList(dynamicDomains))
        }
        param.setSavedInstanceState(state)
        return true 
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        super.onHotReloaded(param)
        Log.d("XBlock", "Hot reloaded: Restoring state...")
        val savedInstanceState = param.savedInstanceState
        if (savedInstanceState is Bundle) {
            val savedDomains = savedInstanceState.getStringArrayList("domains")
            if (savedDomains != null) {
                dynamicDomains.clear()
                dynamicDomains.addAll(savedDomains)
                Log.d("XBlock", "Restored ${dynamicDomains.size} domains after reload")
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
                    showToast(context, "XBlock Active (Hot-Reload Mode)")
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

    private fun hookSocket(classLoader: ClassLoader) {
        try {
            val socketClass = classLoader.loadClass("java.net.Socket")
            val connectMethod = socketClass.getDeclaredMethod("connect", SocketAddress::class.java, Int::class.javaPrimitiveType)
            
            hook(connectMethod).intercept { chain ->
                val address = chain.args[0] as? InetSocketAddress
                if (address != null) {
                    val host = address.hostString 
                    if (isAdDomain(host)) {
                        Log.d("XBlock", "Socket blocking (Silent): $host")
                        return@intercept null 
                    }
                }
                chain.proceed()
            }
        } catch (e: Exception) {
            Log.e("XBlock", "Socket Hook Error: ${e.message}")
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

            // Ghost Mode: Constructor kill
            val ghostClasses = listOf(
                "com.google.android.gms.ads.nativead.NativeAdView",
                "com.applovin.mediation.ads.MaxAdView",
                "com.applovin.adview.AppLovinAdView",
                "com.google.android.gms.ads.admanager.AdManagerAdView"
            )
            for (clsName in ghostClasses) {
                try {
                    val cls = classLoader.loadClass(clsName)
                    cls.getDeclaredConstructors().forEach { constructor ->
                        hook(constructor).intercept { chain ->
                            val view = chain.thisObject as View
                            view.visibility = View.GONE
                            view.alpha = 0f
                            Log.d("XBlock", "Ghost Mode: Constructor-level kill for $clsName")
                            chain.proceed()
                        }
                    }
                } catch (ignored: Exception) {}
            }

        } catch (e: Exception) {
            Log.e("XBlock", "UI Hook Error: ${e.message}")
        }
    }

    private fun recursiveHide(view: View) {
        view.visibility = View.GONE
        view.alpha = 0f
        view.scaleX = 0f
        view.scaleY = 0f
        view.translationX = 9999f
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
                Log.d("XBlock", "Ghost Mode: Collapsing parent ${current.javaClass.name}")
                current.visibility = View.GONE
                current.layoutParams?.let { it.width = 0; it.height = 0 }
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
            for (pkg in adPackages) {
                try {
                    val classes = listOf("Ads", "AdMob", "UnityAds", "AppLovin", "Vungle", "IronSource", "ATSDK", "TTAdSdk", "PAGSdk", "Moloco", "MIntegralSDK")
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

            // Stable AdMob load block
            try {
                val interstitialAdClass = classLoader.loadClass("com.google.android.gms.ads.interstitial.InterstitialAd")
                interstitialAdClass.declaredMethods.filter { it.name == "load" }.forEach { method ->
                    hook(method).intercept {
                        Log.d("XBlock", "AdMob: Blocked Interstitial load()")
                        null 
                    }
                }

                val rewardedAdClass = classLoader.loadClass("com.google.android.gms.ads.rewarded.RewardedAd")
                rewardedAdClass.declaredMethods.filter { it.name == "load" }.forEach { method ->
                    hook(method).intercept {
                        Log.d("XBlock", "AdMob: Blocked Rewarded load()")
                        null
                    }
                }

                val adManagerInterClass = classLoader.loadClass("com.google.android.gms.ads.admanager.AdManagerInterstitialAd")
                adManagerInterClass.declaredMethods.filter { it.name == "load" }.forEach { method ->
                    hook(method).intercept {
                        Log.d("XBlock", "AdManager: Blocked Interstitial load()")
                        null
                    }
                }
            } catch (ignored: Exception) {}

            try {
                val bridgeClass = classLoader.loadClass("com.applovin.mediation.unity.MaxUnityAdManager")
                bridgeClass.declaredMethods.forEach { method ->
                    if (method.name.startsWith("load") || method.name.startsWith("show")) {
                        hook(method).intercept {
                            Log.d("XBlock", "AppLovin Bridge Blocked: ${method.name}()")
                            null
                        }
                    }
                }
            } catch (ignored: Exception) {}

        } catch (e: Exception) {
            Log.e("XBlock", "GameAds Hook Error: ${e.message}")
        }
    }

    private fun hookIntents(classLoader: ClassLoader) {
        try {
            val contextImplClass = classLoader.loadClass("android.app.ContextImpl")
            val startActivityMethod = contextImplClass.getDeclaredMethod("startActivity", Intent::class.java)
            
            hook(startActivityMethod).intercept { chain ->
                val intent = chain.args[0] as? Intent
                if (intent != null && isAdIntent(intent)) {
                    Log.d("XBlock", "Intent blocking: Redirect prevented")
                    null 
                } else {
                    chain.proceed()
                }
            }
            
            try {
                val instrClass = classLoader.loadClass("android.app.Instrumentation")
                val execStartMethod = instrClass.declaredMethods.find { it.name == "execStartActivity" }
                if (execStartMethod != null) {
                    hook(execStartMethod).intercept { chain ->
                        val intent = chain.args.find { it is Intent } as? Intent
                        if (intent != null && isAdIntent(intent)) {
                            Log.d("XBlock", "Instrumentation blocking: Ad Activity prevented")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }
            } catch (ignored: Exception) {}

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
                if (key == "com.google.android.gms.ads.APPLICATION_ID" || 
                    key == "bu_app_id" || 
                    key == "anythink_app_id") {
                    return@intercept "ca-app-pub-0000000000000000~0000000000"
                }
                chain.proceed()
            }
        } catch (ignored: Exception) {}
    }

    private fun hookNuclear(classLoader: ClassLoader) {
        val activities = listOf(
            "com.google.android.gms.ads.AdActivity",
            "com.applovin.adview.AppLovinFullscreenActivity",
            "com.applovin.sdk.AppLovinWebViewActivity"
        )
        for (actName in activities) {
            try {
                val cls = classLoader.loadClass(actName)
                cls.getDeclaredMethod("onCreate", Bundle::class.java).let { method ->
                    hook(method).intercept { chain ->
                        val activity = chain.thisObject as Activity
                        Log.d("XBlock", "Nuclear: Killing ad activity ${activity.javaClass.name}")
                        activity.finish()
                        null
                    }
                }
            } catch (ignored: Exception) {}
        }

        // Deep AdMob Overlay kill
        try {
            val parcelClass = classLoader.loadClass("com.google.android.gms.ads.internal.overlay.AdOverlayInfoParcel")
            parcelClass.getDeclaredConstructors().forEach { constructor ->
                hook(constructor).intercept { chain ->
                    Log.d("XBlock", "Nuclear: Nullifying AdOverlayInfoParcel")
                    chain.proceed()
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun hookEconomic(classLoader: ClassLoader) {
        try {
            // Disable Revenue reporting
            val listeners = listOf(
                "com.google.android.gms.ads.interstitial.InterstitialAd" to "setOnPaidEventListener",
                "com.google.android.gms.ads.rewarded.RewardedAd" to "setOnPaidEventListener",
                "com.applovin.mediation.ads.MaxAdView" to "setRevenueListener",
                "com.applovin.mediation.ads.MaxInterstitialAd" to "setRevenueListener",
                "com.applovin.mediation.ads.MaxRewardedAd" to "setRevenueListener"
            )

            for ((clsName, methodName) in listeners) {
                try {
                    val cls = classLoader.loadClass(clsName)
                    cls.declaredMethods.find { it.name == methodName }?.let { method ->
                        hook(method).intercept { chain ->
                            Log.d("XBlock", "Economic: Blocked listener registration for $clsName")
                            chain.args[0] = null
                            chain.proceed()
                        }
                    }
                } catch (ignored: Exception) {}
            }

            // Zero out reported values
            try {
                val adValueClass = classLoader.loadClass("com.google.android.gms.ads.AdValue")
                adValueClass.getDeclaredMethod("getValueMicros").let { method ->
                    hook(method).intercept { 0L }
                }
            } catch (ignored: Exception) {}

        } catch (ignored: Exception) {}
    }

    private fun hookFlickerPro(classLoader: ClassLoader) {
        try {
            val adViewClasses = listOf(
                "com.google.android.gms.ads.BaseAdView",
                "com.google.android.gms.ads.nativead.NativeAdView",
                "com.applovin.mediation.ads.MaxAdView",
                "com.applovin.adview.AppLovinAdView",
                "com.google.android.gms.ads.admanager.AdManagerAdView"
            )
            for (clsName in adViewClasses) {
                try {
                    val cls = classLoader.loadClass(clsName)
                    
                    // Force visibility to stay GONE
                    cls.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType).let { method ->
                        hook(method).intercept { chain ->
                            chain.args[0] = View.GONE
                            chain.proceed()
                        }
                    }

                    // Absolute Zero: Force size, alpha, and scale to 0
                    cls.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).let { method ->
                        hook(method).intercept { chain ->
                            val view = chain.thisObject as View
                            view.alpha = 0f
                            view.scaleX = 0f
                            view.scaleY = 0f
                            val setMeasuredDimension = View::class.java.getDeclaredMethod("setMeasuredDimension", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            setMeasuredDimension.isAccessible = true
                            setMeasuredDimension.invoke(chain.thisObject, 0, 0)
                            null
                        }
                    }
                } catch (ignored: Exception) {}
            }
        } catch (ignored: Exception) {}
    }

    private fun hookAutoReward(classLoader: ClassLoader) {
        try {
            // AdMob Rewarded Insta-Grant
            val adMobRewarded = classLoader.loadClass("com.google.android.gms.ads.rewarded.RewardedAd")
            adMobRewarded.declaredMethods.find { it.name == "show" }?.let { method ->
                hook(method).intercept { chain ->
                    Log.d("XBlock", "Auto-Reward: Triggering AdMob Reward")
                    val listener = chain.args.find { it.javaClass.name.contains("OnUserEarnedRewardListener") }
                    if (listener != null) {
                        try {
                            val onUserEarnedReward = listener.javaClass.methods.find { it.name == "onUserEarnedReward" }
                            onUserEarnedReward?.invoke(listener, null)
                        } catch (e: Exception) {
                            Log.e("XBlock", "Failed to trigger AdMob reward: ${e.message}")
                        }
                    }
                    null 
                }
            }

            // AppLovin MAX Rewarded Insta-Grant
            val maxRewarded = classLoader.loadClass("com.applovin.mediation.ads.MaxRewardedAd")
            maxRewarded.declaredMethods.find { it.name == "showAd" }?.let { method ->
                hook(method).intercept { chain ->
                    Log.d("XBlock", "Auto-Reward: Triggering AppLovin Reward")
                    try {
                        val listenerField = chain.thisObject.javaClass.getDeclaredFields().find { it.type.name.contains("MaxAdRewardedListener") || it.name == "listener" }
                        listenerField?.isAccessible = true
                        val listener = listenerField?.get(chain.thisObject)
                        if (listener != null) {
                            val onAdRewarded = listener.javaClass.methods.find { it.name == "onAdRewarded" }
                            onAdRewarded?.invoke(listener, null, null)
                        }
                    } catch (e: Exception) {
                        Log.e("XBlock", "Failed to trigger AppLovin reward: ${e.message}")
                    }
                    null
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun isAdDomain(host: String): Boolean {
        val hostLower = host.lowercase()
        
        // Whitelist attribution & essential content domains
        if (hostLower.contains("googleusercontent.com") || 
            hostLower.contains("play.google.com") || 
            hostLower.contains("play.googleapis.com") ||
            hostLower.contains("dl.google.com") ||
            hostLower.contains("googleapis.com") ||
            hostLower.contains("gstatic.com") ||
            hostLower.contains("android.com") ||
            hostLower.contains("adjust.com") ||
            hostLower.contains("appsflyer.com")) {
            return false
        }
        
        return dynamicDomains.any { host.contains(it, ignoreCase = true) } ||
               host.contains("rayjump.com", ignoreCase = true) ||
               host.contains("mintegral.net", ignoreCase = true) ||
               host.contains("maxesads.com", ignoreCase = true) ||
               host.contains("sonicsads.com", ignoreCase = true) ||
               host.contains("news-cdn.site", ignoreCase = true)
    }

    private fun isAdIntent(intent: Intent): Boolean {
        val data = try { intent.dataString?.lowercase() ?: "" } catch (ignored: Exception) { "" }
        val component = intent.component?.className?.lowercase() ?: ""
        
        // Safety: Never block play store intents required for ownership check
        if (data.contains("play.google.com/store/apps/details?id=") && 
            intent.component?.packageName == "com.android.vending") {
            return false
        }

        return data.contains("googleads") || 
               data.contains("doubleclick") ||
               data.contains("adservice") ||
               data.contains("pagead") ||
               data.contains("googleadservices") ||
               data.contains("ads") ||
               component.contains("adactivity") ||
               component.contains("adunitactivity") ||
               component.contains("applovin") ||
               component.contains("vungle") ||
               component.contains("ironsource")
    }
}
