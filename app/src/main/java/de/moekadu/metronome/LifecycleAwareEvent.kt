/*
 * Copyright 2020 Michael Moessner
 *
 * This file is part of Metronome.
 *
 * Metronome is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Metronome is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Metronome.  If not, see <http://www.gnu.org/licenses/>.
 */

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