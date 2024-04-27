package code.blurone.cowardless

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CompoundTagDataType : PersistentDataType<ByteArray, CompoundTag> {
    override fun getPrimitiveType(): Class<ByteArray> {
        return ByteArray::class.java
    }

    override fun getComplexType(): Class<CompoundTag> {
        return CompoundTag::class.java
    }

    override fun toPrimitive(complex: CompoundTag, context: PersistentDataAdapterContext): ByteArray {
        val stream = ByteArrayOutputStream()
        NbtIo.writeCompressed(complex, stream)
        return stream.toByteArray()
    }

    override fun fromPrimitive(primitive: ByteArray, context: PersistentDataAdapterContext): CompoundTag {
        val stream = ByteArrayInputStream(primitive)
        return NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap())
    }
}