/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

@file:JvmName("PsiBytecodeUtil")
package com.demonwav.mcdev.util

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.annotations.Contract

private const val INTERNAL_CONSTRUCTOR_NAME = "<init>"

// Type

@get:Contract(pure = true)
val PsiPrimitiveType.internalName: Char
    get() = when (this) {
        PsiType.BYTE -> 'B'
        PsiType.CHAR -> 'C'
        PsiType.DOUBLE -> 'D'
        PsiType.FLOAT -> 'F'
        PsiType.INT -> 'I'
        PsiType.LONG -> 'J'
        PsiType.SHORT -> 'S'
        PsiType.BOOLEAN -> 'Z'
        PsiType.VOID -> 'V'
        else -> throw IllegalArgumentException("Unsupported primitive type: $this")
    }

@Contract(pure = true)
private fun PsiClassType.erasure() = TypeConversionUtil.erasure(this) as PsiClassType

@get:Contract(pure = true)
val PsiClassType.internalName
    get() = erasure().resolve()!!.internalName

fun PsiClassType.appendInternalName(builder: StringBuilder): StringBuilder = erasure().resolve()!!.appendInternalName(builder)

@get:Contract(pure = true)
val PsiType.descriptor: String
    get() = appendDescriptor(StringBuilder()).toString()

fun PsiType.appendDescriptor(builder: StringBuilder): StringBuilder {
    return when (this) {
        is PsiPrimitiveType -> builder.append(internalName)
        is PsiArrayType -> componentType.appendDescriptor(builder.append('['))
        is PsiClassType -> appendInternalName(builder.append('L')).append(';')
        else -> throw IllegalArgumentException("Unsupported PsiType: $this")
    }
}

// Class

@get:Contract(pure = true)
val PsiClass.internalName
    get() = outerQualifiedName?.replace('.', '/') ?: buildInternalName(StringBuilder()).toString()

fun PsiClass.appendInternalName(builder: StringBuilder): StringBuilder {
    return outerQualifiedName?.let { builder.append(it.replace('.', '/')) } ?: buildInternalName(builder)
}

private fun PsiClass.buildInternalName(builder: StringBuilder): StringBuilder {
    buildInnerName(builder, { it.outerQualifiedName?.replace('.', '/') })
    return builder
}

@get:Contract(pure = true)
val PsiClass.descriptor: String?
    get() = appendInternalName(StringBuilder().append('L')).append(';').toString()

fun PsiClass.appendDescriptor(builder: StringBuilder): StringBuilder = appendInternalName(builder.append('L')).append(';')

@Contract(pure = true)
fun PsiClass.findMethodsByInternalName(internalName: String, checkBases: Boolean = false): Array<PsiMethod> {
    return if (internalName == INTERNAL_CONSTRUCTOR_NAME) {
        constructors
    } else {
        findMethodsByName(internalName, checkBases)
    }
}

// Method

@get:Contract(pure = true)
val PsiMethod.internalName: String
    get() = if (isConstructor) INTERNAL_CONSTRUCTOR_NAME else name

@get:Contract(pure = true)
val PsiMethod.descriptor: String
    get() = appendDescriptor(StringBuilder()).toString()

fun PsiMethod.appendDescriptor(builder: StringBuilder): StringBuilder {
    builder.append('(')
    for (parameter in parameterList.parameters) {
        parameter.typeElement?.type?.appendDescriptor(builder)
    }
    builder.append(')')
    return (returnType ?: PsiType.VOID).appendDescriptor(builder)
}

// Field
@get:Contract(pure = true)
val PsiField.descriptor: String
    get() = appendDescriptor(StringBuilder()).toString()

fun PsiField.appendDescriptor(builder: StringBuilder): StringBuilder = type.appendDescriptor(builder)
