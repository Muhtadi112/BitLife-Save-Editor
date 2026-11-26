package com.adi.bitlifesaveeditor

import java.nio.charset.StandardCharsets

/**
 * Read-only NRBF record walker for the specific path:
 * Life.<Finances>k__BackingField ->
 * SimFinances.<BankBalance>k__BackingField (PrimitiveType.Double).
 *
 * This prototype never invokes BinaryFormatter or constructs game classes.
 * It is deliberately not wired into BalancePatchEngine until it is validated
 * against user-supplied real save copies from supported BitLife versions.
 */
internal data class NrbfBalanceLocation(
    val offset: Int,
    val value: Double,
    val rootType: String,
    val financesType: String
)

internal object NrbfBalanceLocator {
    fun locate(data: ByteArray): NrbfBalanceLocation? =
        runCatching { Parser(data).locate() }.getOrNull()
}

private class Parser(private val data: ByteArray) {
    private companion object {
        const val RECORD_HEADER = 0
        const val RECORD_CLASS_WITH_ID = 1
        const val RECORD_SYSTEM_CLASS = 2
        const val RECORD_CLASS = 3
        const val RECORD_SYSTEM_CLASS_WITH_TYPES = 4
        const val RECORD_CLASS_WITH_TYPES = 5
        const val RECORD_STRING = 6
        const val RECORD_BINARY_ARRAY = 7
        const val RECORD_PRIMITIVE_TYPED = 8
        const val RECORD_REFERENCE = 9
        const val RECORD_NULL = 10
        const val RECORD_MESSAGE_END = 11
        const val RECORD_LIBRARY = 12
        const val RECORD_NULL_RUN_BYTE = 13
        const val RECORD_NULL_RUN_INT = 14
        const val RECORD_ARRAY_PRIMITIVE = 15
        const val RECORD_ARRAY_OBJECT = 16
        const val RECORD_ARRAY_STRING = 17

        const val BINARY_PRIMITIVE = 0
        const val BINARY_SYSTEM_CLASS = 3
        const val BINARY_CLASS = 4
        const val BINARY_PRIMITIVE_ARRAY = 7
        const val BINARY_OBJECT = 2

        const val PRIMITIVE_NULL = 17
        const val PRIMITIVE_STRING = 18
        const val PRIMITIVE_DOUBLE = 6

        const val MAX_SAVE_BYTES = 64 * 1024 * 1024
        const val MAX_STRING_BYTES = 1024 * 1024
        const val MAX_COLLECTION_COUNT = 1_000_000
        const val MAX_OBJECTS = 250_000
        const val MAX_DEPTH = 128
    }

    private data class MemberType(
        val binaryType: Int,
        val primitiveType: Int? = null
    )

    private data class ClassMetadata(
        val className: String,
        val memberNames: List<String>,
        val memberTypes: List<MemberType>
    )

    private data class ParsedValue(
        val objectId: Int? = null,
        val referenceId: Int? = null,
        val primitiveType: Int? = null,
        val primitiveOffset: Int? = null,
        val doubleValue: Double? = null
    )

    private class ParsedObject(
        val objectId: Int,
        val className: String
    ) {
        val members = linkedMapOf<String, ParsedValue>()
    }

    private val metadata = HashMap<Int, ClassMetadata>()
    private val objects = HashMap<Int, ParsedObject>()
    private val objectIds = HashSet<Int>()
    private var position = 0
    private var rootId = 0
    private var objectCount = 0

