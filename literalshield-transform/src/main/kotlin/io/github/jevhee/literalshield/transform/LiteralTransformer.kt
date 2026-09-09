package io.github.jevhee.literalshield.transform

import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.security.MessageDigest

/** Reports contain locations and decisions only, never original values. */
data class LiteralRecord(
    val id: String, val className: String, val member: String, val ordinal: Int,
    val length: Int, val risk: String, val decision: String, val reason: String,
)
data class TransformResult(val bytes: ByteArray, val records: List<LiteralRecord>)

class LiteralTransformer(private val config: ShieldConfig = ShieldConfig()) {
    private val policy = LiteralPolicy(config)

    fun transform(bytes: ByteArray, module: String, variant: String, seed: String, protect: Boolean = true): TransformResult {
        val node = ClassNode(Opcodes.ASM9)
        ClassReader(bytes).accept(node, 0)
        val records = process(node, module, variant, seed, protect)
        if (records.none { it.decision == "TRANSFORMED" }) return TransformResult(bytes, records)
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        node.accept(writer)
        return TransformResult(writer.toByteArray(), records)
    }

    fun visitor(next: ClassVisitor, module: String, variant: String, seed: String): ClassVisitor =
        object : ClassNode(Opcodes.ASM9) {
            override fun visitEnd() {
                super.visitEnd()
                process(this, module, variant, seed, true)
                accept(next)
            }
        }

