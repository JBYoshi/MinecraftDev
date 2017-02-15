/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.platform.mcp.srg

import com.demonwav.mcdev.util.MemberReference
import com.demonwav.mcdev.util.fullQualifiedName
import com.demonwav.mcdev.util.qualifiedMemberReference
import com.demonwav.mcdev.util.simpleQualifiedMemberReference
import com.google.common.collect.ImmutableBiMap
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.Contract
import java.nio.file.Files
import java.nio.file.Path

internal class McpSrgMap private constructor(
        private val classMap: ImmutableBiMap<String, String>,
        private val fieldMap: ImmutableBiMap<MemberReference, MemberReference>,
        private val methodMap: ImmutableBiMap<MemberReference, MemberReference>) {

    @Contract(pure = true) fun getSrgClass(fullQualifiedName: String) = classMap[fullQualifiedName]
    @Contract(pure = true) fun mapToSrgClass(fullQualifiedName: String) = getSrgClass(fullQualifiedName) ?: fullQualifiedName
    @Contract(pure = true) fun findSrgClass(psiClass: PsiClass) = getSrgClass(psiClass.fullQualifiedName)

    @Contract(pure = true) fun getSrgField(reference: MemberReference) = fieldMap[reference]
    @Contract(pure = true) fun mapToSrgField(reference: MemberReference) = getSrgField(reference) ?: reference
    @Contract(pure = true) fun findSrgField(field: PsiField) = getSrgField(field.simpleQualifiedMemberReference)

    @Contract(pure = true) fun getSrgMethod(reference: MemberReference) = methodMap[reference]
    @Contract(pure = true) fun mapToSrgMethod(reference: MemberReference) = methodMap[reference] ?: reference
    @Contract(pure = true) fun findSrgMethod(method: PsiMethod) = getSrgMethod(method.qualifiedMemberReference)

    @Contract(pure = true) fun getMcpClass(fullQualifiedName: String) = classMap.inverse()[fullQualifiedName]
    @Contract(pure = true) fun mapToMcpClass(fullQualifiedName: String) = getMcpClass(fullQualifiedName) ?: fullQualifiedName

    @Contract(pure = true) fun getMcpField(reference: MemberReference) = fieldMap.inverse()[reference]
    @Contract(pure = true) fun mapToMcpField(reference: MemberReference) = getMcpField(reference) ?: reference

    @Contract(pure = true) fun getMcpMethod(reference: MemberReference) = methodMap.inverse()[reference]
    @Contract(pure = true) fun mapToMcpMethod(reference: MemberReference) = getMcpMethod(reference) ?: reference

    internal companion object {

        internal fun parse(path: Path): McpSrgMap {
            val classMapBuilder = ImmutableBiMap.builder<String, String>()
            val fieldMapBuilder = ImmutableBiMap.builder<MemberReference, MemberReference>()
            val methodMapBuilder = ImmutableBiMap.builder<MemberReference, MemberReference>()

            for (line in Files.readAllLines(path)) {
                val parts = line.split(' ')
                when (parts[0]) {
                    "CL:" -> classMapBuilder.put(parts[1].replace('/', '.'), parts[2].replace('/', '.'))
                    "FD:" -> fieldMapBuilder.put(SrgMemberReference.parse(parts[1]), SrgMemberReference.parse(parts[2]))
                    "MD:" -> methodMapBuilder.put(
                            SrgMemberReference.parse(parts[1], parts[2]),
                            SrgMemberReference.parse(parts[3], parts[4])
                    )
                }
            }

            return McpSrgMap(classMapBuilder.build(), fieldMapBuilder.build(), methodMapBuilder.build())
        }

    }

}
