/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.editor.markup.EffectType
import org.jetbrains.annotations.Contract

@State(name = "MinecraftSettings", storages = arrayOf(Storage("minecraft_dev.xml")))
class MinecraftSettings : PersistentStateComponent<MinecraftSettings.State> {

    data class State(
        var isShowProjectPlatformIcons: Boolean = true,
        var isShowEventListenerGutterIcons: Boolean = true,
        var isShowChatColorGutterIcons: Boolean = true,
        var isShowChatColorUnderlines: Boolean = false,
        var isEnableSideOnlyChecks: Boolean = true,
        var underlineType: MinecraftSettings.UnderlineType = MinecraftSettings.UnderlineType.DOTTED
    )

    private var state = State()

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {
        this.state = state
    }

    // State mappings
    var isShowProjectPlatformIcons: Boolean
        get() = state.isShowProjectPlatformIcons
        set(showProjectPlatformIcons) {
            state.isShowProjectPlatformIcons = showProjectPlatformIcons
        }

    var isShowEventListenerGutterIcons: Boolean
        get() = state.isShowEventListenerGutterIcons
        set(showEventListenerGutterIcons) {
            state.isShowEventListenerGutterIcons = showEventListenerGutterIcons
        }

    var isShowChatColorGutterIcons: Boolean
        get() = state.isShowChatColorGutterIcons
        set(showChatColorGutterIcons) {
            state.isShowChatColorGutterIcons = showChatColorGutterIcons
        }

    var isShowChatColorUnderlines: Boolean
        get() = state.isShowChatColorUnderlines
        set(showChatColorUnderlines) {
            state.isShowChatColorUnderlines = showChatColorUnderlines
        }

    var underlineType: UnderlineType
        get() = state.underlineType
        set(underlineType) {
            state.underlineType = underlineType
        }

    val underlineTypeIndex: Int
        get() {
            val type = underlineType
            return (0..UnderlineType.values().size - 1).firstOrNull { type == UnderlineType.values()[it] } ?: 0
        }

    var isEnableSideOnlyChecks: Boolean
        get() = state.isEnableSideOnlyChecks
        set(enableSideOnlyChecks) {
            state.isEnableSideOnlyChecks = enableSideOnlyChecks
        }

    enum class UnderlineType(private val regular: String, val effectType: EffectType) {

        NORMAL("Normal", EffectType.LINE_UNDERSCORE),
        BOLD("Bold", EffectType.BOLD_LINE_UNDERSCORE),
        DOTTED("Dotted", EffectType.BOLD_DOTTED_LINE),
        BOXED("Boxed", EffectType.BOXED),
        ROUNDED_BOXED("Rounded Boxed", EffectType.ROUNDED_BOX),
        WAVED("Waved", EffectType.WAVE_UNDERSCORE);

        @Contract(pure = true)
        override fun toString(): String {
            return regular
        }
    }

    companion object {
        @JvmStatic
        val instance: MinecraftSettings
            get() = ServiceManager.getService(MinecraftSettings::class.java)
    }
}
