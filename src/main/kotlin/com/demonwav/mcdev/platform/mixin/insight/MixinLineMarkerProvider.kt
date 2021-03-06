/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.platform.mixin.insight

import com.demonwav.mcdev.asset.MixinAssets
import com.demonwav.mcdev.platform.mixin.util.MixinUtils
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.util.FunctionUtil
import java.awt.event.MouseEvent
import javax.swing.Icon

class MixinLineMarkerProvider : LineMarkerProviderDescriptor(), GutterIconNavigationHandler<PsiIdentifier> {

    override fun getName() = "Mixin line marker"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiIdentifier>? {
        if (element !is PsiClass) {
            return null
        }

        val identifier = element.nameIdentifier ?: return null
        return if (MixinUtils.getMixinAnnotation(element) != null) {
            LineMarker(identifier, this)
        } else {
            null
        }
    }

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: Collection<LineMarkerInfo<PsiElement>>) {
    }

    override fun navigate(e: MouseEvent, elt: PsiIdentifier) {
        val psiClass = elt.parent as? PsiClass ?: return
        val targets = MixinUtils.getAllMixedClasses(psiClass).values
        if (targets.isNotEmpty()) {
            PsiElementListNavigator.openTargets(e, targets.toTypedArray(),
                    "Choose target class of ${psiClass.name!!}", null, PsiClassListCellRenderer.INSTANCE)
        }
    }

    private class LineMarker(identifier: PsiIdentifier, navHandler: GutterIconNavigationHandler<PsiIdentifier>)
        : MergeableLineMarkerInfo<PsiIdentifier>(identifier, identifier.textRange, ICON,
            Pass.LINE_MARKERS, TOOLTIP_FUNCTION, navHandler, GutterIconRenderer.Alignment.RIGHT) {

        override fun canMergeWith(info: MergeableLineMarkerInfo<*>) = info is LineMarker
        override fun getCommonTooltip(infos: List<MergeableLineMarkerInfo<PsiElement>>) = TOOLTIP_FUNCTION
        override fun getCommonIcon(infos: List<MergeableLineMarkerInfo<PsiElement>>) = ICON

        private companion object {
            @JvmField val ICON: Icon = MixinAssets.MIXIN_CLASS_ICON
            @JvmField val TOOLTIP_FUNCTION = FunctionUtil.constant<Any, String>("Go to target class")
        }

    }


}
