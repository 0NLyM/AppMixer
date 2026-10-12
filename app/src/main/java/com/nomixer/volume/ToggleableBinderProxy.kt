package com.nomixer.volume

import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import androidx.annotation.RequiresApi
import com.flyjingfish.android_aop_annotation.ProceedJoinPoint
import com.flyjingfish.android_aop_annotation.anno.AndroidAopPointCut
import com.flyjingfish.android_aop_annotation.base.BasePointCut
import org.joor.Reflect
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.util.concurrent.Executor

@AndroidAopPointCut(EnableBinderProxyCut::class)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnableBinderProxy()

class EnableBinderProxyCut : BasePointCut<EnableBinderProxy> {
    override fun invoke(joinPoint: ProceedJoinPoint, anno: EnableBinderProxy): Any? {
        return ToggleableBinderProxy.withEnabled { joinPoint.proceed() }
    }
}

class ToggleableBinderProxy(private val base: IBinder) : IBinder by base {
    companion object {
        private val _enabled: ThreadLocal<Boolean> = ThreadLocal<Boolean>.withInitial { false }

        var enabled: Boolean
            get() = _enabled.get()!!
            set(value) = _enabled.set(value)

        fun <T> withEnabled(block: () -> T): T {
            val prev = enabled
            try {
                enabled = true
                return block()
            } finally {
                enabled = prev
            }
        }

        /**
         * Whether Shizuku can carry a call right now. Without it a call that
         * asked for it goes the plain way instead, as an app's own: what that
         * is allowed is read all the same, and what it isn't is refused the
         * way the platform refuses it -- rather than failing in Shizuku's own
         * wrapper, which throws for a binder it hasn't received.
         */
        fun shizukuReady(): Boolean = try {
            Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

        fun wrap(proxy: Any) {
            Reflect.on(proxy).apply {
                set("mRemote", get<IBinder>("mRemote").run {
                    this as? ToggleableBinderProxy ?: ToggleableBinderProxy(this)
                })
            }
        }
    }

    private val shizukuWrapper = ShizukuBinderWrapper(base)

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun addFrozenStateChangeCallback(
        executor: Executor,
        callback: IBinder.FrozenStateChangeCallback
    ) {
        base.addFrozenStateChangeCallback(executor, callback)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun removeFrozenStateChangeCallback(callback: IBinder.FrozenStateChangeCallback): Boolean {
        return base.removeFrozenStateChangeCallback(callback)
    }

    override fun transact(
        code: Int, data: Parcel, reply: Parcel?, flags: Int
    ): Boolean {
        return (if (enabled && shizukuReady()) shizukuWrapper else base).transact(code, data, reply, flags)
    }
}
