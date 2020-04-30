package de.moekadu.metronome

import android.util.Log


class InfiniteCircularBuffer<Type>(initialCapacity : Int = 1, private val init : () -> Type) {
    var data = Array<Any?>(initialCapacity) {init()}
    var indexStart = 0
        private set
    var indexEnd = 0
        private set
    val size : Int
        get() {
            return indexEnd - indexStart
        }

    fun clear() {
        indexStart = 0
        indexEnd = 0
    }

    fun add() : Type {
        if(indexEnd - indexStart == data.size){
            val oldSize = data.size
            val newSize = 2 * data.size
            val newData = Array<Any?>(newSize) {init()}
            for(i in indexStart until indexEnd) {
                newData[i % newSize] = data[i % oldSize]
            }
            data = newData
        }
        ++indexEnd
        return get(indexEnd-1)
    }

    operator fun get(i : Int) : Type {
        // Log.v("AudioMixer", "InfiniteCircularBuffer.get : i=$i, indexStart=$indexStart, indexEnd=$indexEnd")
        require(i in indexStart until indexEnd)
        @Suppress("UNCHECKED_CAST")
        return data[i % data.size] as Type
    }

    fun first() : Type {
        return get(indexStart)
    }

    fun last() : Type {
        return get(indexEnd-1)
    }

    fun pop() : Type {
        val value = get(indexStart)
        ++indexStart
        return value
    }
}