    fun locate(): NrbfBalanceLocation? {
        if (data.size < 17 || data.size > MAX_SAVE_BYTES) return null
        if (readUnsignedByte() != RECORD_HEADER) return null

        rootId = readInt()
        val headerId = readInt()
        val majorVersion = readInt()
        val minorVersion = readInt()
        if (rootId <= 0 || headerId != -1 || majorVersion != 1 || minorVersion != 0) return null

        var messageEnded = false
        while (position < data.size) {
            if (peekUnsignedByte() == RECORD_MESSAGE_END) {
                readUnsignedByte()
                messageEnded = true
                break
            }
            readRecordValue(0)
        }
        if (!messageEnded || position != data.size) return null

        val root = objects[rootId] ?: return null
        if (simpleTypeName(root.className) != "Life") return null

        val financesValue = root.members["<Finances>k__BackingField"] ?: return null
        val financesId = financesValue.objectId ?: financesValue.referenceId ?: return null
        val finances = objects[financesId] ?: return null
        if (simpleTypeName(finances.className) != "SimFinances") return null

        val balance = finances.members["<BankBalance>k__BackingField"] ?: return null
        if (balance.primitiveType != PRIMITIVE_DOUBLE) return null
        val offset = balance.primitiveOffset ?: return null
        val value = balance.doubleValue ?: return null
        if (!value.isFinite()) return null

        return NrbfBalanceLocation(
            offset = offset,
            value = value,
            rootType = root.className,
            financesType = finances.className
        )
    }

    private fun readRecordValue(depth: Int): ParsedValue {
        if (depth > MAX_DEPTH) fail("NRBF nesting limit exceeded")

        var recordType = readUnsignedByte()
        while (recordType == RECORD_LIBRARY) {
            readInt() // library id
            readString()
            recordType = readUnsignedByte()
        }

        return when (recordType) {
            RECORD_CLASS_WITH_ID -> readClassWithId(depth)
            RECORD_SYSTEM_CLASS -> readClassWithoutTypes(depth, systemClass = true)
            RECORD_CLASS -> readClassWithoutTypes(depth, systemClass = false)
            RECORD_SYSTEM_CLASS_WITH_TYPES -> readClassWithTypes(depth, systemClass = true)
            RECORD_CLASS_WITH_TYPES -> readClassWithTypes(depth, systemClass = false)
            RECORD_STRING -> {
                val objectId = readInt()
                registerObjectId(objectId)
                readString()
                ParsedValue(objectId = objectId)
            }
            RECORD_BINARY_ARRAY -> readBinaryArray(depth)
            RECORD_PRIMITIVE_TYPED -> readPrimitive(readUnsignedByte())
            RECORD_REFERENCE -> ParsedValue(referenceId = readInt())
            RECORD_NULL -> ParsedValue()
            RECORD_ARRAY_PRIMITIVE -> readSinglePrimitiveArray()
            RECORD_ARRAY_OBJECT, RECORD_ARRAY_STRING -> readSingleRecordArray(depth)
            else -> fail("Unsupported NRBF record type $recordType")
        }
    }

    private fun readClassWithId(depth: Int): ParsedValue {
        val objectId = readInt()
        val metadataId = readInt()
        val classMetadata = metadata[metadataId] ?: fail("Unknown NRBF metadata id $metadataId")
        return readClassInstance(objectId, classMetadata, depth + 1)
    }

    private fun readClassWithoutTypes(depth: Int, systemClass: Boolean): ParsedValue {
        val objectId = readInt()
        val className = readString()
        val memberCount = readCount()
        val memberNames = List(memberCount) { readString() }
        ensureUniqueMemberNames(memberNames)
        if (!systemClass) readInt() // library id

        val classMetadata = ClassMetadata(
            className = className,
            memberNames = memberNames,
            memberTypes = List(memberCount) { MemberType(BINARY_OBJECT) }
        )
        metadata[objectId] = classMetadata
        return readClassInstance(objectId, classMetadata, depth + 1)
    }

    private fun readClassWithTypes(depth: Int, systemClass: Boolean): ParsedValue {
        val objectId = readInt()
        val className = readString()
        val memberCount = readCount()
        val memberNames = List(memberCount) { readString() }
        ensureUniqueMemberNames(memberNames)

        val binaryTypes = List(memberCount) { readUnsignedByte() }
        val memberTypes = binaryTypes.map { readMemberType(it) }
        if (!systemClass) readInt() // library id

        val classMetadata = ClassMetadata(className, memberNames, memberTypes)
        metadata[objectId] = classMetadata
        return readClassInstance(objectId, classMetadata, depth + 1)
    }

