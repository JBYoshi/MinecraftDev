/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.insight.generation;

import com.intellij.codeInsight.generation.actions.BaseGenerateAction;

public class GenerateEventListenerAction extends BaseGenerateAction {
    public GenerateEventListenerAction() {
        super(new GenerateEventListenerHandler());
    }
}
