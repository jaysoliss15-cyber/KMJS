package de.robv.android.xposed

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

abstract class XC_MethodHook(val priority: Int = 50) {
    class MethodHookParam {
        @JvmField var method: Member? = null
        @JvmField var thisObject: Any? = null
        @JvmField var args: Array<Any?> = emptyArray()
        private var result: Any? = null
        private var throwable: Throwable? = null
        @JvmField var returnEarly = false

        fun getResult(): Any? = result
        fun setResult(result: Any?) {
            this.result = result
            this.throwable = null
            this.returnEarly = true
        }
        fun getThrowable(): Throwable? = throwable
        fun setThrowable(throwable: Throwable?) {
            this.throwable = throwable
            this.result = null
            this.returnEarly = true
        }
        fun hasThrowable(): Boolean = throwable != null
    }

    class Unhook(val hook: XC_MethodHook, val method: Member) {
        fun unhook() {
            // Unhook placeholder
        }
    }

    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}
}

open class XC_MethodReplacement(priority: Int = 50) : XC_MethodHook(priority) {
    override fun beforeHookedMethod(param: MethodHookParam) {
        try {
            val result = replaceHookedMethod(param)
            param.setResult(result)
        } catch (t: Throwable) {
            param.setThrowable(t)
        }
    }

    open fun replaceHookedMethod(param: MethodHookParam): Any? = null

    companion object {
        @JvmStatic
        fun returnConstant(value: Any?): XC_MethodReplacement {
            return object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? = value
            }
        }
    }
}

object XposedBridge {
    @JvmStatic
    fun log(text: String) {
        android.util.Log.i("KMJS-XPOSED", text)
    }

    @JvmStatic
    fun log(throwable: Throwable) {
        android.util.Log.e("KMJS-XPOSED", "Xposed error", throwable)
    }

    @JvmStatic
    fun hookMethod(hookMethod: Member, callback: XC_MethodHook): XC_MethodHook.Unhook {
        android.util.Log.d("KMJS-XPOSED", "Hook registered for: $hookMethod")
        return XC_MethodHook.Unhook(callback, hookMethod)
    }

    @JvmStatic
    fun hookAllMethods(hookClass: Class<*>, methodName: String, callback: XC_MethodHook): Set<XC_MethodHook.Unhook> {
        val unhooks = mutableSetOf<XC_MethodHook.Unhook>()
        for (method in hookClass.declaredMethods) {
            if (method.name == methodName) {
                unhooks.add(hookMethod(method, callback))
            }
        }
        return unhooks
    }

    @JvmStatic
    fun hookAllConstructors(hookClass: Class<*>, callback: XC_MethodHook): Set<XC_MethodHook.Unhook> {
        val unhooks = mutableSetOf<XC_MethodHook.Unhook>()
        for (constructor in hookClass.declaredConstructors) {
            unhooks.add(hookMethod(constructor, callback))
        }
        return unhooks
    }
}

object XposedHelpers {
    @JvmStatic
    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        val cl = classLoader ?: ClassLoader.getSystemClassLoader()
        return cl.loadClass(className)
    }

    @JvmStatic
    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? {
        return try {
            findClass(className, classLoader)
        } catch (e: Throwable) {
            null
        }
    }

    @JvmStatic
    fun findField(clazz: Class<*>, fieldName: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldError(fieldName)
    }

    @JvmStatic
    fun findFieldIfExists(clazz: Class<*>, fieldName: String): Field? {
        return try {
            findField(clazz, fieldName)
        } catch (e: Throwable) {
            null
        }
    }

    @JvmStatic
    fun findMethodExact(clazz: Class<*>, methodName: String, vararg parameterTypes: Any?): Method {
        val paramClasses = parameterTypes.map {
            when (it) {
                is Class<*> -> it
                is String -> findClass(it, clazz.classLoader)
                else -> throw IllegalArgumentException("Parameter type must be Class or String")
            }
        }.toTypedArray()
        val method = clazz.getDeclaredMethod(methodName, *paramClasses)
        method.isAccessible = true
        return method
    }

    @JvmStatic
    fun findMethodExactIfExists(clazz: Class<*>, methodName: String, vararg parameterTypes: Any?): Method? {
        return try {
            findMethodExact(clazz, methodName, *parameterTypes)
        } catch (e: Throwable) {
            null
        }
    }

    @JvmStatic
    fun findMethodBestMatch(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method {
        for (method in clazz.declaredMethods) {
            if (method.name == methodName && method.parameterTypes.size == parameterTypes.size) {
                var match = true
                for (i in parameterTypes.indices) {
                    if (!method.parameterTypes[i].isAssignableFrom(parameterTypes[i])) {
                        match = false
                        break
                    }
                }
                if (match) {
                    method.isAccessible = true
                    return method
                }
            }
        }
        // Try superclass
        val superclass = clazz.superclass
        if (superclass != null) {
            return findMethodBestMatch(superclass, methodName, *parameterTypes)
        }
        throw NoSuchMethodError("$methodName on $clazz")
    }

    @JvmStatic
    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any?
    ): XC_MethodHook.Unhook? {
        return try {
            val clazz = findClass(className, classLoader)
            findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
        } catch (t: Throwable) {
            XposedBridge.log("Failed to find and hook method $methodName in $className: ${t.message}")
            null
        }
    }

    @JvmStatic
    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypesAndCallback: Any?
    ): XC_MethodHook.Unhook {
        if (parameterTypesAndCallback.isEmpty()) {
            throw IllegalArgumentException("No callback provided")
        }
        val callback = parameterTypesAndCallback.last() as? XC_MethodHook
            ?: throw IllegalArgumentException("Last parameter must be XC_MethodHook")
        val paramTypes = parameterTypesAndCallback.copyOfRange(0, parameterTypesAndCallback.size - 1)
        val method = findMethodExact(clazz, methodName, *paramTypes)
        return XposedBridge.hookMethod(method, callback)
    }

    @JvmStatic
    fun findAndHookConstructor(
        clazz: Class<*>,
        vararg parameterTypesAndCallback: Any?
    ): XC_MethodHook.Unhook {
        val callback = parameterTypesAndCallback.last() as? XC_MethodHook
            ?: throw IllegalArgumentException("Last parameter must be XC_MethodHook")
        val paramTypes = parameterTypesAndCallback.copyOfRange(0, parameterTypesAndCallback.size - 1)
            .map { if (it is Class<*>) it else findClass(it as String, clazz.classLoader) }
            .toTypedArray()
        val constructor = clazz.getDeclaredConstructor(*paramTypes)
        constructor.isAccessible = true
        return XposedBridge.hookMethod(constructor, callback)
    }

    @JvmStatic
    fun getObjectField(obj: Any, fieldName: String): Any? {
        val field = findField(obj.javaClass, fieldName)
        return field.get(obj)
    }

    @JvmStatic
    fun setObjectField(obj: Any, fieldName: String, value: Any?) {
        val field = findField(obj.javaClass, fieldName)
        field.set(obj, value)
    }

    @JvmStatic
    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        val types = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        val method = findMethodBestMatch(obj.javaClass, methodName, *types)
        return method.invoke(obj, *args)
    }

    @JvmStatic
    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        val types = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        val method = findMethodBestMatch(clazz, methodName, *types)
        return method.invoke(null, *args)
    }
}

interface IXposedHookLoadPackage {
    fun handleLoadPackage(lpparam: de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam)
}

interface IXposedHookZygoteInit {
    class StartupParam {
        @JvmField var modulePath: String? = null
        @JvmField var startsSystemServer = false
    }
    fun initZygote(startupParam: StartupParam)
}
