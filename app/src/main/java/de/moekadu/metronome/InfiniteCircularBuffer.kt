/*
 * Copyright 2019 Michael Moessner
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

class InfiniteCircularBuffer<Type>(initialCapacity : Int = 1, private val init : () -> Type) {
    private var data = Array<Any?>(initialCapacity) {init()}
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

//    fun last() : Type {
//        return get(indexEnd-1)
//    }

    fun pop() : Type {
        val value = get(indexStart)
        ++indexStart
        return value
    }
}