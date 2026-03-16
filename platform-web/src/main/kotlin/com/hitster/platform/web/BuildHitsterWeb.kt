package com.hitster.platform.web

import com.github.xpenatan.gdx.backends.teavm.config.AssetFileHandle
import com.github.xpenatan.gdx.backends.teavm.config.TeaBuildConfiguration
import com.github.xpenatan.gdx.backends.teavm.config.TeaBuilder
import com.github.xpenatan.gdx.backends.teavm.config.TeaTargetType
import org.teavm.tooling.TeaVMTool
import org.teavm.vm.TeaVMOptimizationLevel
import java.io.File

object BuildHitsterWeb {
    @JvmStatic
    fun main(args: Array<String>) {
        val buildConfiguration = TeaBuildConfiguration().apply {
            assetsPath.add(AssetFileHandle("../platform-android/src/main/assets"))
            webappPath = File("build/dist").canonicalPath
            targetType = TeaTargetType.JAVASCRIPT
        }
        TeaBuilder.config(buildConfiguration)

        TeaVMTool().apply {
            setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
            setMainClass(HitsterWebLauncher::class.java.name)
            setObfuscated(false)
            TeaBuilder.build(this)
        }
    }
}
