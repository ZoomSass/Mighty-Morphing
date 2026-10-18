package app.mightymorphing.patches.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private fun MutableMethod.where(): String = "$definingClass->$name"

private fun Any.declaredFieldsDeep(): List<java.lang.reflect.Field> = buildList {
    var type: Class<*>? = javaClass
    while (type != null && type != Any::class.java) {
        addAll(type.declaredFields)
        type = type.superclass
    }
}

private fun MutableMethodImplementation.setRegisterCount(count: Int) {
    if (registerCount == count) return
    val current = registerCount
    val fields = declaredFieldsDeep().filter { field ->
        field.type == Int::class.javaPrimitiveType &&
            !java.lang.reflect.Modifier.isStatic(field.modifiers)
    }
    val field = fields.firstOrNull { it.name == "registerCount" }
        ?: fields.firstOrNull { candidate ->
            candidate.isAccessible = true
            runCatching { candidate.getInt(this) }.getOrNull() == current &&
                candidate.declaringClass != javaClass
        }
        ?: fields.firstOrNull { candidate ->
            candidate.isAccessible = true
            runCatching { candidate.getInt(this) }.getOrNull() == current
        }
        ?: throw PatchException("This smali library has no register count field")
    field.isAccessible = true
    runCatching {
        val modifiers = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
        modifiers.isAccessible = true
        modifiers.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
    }
    field.setInt(this, count)
    if (registerCount == count) return
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    val offset = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field::class.java)
        .invoke(unsafe, field) as Long
    unsafeClass.getMethod(
        "putInt",
        Any::class.java,
        Long::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
    ).invoke(unsafe, this, offset, count)
    if (registerCount != count) {
        throw PatchException("Register count stayed $registerCount")
    }
}

private fun MutableMethodImplementation.clearTryBlocks() {
    if (tryBlocks.isEmpty()) return
    val fields = declaredFieldsDeep()
    val field = fields.firstOrNull { it.name == "tryBlocks" }
        ?: fields.singleOrNull { List::class.java.isAssignableFrom(it.type) && it.name != "instructionList" }
        ?: throw PatchException("This smali library has no try-block list")
    field.isAccessible = true
    val blocks = field.get(this) as? MutableList<*>
        ?: throw PatchException("This smali library has no editable try-block list")
    blocks.clear()
}

fun MutableMethod.clearBody() {
    val impl = implementation ?: throw PatchException("${where()} has no body")
    impl.clearTryBlocks()
    val count = impl.instructions.size
    if (count > 0) {
        removeInstructions(count)
    }
}

/**
 * Replace a method body. [registerCount] is the total register count, including parameters.
 * Set it before the new body is compiled so `p` registers land on the right slots.
 */
fun MutableMethod.setBody(registerCount: Int, smali: String) {
    val impl = implementation ?: throw PatchException("${where()} has no body")
    clearBody()
    // A larger existing count still maps p registers to the parameters.
    if (impl.registerCount < registerCount) {
        impl.setRegisterCount(registerCount)
    }
    addInstructionsWithLabels(0, smali.trimIndent())
}

fun MutableMethod.returnVoid() {
    val registers = implementation?.registerCount ?: 1
    setBody(if (registers == 0) 1 else registers, "return-void")
}

fun MutableMethod.returnBoolean(value: Boolean) {
    val registers = maxOf(implementation?.registerCount ?: 1, 1)
    val bit = if (value) "0x1" else "0x0"
    setBody(
        registers,
        """
        const/4 v0, $bit
        return v0
        """,
    )
}

fun MutableMethod.indexOfReference(snippet: String): Int {
    val index = instructions.indexOfFirst { instruction ->
        instruction is ReferenceInstruction && instruction.reference.toString().contains(snippet)
    }
    if (index < 0) {
        throw PatchException("${where()} has no instruction containing $snippet")
    }
    return index
}

fun MutableMethod.indexOfString(value: String): Int {
    val index = instructions.indexOfFirst { instruction ->
        (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
            instruction is ReferenceInstruction &&
            instruction.reference.toString().contains(value)
    }
    if (index < 0) {
        throw PatchException("${where()} has no string $value")
    }
    return index
}

fun MutableMethod.insertAt(index: Int, smali: String, vararg labels: ExternalLabel) {
    addInstructionsWithLabels(index, smali.trimIndent(), *labels)
}
