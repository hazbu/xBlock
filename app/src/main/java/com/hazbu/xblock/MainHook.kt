package com.hazbu.xblock
import com.hazbu.xblock.Constants.AUTHORITY
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import androidx.core.view.isVisible
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
class MainHook : XposedModule() {
    @Volatile
    private var dynamicDomains: Set<String> = emptySet()
    @Volatile
    private var dynamicPackages: Set<String> = emptySet()
    private val classCheckCache = ConcurrentHashMap<Class<*>, Boolean>()
    companion object {
        private const val MODULE_PACKAGE = "com.hazbu.xblock"
        private const val TAG = "xBlock"
    }
    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        Log.d(TAG, "onPackageReady: ${param.packageName}")
        if (param.packageName == MODULE_PACKAGE) return
        Log.d(TAG, "Hooking ${param.packageName} via libxposed")
        val classLoader = param.classLoader
        hookApplication(classLoader)
        hookDns(classLoader)
        hookDeepDns(classLoader)
        hookSocket(classLoader)
        hookOkHttp(classLoader)
        hookCronet(classLoader)
        hookUi(classLoader)
        hookWebView(classLoader)
        hookIntents(classLoader)
        hookService(classLoader)
        hookNuclear(classLoader)
    }
    override fun onHotReloading(param: HotReloadingParam): Boolean {
        Log.d(TAG, "Hot reloading: Preserving module state...")
        return true 
    }
    override fun onHotReloaded(param: HotReloadedParam) {
        super.onHotReloaded(param)
        Log.d(TAG, "Hot reloaded")
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
        } catch (_: Exception) {
            Log.e(TAG, "Hook Error")
        }
    }
    private fun fetchDomainsFromProvider(context: Context) {
        try {
            val statusUri = "content://${Constants.AUTHORITY}/status".toUri()
            val statusCursor = context.contentResolver.query(statusUri, null, null, null, null)
            var count = 0
            if (statusCursor != null) {
                if (statusCursor.moveToFirst()) {
                    count = statusCursor.getInt(0)
                }
                statusCursor.close()
            }
            val loadedDomains = fetchSetFromProvider(context, "domains")
            if (loadedDomains.isNotEmpty()) {
                dynamicDomains = loadedDomains
                Log.d(TAG, "Successfully fetched ${dynamicDomains.size} domains via streaming")
            }
            val loadedPackages = fetchSetFromProvider(context, "packages")
            if (loadedPackages.isNotEmpty()) {
                dynamicPackages = loadedPackages
                classCheckCache.clear()
                Log.d(TAG, "Successfully fetched ${dynamicPackages.size} packages from Exodus via streaming")
            }
        } catch (_: Exception) {
            Log.e(TAG, "Failed to fetch data from provider")
        }
    }
    private fun fetchSetFromProvider(context: Context, path: String): Set<String> {
        val uri = "content://${Constants.AUTHORITY}/$path".toUri()
        val result = HashSet<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        result.add(trimmed.lowercase().trimEnd('.'))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Streaming openInputStream failed for $path: ${e.message}")
        }
        if (result.isEmpty()) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    while (it.moveToNext()) {
                        val item = it.getString(0)
                        if (!item.isNullOrEmpty()) {
                            result.add(item.trim().lowercase().trimEnd('.'))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cursor query failed for $path: ${e.message}")
            }
        }
        return result
    }
    private fun hookDns(classLoader: ClassLoader) {
        try {
            val inetAddressClass = classLoader.loadClass("java.net.InetAddress")
            val getByNameMethod = inetAddressClass.getDeclaredMethod("getByName", String::class.java)
            val getAllByNameMethod = inetAddressClass.getDeclaredMethod("getAllByName", String::class.java)
            val sinkholeAddress = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))
            hook(getByNameMethod).intercept { chain ->
                val host = chain.args[0] as? String
                if ((host != null) && isAdDomain(host)) {
                    Log.d(TAG, "DNS blocking: $host")
                    sinkholeAddress
                } else {
                    chain.proceed()
                }
            }
            hook(getAllByNameMethod).intercept { chain ->
                val host = chain.args[0] as? String
                if ((host != null) && isAdDomain(host)) {
                    Log.d(TAG, "DNS (all) blocking: $host")
                    arrayOf(sinkholeAddress)
                } else {
                    chain.proceed()
                }
            }
        } catch (_: Exception) {
            Log.e(TAG, "DNS Hook Error")
        }
    }
    private fun hookDeepDns(classLoader: ClassLoader) {
        try {
            val dnsResolverClass = classLoader.loadClass("android.net.DnsResolver")
            val callbackClass = classLoader.loadClass("android.net.DnsResolver" + "$" + "Callback")
            val dnsExceptionClass = classLoader.loadClass("android.net.DnsResolver" + "$" + "DnsException")
            val dnsExceptionConstructor = dnsExceptionClass.getDeclaredConstructor(Int::class.java, Throwable::class.java)
            val queryMethods = dnsResolverClass.declaredMethods.filter { it.name == "query" || it.name == "rawQuery" }
            for (method in queryMethods) {
                hook(method).intercept { chain ->
                    val domain = chain.args.find { it is String } as? String
                    if (domain != null && isAdDomain(domain)) {
                        Log.d(TAG, "Deep DNS blocking: $domain")
                        val callback = chain.args.find { callbackClass.isInstance(it) }
                        if (callback != null) {
                            val error = dnsExceptionConstructor.newInstance(1, null)
                            val onErrorMethod = callbackClass.getDeclaredMethod("onError", dnsExceptionClass)
                            onErrorMethod.invoke(callback, error)
                        }
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }
        } catch (_: Exception) {
            Log.e(TAG, "Deep DNS Hook Error")
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
                        Log.d(TAG, "Socket blocking: $host")
                        throw java.net.ConnectException("Connection refused (xBlock)")
                    }
                }
                chain.proceed()
            }
        } catch (_: Exception) {
            Log.e(TAG, "Socket Hook Error")
        }
    }
    private fun hookOkHttp(classLoader: ClassLoader) {
        try {
            val builderClass = classLoader.loadClass("okhttp3.OkHttpClient" + "$" + "Builder")
            val dnsInterface = classLoader.loadClass("okhttp3.Dns")
            val dnsMethod = builderClass.getDeclaredMethod("dns", dnsInterface)
            val systemDns = dnsInterface.getDeclaredField("SYSTEM")[null]
            hook(dnsMethod).intercept { chain ->
                Log.d(TAG, "OkHttp DNS: Forcing system DNS")
                chain.args[0] = systemDns
                chain.proceed()
            }
        } catch (_: Exception) {}
    }
    private fun hookCronet(classLoader: ClassLoader) {
        try {
            val builderClass = classLoader.loadClass("org.chromium.net.CronetEngine" + "$" + "Builder")
            builderClass.declaredMethods.find { it.name == "setUseBuiltInDnsResolver" }?.let { method ->
                hook(method).intercept { chain ->
                    Log.d(TAG, "Cronet DNS: Disabling built-in resolver")
                    chain.args[0] = false
                    chain.proceed()
                }
            }
        } catch (_: Exception) {}
    }
    private fun isAdView(view: View): Boolean {
        val packages = dynamicPackages
        if (packages.isEmpty()) return false
        return classCheckCache.computeIfAbsent(view.javaClass) { clazz ->
            AdBlockUtils.isAdClass(clazz.name, packages)
        }
    }
    private fun hookUi(classLoader: ClassLoader) {
        try {
            val viewGroupClass = classLoader.loadClass("android.view.ViewGroup")
            val addViewMethod = viewGroupClass.getDeclaredMethod("addView", View::class.java, ViewGroup.LayoutParams::class.java)
            hook(addViewMethod).intercept { chain ->
                val view = chain.args[0] as? View
                if (view != null && isAdView(view)) {
                    Log.d(TAG, "UI blocking: Hiding ${view.javaClass.name}")
                    recursiveHide(view)
                }
                chain.proceed()
            }
            try {
                val viewClass = classLoader.loadClass("android.view.View")
                hook(viewClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)).intercept { chain ->
                    val view = chain.thisObject as? View
                    if (view != null && isAdView(view)) {
                        chain.args[0] = View.GONE
                    }
                    chain.proceed()
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {
            Log.e(TAG, "UI Hook Error")
        }
    }
    private fun recursiveHide(view: View) {
        view.visibility = View.GONE
        view.alpha = 0f
        view.scaleX = 0f
        view.scaleY = 0f
        view.translationX = 9999f
        try {
            view.layoutParams?.let {
                it.width = 0
                it.height = 0
            }
        } catch (_: Exception) {}
        var current = view.parent as? ViewGroup
        while (current != null) {
            var visibleChildren = 0
            for (i in 0 until current.childCount) {
                val child = current.getChildAt(i)
                if (child != view && child.isVisible) {
                    visibleChildren++
                }
            }
            if (visibleChildren == 0) {
                current.visibility = View.GONE
                try {
                    current.layoutParams?.let { it.width = 0; it.height = 0 }
                } catch (_: Exception) {}
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
                val modernMethod = webViewClientClass.getDeclaredMethod(
                    "shouldInterceptRequest", 
                    classLoader.loadClass("android.webkit.WebView"), 
                    classLoader.loadClass("android.webkit.WebResourceRequest"),
                )
                hook(modernMethod).intercept { chain ->
                    val request = chain.args[1] as? WebResourceRequest
                    val host = request?.url?.host
                    if ((host != null) && isAdDomain(host)) {
                        Log.d(TAG, "WebView blocking $host")
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                    } else {
                        chain.proceed()
                    }
                }
            } catch (_: NoSuchMethodException) {}
            try {
                val legacyMethod = webViewClientClass.getDeclaredMethod("shouldInterceptRequest", 
                    classLoader.loadClass("android.webkit.WebView"), 
                    String::class.java)
                hook(legacyMethod).intercept { chain ->
                    val urlString = chain.args[1] as? String
                    val host = urlString?.toUri()?.host
                    if ((host != null) && isAdDomain(host)) {
                        Log.d(TAG, "WebView (legacy) blocking $host")
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                    } else {
                        chain.proceed()
                    }
                }
            } catch (_: NoSuchMethodException) {}
        } catch (_: Exception) {
            Log.e(TAG, "WebView Hook Error")
        }
    }
    @Suppress("PrivateApi")
    private fun hookIntents(classLoader: ClassLoader) {
        try {
            val contextImplClass = classLoader.loadClass("android.app.ContextImpl")
            val startActivityMethod = contextImplClass.getDeclaredMethod("startActivity", Intent::class.java)
            hook(startActivityMethod).intercept { chain ->
                val intent = chain.args[0] as? Intent
                if (intent != null && isAdIntent(intent)) {
                    Log.d(TAG, "Intent blocking: Redirect prevented")
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
                            Log.d(TAG, "Instrumentation blocking: Ad Activity prevented")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {
            Log.e(TAG, "Intent Hook Error")
        }
    }
    @Suppress("PrivateApi")
    private fun hookService(classLoader: ClassLoader) {
        try {
            val contextImplClass = classLoader.loadClass("android.app.ContextImpl")
            val bindServiceMethods = contextImplClass.declaredMethods.filter { it.name == "bindService" }
            for (method in bindServiceMethods) {
                hook(method).intercept { chain ->
                    val intent = chain.args.find { it is Intent } as? Intent
                    if (intent != null && isAdIntent(intent)) {
                        Log.d(TAG, "Blocking ad service: action=${intent.action}, pkg=${intent.`package`}")
                        return@intercept false 
                    }
                    chain.proceed()
                }
            }
        } catch (_: Exception) {
            Log.e(TAG, "Service Hook Error")
        }
    }
    private fun hookNuclear(classLoader: ClassLoader) {
        try {
            val activityClass = classLoader.loadClass("android.app.Activity")
            val onCreateMethod = activityClass.getDeclaredMethod("onCreate", Bundle::class.java)
            hook(onCreateMethod).intercept { chain ->
                val activity = chain.thisObject as Activity
                val className = activity.javaClass.name
                val packages = dynamicPackages
                if (AdBlockUtils.isAdClass(className, packages)) {
                    Log.d(TAG, "Nuclear: Finishing ad activity $className")
                    val result = chain.proceed()
                    activity.setResult(Activity.RESULT_OK)
                    activity.finish()
                    return@intercept result
                }
                chain.proceed()
            }
        } catch (_: Exception) {}
    }
    private fun isAdDomain(host: String): Boolean {
        return AdBlockUtils.isAdDomain(host, dynamicDomains)
    }
    private fun isAdIntent(intent: Intent): Boolean {
        // Never block internal app activities (doing so causes games like Unity to freeze waiting for ad dismiss callbacks)
        if (intent.component != null) {
            return false
        }
        val data = try { intent.dataString?.lowercase() ?: "" } catch (_: Exception) { "" }
        val action = intent.action ?: ""

        // Block external view redirects to ad domains or ad tracker urls
        if (action == Intent.ACTION_VIEW) {
            val uri = intent.data
            val host = uri?.host
            if (host != null && isAdDomain(host)) {
                Log.i(TAG, "Intent blocking: Ad redirect prevented ($host)")
                return true
            }
        }
        return false
    }
}

