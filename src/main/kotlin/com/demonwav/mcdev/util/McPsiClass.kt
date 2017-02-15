/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

@file:JvmName("McPsiClass")
package com.demonwav.mcdev.util

import com.intellij.navigation.AnonymousElementProvider
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.Contract

// Type

@get:Contract(pure = true)
internal val PsiClassType.fullQualifiedName
    get() = resolve()!!.fullQualifiedName

// Class

@get:Contract(pure = true)
internal val PsiClass.outerQualifiedName
    get() = if (containingClass == null) qualifiedName else null

@get:Contract(pure = true)
internal val PsiClass.fullQualifiedName
    get() = outerQualifiedName ?: buildQualifiedName(StringBuilder()).toString()

internal fun PsiClass.appendFullQualifiedName(builder: StringBuilder): StringBuilder {
    return outerQualifiedName?.let { builder.append(it) } ?: buildQualifiedName(builder)
}

private fun PsiClass.buildQualifiedName(builder: StringBuilder): StringBuilder {
    buildInnerName(builder, PsiClass::outerQualifiedName)
    return builder
}

@get:Contract(pure = true)
private val PsiClass.outerShortName
    get() = if (containingClass == null) name else null

@get:Contract(pure = true)
internal val PsiClass.shortName: String
    get() {
        outerShortName?.let { return it }
        val builder = StringBuilder()
        buildInnerName(builder, PsiClass::outerShortName, '.')
        return builder.toString()
    }

internal inline fun PsiClass.buildInnerName(builder: StringBuilder, getName: (PsiClass) -> String?, separator: Char = '$') {
    var currentClass: PsiClass = this
    var parentClass: PsiClass?
    var name: String?
    val list = ArrayList<String>()

    do {
        parentClass = currentClass.containingClass
        if (parentClass != null) {
            // Add named inner class
            list.add(currentClass.name!!)
        } else {
            parentClass = currentClass.parent.findContainingClass()!!

            // Add index of anonymous class to list
            list.add(parentClass.getAnonymousIndex(currentClass).toString())
        }

        currentClass = parentClass
        name = getName(currentClass)
    } while (name == null)

    // Append name of outer class
    builder.append(name)

    // Append names for all inner classes
    for (i in list.lastIndex downTo 0) {
        builder.append(separator).append(list[i])
    }
}

@Contract(pure = true)
internal fun findQualifiedClass(fullQualifiedName: String, context: PsiElement): PsiClass? {
    return findQualifiedClass(context.project, fullQualifiedName, context.resolveScope)
}

@Contract(pure = true)
@JvmOverloads
internal fun findQualifiedClass(project: Project, fullQualifiedName: String,
                                scope: GlobalSearchScope = GlobalSearchScope.allScope(project)): PsiClass? {
    var innerPos = fullQualifiedName.indexOf('$')
    if (innerPos == -1) {
        return JavaPsiFacade.getInstance(project).findClass(fullQualifiedName, scope)
    }

    var currentClass = JavaPsiFacade.getInstance(project).findClass(fullQualifiedName.substring(0, innerPos), scope) ?: return null
    var outerPos: Int

    while (true) {
        outerPos = innerPos + 1
        innerPos = fullQualifiedName.indexOf('$', outerPos)

        if (innerPos == -1) {
            return currentClass.findInnerClass(fullQualifiedName.substring(outerPos))
        } else {
            currentClass = currentClass.findInnerClass(fullQualifiedName.substring(outerPos, innerPos)) ?: return null
        }
    }
}

private fun PsiClass.findInnerClass(name: String): PsiClass? {
    val anonymousIndex = name.toIntOrNull()
    return if (anonymousIndex == null) {
        // Named inner class
        findInnerClassByName(name, false)
    } else {
        val anonymousElements = anonymousElements ?: return null
        if (anonymousIndex <= anonymousElements.size) {
            anonymousElements[anonymousIndex - 1] as PsiClass
        } else {
            null
        }
    }
}

@Contract(pure = true)
internal fun PsiElement.getAnonymousIndex(anonymousElement: PsiElement): Int {
    // Attempt to find name for anonymous class
    for ((i, element) in anonymousElements!!.withIndex()) {
        if (manager.areElementsEquivalent(element, anonymousElement)) {
            return i + 1
        }
    }

    throw IllegalStateException("Failed to determine anonymous class for $anonymousElement")
}

@get:Contract(pure = true)
internal val PsiElement.anonymousElements: Array<PsiElement>?
    get() {
        for (provider in Extensions.getExtensions(AnonymousElementProvider.EP_NAME)) {
            val elements = provider.getAnonymousElements(this)
            if (elements.isNotEmpty()) {
                return elements
            }
        }

        return null
    }

// Inheritance

@Contract(pure = true)
internal fun PsiClass.extendsOrImplements(qualifiedClassName: String): Boolean {
    val aClass = JavaPsiFacade.getInstance(project).findClass(qualifiedClassName, resolveScope) ?: return false
    return manager.areElementsEquivalent(this, aClass) || this.isInheritor(aClass, true)
}

internal fun PsiClass.addImplements(qualifiedClassName: String) {
    val project = project
    val listenerClass = JavaPsiFacade.getInstance(project).findClass(qualifiedClassName, resolveScope) ?: return

    val elementFactory = JavaPsiFacade.getElementFactory(project)
    val element = elementFactory.createClassReferenceElement(listenerClass)

    val referenceList = implementsList
    if (referenceList != null) {
        referenceList.add(element)
    } else {
        add(elementFactory.createReferenceList(arrayOf(element)))
    }
}

// Modifier list

@Contract(pure = true)
internal fun PsiModifierListOwner.findAnnotation(qualifiedName: String): PsiAnnotation? {
    return modifierList?.findAnnotation(qualifiedName)
}

// Member

@get:Contract(pure = true)
internal val PsiMember.accessModifier
    get() = when {
        hasModifierProperty(PsiModifier.PUBLIC) -> PsiModifier.PUBLIC
        hasModifierProperty(PsiModifier.PROTECTED) -> PsiModifier.PROTECTED
        hasModifierProperty(PsiModifier.PACKAGE_LOCAL) -> PsiModifier.PACKAGE_LOCAL
        hasModifierProperty(PsiModifier.PRIVATE) -> PsiModifier.PRIVATE
        else -> PsiModifier.PUBLIC
    }
