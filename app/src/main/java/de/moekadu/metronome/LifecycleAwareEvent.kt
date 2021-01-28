package de.moekadu.metronome

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent

class LifecycleAwareEvent<T> {

    fun interface Observer<T> {
        fun onChanged(t: T)
    }

    private inner class LifecycleOwnerInfo(val lifecycleOwner: LifecycleOwner): LifecycleObserver {
        val lifecycle get() = lifecycleOwner.lifecycle

        init {
            lifecycle.addObserver(this)
        }
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun removeObserver() {
            lifecycle.removeObserver(this)
            observers = observers.filterValues { !(it === lifecycleOwner) }.toMutableMap()
        }
    }

    private var observers = mutableMapOf<Observer<T>, LifecycleOwnerInfo>()

    fun observe(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
        observers[observer] = LifecycleOwnerInfo(lifecycleOwner)
    }

    fun removeObserver(observer: Observer<T>) {
        observers.remove(observer)?.removeObserver()
    }

    fun triggerEvent(t: T) {
        for (observer in observers) {
            val lifecycleOwner = observer.value
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                observer.key.onChanged(t)
        }
    }
}