    private fun process(node: ClassNode, module: String, variant: String, seed: String, protect: Boolean): List<LiteralRecord> {
        if (node.invisibleAnnotations.orEmpty().any { it.desc == MARKER }) return emptyList()
        val records = mutableListOf<LiteralRecord>()
        val excluded = policy.excludedClass(node.name)
        // Unknown attributes may contain pool indices that cannot safely be rebuilt.
        val unknownAttributes = !node.attrs.isNullOrEmpty() || node.fields.any { !it.attrs.isNullOrEmpty() } ||
            node.methods.any { !it.attrs.isNullOrEmpty() }
        fun record(value: String, member: String, ordinal: Int, forced: String? = null): LiteralRecord {
            val assessment = policy.assess(value)
            val reason = if (excluded) "EXCLUDED_CLASS" else if (unknownAttributes) "UNSUPPORTED_BYTECODE" else forced ?: assessment.reason
            val eligible = !excluded && !unknownAttributes && forced == null && assessment.eligible
            val identity = "$module|$variant|${node.name}|$member|$ordinal|1"
            val id = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                .take(12).joinToString("") { "%02x".format(it) }
            return LiteralRecord(id, node.name, member, ordinal, value.length, assessment.risk,
                if (!eligible) "SKIPPED" else if (protect) "TRANSFORMED" else "ELIGIBLE", reason).also(records::add)
        }
        val fieldInit = InsnList()
        val hasClinit = node.methods.any { it.name == "<clinit>" }
        for (field in node.fields) {
            val value = field.value as? String ?: continue
            val privateStaticFinal = field.access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL) ==
                (Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL)
            // Initial spike supports only classes without existing initialization dependencies.
            val safety = when {
                !privateStaticFinal -> "EXPORTED_CONSTANT"
                hasClinit || node.superName != "java/lang/Object" || node.interfaces.isNotEmpty() -> "UNSAFE_INITIALIZATION"
                value.toByteArray(Charsets.UTF_8).size > 1024 || fieldInit.size() > 10000 -> "SIZE_LIMIT"
                else -> null
            }
            val entry = record(value, "${field.name}:${field.desc}", -1, safety)
            if (entry.decision == "TRANSFORMED") {
                field.value = null
                fieldInit.add(instructions(value, LiteralEncoder.seed(seed, entry.id)))
                fieldInit.add(FieldInsnNode(Opcodes.PUTSTATIC, node.name, field.name, field.desc))
            }
        }
        for (method in node.methods) {
            val original = method.instructions.toArray()
            val unsafe = original.any { instruction ->
                instruction is InvokeDynamicInsnNode ||
                    instruction is LdcInsnNode && instruction.cst is ConstantDynamic ||
                    instruction is MethodInsnNode && unsafeSink(instruction)
            }
            // Worst-case bound for existing instructions, widened jumps/switches, and new arrays.
            var budget = original.sumOf { instruction ->
                when (instruction) {
                    is TableSwitchInsnNode -> 20 + 4 * instruction.labels.size
                    is LookupSwitchInsnNode -> 12 + 8 * instruction.labels.size
                    else -> 8
                }
            }
            for ((ordinal, instruction) in original.withIndex()) {
                val value = (instruction as? LdcInsnNode)?.cst as? String ?: continue
                val cost = value.toByteArray(Charsets.UTF_8).size * 8 + 32
                val safety = when {
                    unsafe -> "UNSAFE_CONTEXT"
                    cost > 8192 || budget + cost > 48000 -> "SIZE_LIMIT"
                    else -> null
                }
                val entry = record(value, method.name + method.desc, ordinal, safety)
                if (entry.decision == "TRANSFORMED") {
                    method.instructions.insertBefore(instruction, instructions(value, LiteralEncoder.seed(seed, entry.id)))
                    method.instructions.remove(instruction)
                    budget += cost
                }
            }
        }
        // Record annotation string values as metadata gaps, including nested annotations/arrays.
        fun metadata(value: Any?, member: String) {
            when (value) {
                is String -> record(value, member, records.size, "METADATA")
                is AnnotationNode -> value.values.orEmpty().chunked(2).forEach { metadata(it.getOrNull(1), member) }
                is List<*> -> value.forEach { metadata(it, member) }
            }
        }
        (node.visibleAnnotations.orEmpty() + node.invisibleAnnotations.orEmpty()).forEach { metadata(it, "@class") }
        node.fields.forEach { field ->
            (field.visibleAnnotations.orEmpty() + field.invisibleAnnotations.orEmpty()).forEach { metadata(it, "@${field.name}") }
        }
        node.methods.forEach { method ->
            (method.visibleAnnotations.orEmpty() + method.invisibleAnnotations.orEmpty()).forEach { metadata(it, "@${method.name}${method.desc}") }
            metadata(method.annotationDefault, "@default:${method.name}")
        }
        if (fieldInit.size() > 0) {
            val init = MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
            init.instructions.add(fieldInit)
            init.instructions.add(InsnNode(Opcodes.RETURN))
            node.methods.add(init)
        }
        if (records.any { it.decision == "TRANSFORMED" }) {
            if (node.invisibleAnnotations == null) node.invisibleAnnotations = mutableListOf()
            node.invisibleAnnotations.add(AnnotationNode(MARKER).apply { values = mutableListOf("version", 1) })
        }
        return records
    }

    private fun unsafeSink(call: MethodInsnNode): Boolean =
        call.owner == "java/lang/Class" || call.owner == "java/lang/ClassLoader" ||
            call.owner.startsWith("java/lang/reflect/") || call.owner.startsWith("java/lang/invoke/") ||
            call.owner == "android/content/res/Resources" || call.owner.startsWith("androidx/navigation/") ||
            call.owner.startsWith("androidx/room/") || call.owner.startsWith("kotlinx/serialization/") ||
            call.owner.startsWith("com/google/gson/") || call.owner.startsWith("com/squareup/moshi/") ||
            call.owner == "java/lang/System" && call.name in setOf("load", "loadLibrary")

    private fun instructions(value: String, seed: Long): InsnList {
        val encoded = LiteralEncoder.encode(value, seed, (seed and 1).toInt())
        return InsnList().apply {
            add(push(encoded.payload.size))
            add(IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE))
            encoded.payload.forEachIndexed { index, byte ->
                add(InsnNode(Opcodes.DUP)); add(push(index)); add(push(byte.toInt()))
                add(InsnNode(Opcodes.BASTORE))
            }
            add(LdcInsnNode(seed)); add(push(encoded.variant)); add(push(encoded.format))
            add(MethodInsnNode(Opcodes.INVOKESTATIC, DECODER, "decode", "([BJII)Ljava/lang/String;", false))
        }
    }

    private fun push(value: Int): AbstractInsnNode = when (value) {
        in -1..5 -> InsnNode(Opcodes.ICONST_0 + value)
        in -128..127 -> IntInsnNode(Opcodes.BIPUSH, value)
        in -32768..32767 -> IntInsnNode(Opcodes.SIPUSH, value)
        else -> LdcInsnNode(value)
    }

    companion object {
        const val DECODER = "io/github/jevhee/literalshield/runtime/LiteralDecoder"
        const val MARKER = "Lio/github/jevhee/literalshield/runtime/Transformed;"
    }
}