    private fun readMemberType(binaryType: Int): MemberType {
        return when (binaryType) {
            BINARY_PRIMITIVE -> MemberType(binaryType, readUnsignedByte())
            BINARY_SYSTEM_CLASS -> {
                readString()
                MemberType(binaryType)
            }
            BINARY_CLASS -> {
                readString()
                readInt() // library id
                MemberType(binaryType)
            }
            BINARY_PRIMITIVE_ARRAY -> MemberType(binaryType, readUnsignedByte())
            in 1..6 -> MemberType(binaryType)
            else -> fail("Unsupported NRBF binary type $binaryType")
        }
    }

    private fun readClassInstance(
        objectId: Int,
        classMetadata: ClassMetadata,
        depth: Int
    ): ParsedValue {
        registerObjectId(objectId)
        val instance = ParsedObject(objectId, classMetadata.className)
        objects[objectId] = instance

        classMetadata.memberNames.forEachIndexed { index, memberName ->
            val memberType = classMetadata.memberTypes[index]
            val value = if (
                memberType.binaryType == BINARY_PRIMITIVE &&
                memberType.primitiveType != PRIMITIVE_STRING
            ) {
                readPrimitive(memberType.primitiveType ?: fail("Missing primitive type"))
            } else {
                readRecordValue(depth + 1)
            }
            instance.members[memberName] = value
        }
        return ParsedValue(objectId = objectId)
    }

    private fun readBinaryArray(depth: Int): ParsedValue {
        val objectId = readInt()
        registerObjectId(objectId)
        val arrayType = readUnsignedByte()
        if (arrayType !in 0..5) fail("Invalid NRBF binary array kind")

        val rank = readCount()
        if (rank == 0 || rank > 32) fail("Invalid NRBF array rank")
        val lengths = List(rank) { readCount() }
        if (arrayType in 3..5) repeat(rank) { readInt() }

        val elementBinaryType = readUnsignedByte()
        val elementType = readMemberType(elementBinaryType)
        var elementCount = 1L
        for (length in lengths) {
            if (length != 0 && elementCount > MAX_COLLECTION_COUNT.toLong() / length.toLong()) {
                fail("NRBF array is too large")
            }
            elementCount *= length.toLong()
        }

        if (elementType.binaryType == BINARY_PRIMITIVE) {
            val primitiveType = elementType.primitiveType ?: fail("Missing array primitive type")
            repeat(elementCount.toInt()) { readPrimitive(primitiveType) }
        } else {
            readArrayElements(elementCount.toInt(), depth + 1)
        }
        return ParsedValue(objectId = objectId)
    }

    private fun readSinglePrimitiveArray(): ParsedValue {
        val objectId = readInt()
        registerObjectId(objectId)
        val length = readCount()
        val primitiveType = readUnsignedByte()
        repeat(length) { readPrimitive(primitiveType) }
        return ParsedValue(objectId = objectId)
    }

    private fun readSingleRecordArray(depth: Int): ParsedValue {
        val objectId = readInt()
        registerObjectId(objectId)
        val length = readCount()
        readArrayElements(length, depth + 1)
        return ParsedValue(objectId = objectId)
    }

    private fun readArrayElements(count: Int, depth: Int) {
        var remaining = count
        while (remaining > 0) {
            when (peekUnsignedByte()) {
                RECORD_NULL_RUN_BYTE -> {
                    readUnsignedByte()
                    val nullCount = readUnsignedByte()
                    if (nullCount == 0 || nullCount > remaining) fail("Invalid NRBF null run")
                    remaining -= nullCount
                }
                RECORD_NULL_RUN_INT -> {
                    readUnsignedByte()
                    val nullCount = readCount()
                    if (nullCount == 0 || nullCount > remaining) fail("Invalid NRBF null run")
                    remaining -= nullCount
                }
                else -> {
                    readRecordValue(depth + 1)
                    remaining--
                }
            }
        }
    }

