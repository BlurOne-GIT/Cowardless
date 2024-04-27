/*
    MIT License
    Copyright (c) 2024 BlurOne!
    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:
    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
*/

package code.blurone.cowardless

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import java.nio.ByteBuffer
import java.util.UUID

class LocationDataType : PersistentDataType<ByteArray, Location> {
    override fun getPrimitiveType(): Class<ByteArray> {
        return ByteArray::class.java
    }

    override fun getComplexType(): Class<Location> {
        return Location::class.java
    }

    override fun toPrimitive(complex: Location, context: PersistentDataAdapterContext): ByteArray {
        val buffer = ByteBuffer.allocate(48)
        buffer.putLong(complex.world?.uid?.mostSignificantBits ?: 0)
        buffer.putLong(complex.world?.uid?.leastSignificantBits ?: 0)
        buffer.putDouble(complex.x)
        buffer.putDouble(complex.y)
        buffer.putDouble(complex.z)
        buffer.putFloat(complex.yaw)
        buffer.putFloat(complex.pitch)
        return buffer.array()
    }

    override fun fromPrimitive(primitive: ByteArray, context: PersistentDataAdapterContext): Location {
        val buffer = ByteBuffer.wrap(primitive)
        return Location(
            Bukkit.getWorld(UUID(buffer.long, buffer.long)), // world
            buffer.double, // x
            buffer.double, // y
            buffer.double, // z
            buffer.float, // yaw
            buffer.float) // pitch
    }
}