    private fun readPrimitive(primitiveType: Int): ParsedValue {
        val valueOffset = position
        return when (primitiveType) {
            1, 2, 10 -> {
                skip(1)
                ParsedValue(primitiveType = primitiveType, primitiveOffset = valueOffset)
            }
            3, 7, 14 -> {
                skip(2)
                ParsedValue(primitiveType = primitiveType, primitiveOffset = valueOffset)
            }
            5 -> {
                skip(16)
                ParsedValue(primitiveType = primitiveType, primitiveOffset = valueOffset)
            }
            PRIMITIVE_DOUBLE -> {
                val value = Double.fromBits(readLong())
                ParsedValue(
                    primitiveType = primitiveType,
                    primitiveOffset = valueOffset,
                    doubleValue = value
                )
            }
            8, 11, 15 -> {
                skip(4)
                ParsedValue(primitiveType = primitiveType, primitiveOffset = valueOffset)
            }
            9, 12, 13, 16 -> {
                skip(8)
                ParsedValue(primitiveType = primitiveType, primitiveOffset = valueOffset)
            }
            PRIMITIVE_NULL -> ParsedValue(primitiveType = primitiveType, primitiveOffset = valueOffset)
            else -> fail("Unsupported NRBF primitive type $primitiveType")
        }
    }

    private fun registerObjectId(objectId: Int) {
        if (objectId <= 0) fail("Invalid NRBF object id")
        objectCount++
        if (objectCount > MAX_OBJECTS) fail("NRBF object limit exceeded")
        if (!objectIds.add(objectId)) fail("Duplicate NRBF object id $objectId")
    }

    private fun ensureUniqueMemberNames(names: List<String>) {
        if (names.toSet().size != names.size) fail("Duplicate NRBF member name")
    }

    private fun simpleTypeName(name: String): String =
        name.substringBefore(',').substringAfterLast('.').trim()

    private fun readString(): String {
        var length = 0
        var shift = 0
        repeat(5) {
            val next = readUnsignedByte()
            length = length or ((next and 0x7f) shl shift)
            if ((next and 0x80) == 0) {
                if (length < 0 || length > MAX_STRING_BYTES) fail("NRBF string is too large")
                val bytes = readBytes(length)
                return String(bytes, StandardCharsets.UTF_8)
            }
            shift += 7
        }
        fail("Malformed NRBF string length")
    }

    private fun readCount(): Int {
        val count = readInt()
        if (count < 0 || count > MAX_COLLECTION_COUNT) fail("Invalid NRBF collection count")
        return count
    }

    private fun readUnsignedByte(): Int {
        if (position >= data.size) fail("Unexpected end of NRBF stream")
        return data[position++].toInt() and 0xff
    }

    private fun peekUnsignedByte(): Int {
        if (position >= data.size) fail("Unexpected end of NRBF stream")
        return data[position].toInt() and 0xff
    }

    private fun readInt(): Int {
        if (position > data.size - 4) fail("Unexpected end of NRBF stream")
        val value =
            (data[position].toInt() and 0xff) or
                ((data[position + 1].toInt() and 0xff) shl 8) or
                ((data[position + 2].toInt() and 0xff) shl 16) or
                ((data[position + 3].toInt() and 0xff) shl 24)
        position += 4
        return value
    }

    private fun readLong(): Long {
        if (position > data.size - 8) fail("Unexpected end of NRBF stream")
        var value = 0L
        repeat(8) { index ->
            value = value or ((data[position + index].toLong() and 0xffL) shl (index * 8))
        }
        position += 8
        return value
    }

    private fun readBytes(length: Int): ByteArray {
        if (length < 0 || position > data.size - length) fail("Unexpected end of NRBF stream")
        val result = data.copyOfRange(position, position + length)
        position += length
        return result
    }

    private fun skip(length: Int) {
        if (length < 0 || position > data.size - length) fail("Unexpected end of NRBF stream")
        position += length
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)

}
