package com.luxwallet.app.core.ui.component

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.luxwallet.app.R
import com.luxwallet.app.engine.PaydayStatus

enum class LumiMood { CALM, HAPPY, FOCUS, PROUD, EXCITED, CURIOUS, NERVOUS, SHOCKED, SAD, ANGRY }
fun budgetMood(percent: Double, completed: Boolean = false): LumiMood = when {
    percent > 120 -> LumiMood.ANGRY
    percent > 100 -> LumiMood.SAD
    percent >= 75 -> LumiMood.NERVOUS
    completed -> LumiMood.PROUD
    percent <= 50 -> LumiMood.HAPPY
    else -> LumiMood.CALM
}
fun lumiMood(status: PaydayStatus?): LumiMood = when {
    status == null || status.expired -> LumiMood.CALM
    status.freeRemaining < 0 || (status.dailyBudget <= 0 && status.spentToday > 0) -> LumiMood.ANGRY
    status.dailyBudget <= 0 -> LumiMood.CALM
    else -> budgetMood(status.usedPercent)
}
/** Evidence is ordered: serious budget risk, recent events, then daily progress. Review only guides an inactive plan. */
fun companionMood(status: PaydayStatus?, review: Boolean, spike: Boolean, income: Boolean, completed: Boolean = false): LumiMood = when {
    status != null && !status.expired && (status.freeRemaining < 0 || status.usedPercent >= 75 || (status.dailyBudget <= 0 && status.spentToday > 0)) -> lumiMood(status)
    review && (status == null || status.expired) -> LumiMood.CURIOUS
    spike -> LumiMood.SHOCKED
    income -> LumiMood.EXCITED
    completed -> LumiMood.PROUD
    else -> lumiMood(status)
}
@Composable fun Lumi(mood: LumiMood = LumiMood.CALM, modifier: Modifier = Modifier) {
    val atlas = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.lumi_covers)
    val cell = when(mood) {
        LumiMood.HAPPY -> 0; LumiMood.PROUD -> 1; LumiMood.CALM -> 2
        LumiMood.EXCITED -> 3; LumiMood.CURIOUS -> 4; LumiMood.NERVOUS, LumiMood.FOCUS -> 5
        LumiMood.SHOCKED -> 6; LumiMood.SAD -> 7; LumiMood.ANGRY -> 8
    }
    androidx.compose.foundation.Canvas(modifier.clip(RoundedCornerShape(24)).semantics { contentDescription = "Lumi · ${mood.name.lowercase()}" }) {
        drawImage(atlas, srcOffset = androidx.compose.ui.unit.IntOffset(listOf(50, 448, 845)[cell % 3], listOf(31, 431, 825)[cell / 3]),
            srcSize = androidx.compose.ui.unit.IntSize(if (cell % 3 == 1) 357 else 358, if (cell / 3 == 2) 364 else 358),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
    }
}
object LumiLauncher {
    val names = listOf("LumiCalm", "LumiHappy", "LumiFocus", "LumiProud", "LumiExcited", "LumiCurious", "LumiNervous", "LumiShocked", "LumiSad", "LumiAngry")
    fun alias(mood: LumiMood) = "Lumi" + (if (mood == LumiMood.FOCUS) "Nervous" else mood.name.lowercase().replaceFirstChar { it.uppercase() })
    fun apply(context: Context, mood: LumiMood) {
        val manager = context.packageManager
        val components = names.map { ComponentName(context.packageName, "com.luxwallet.app.$it") }
        val target = ComponentName(context.packageName, "com.luxwallet.app.${alias(mood)}")
        if (manager.getComponentEnabledSetting(target) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
            components.filter { it != target }.all { manager.getComponentEnabledSetting(it) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED }) return
        // Enable the new icon first so older launchers never see zero launchable components.
        manager.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        components.filter { it != target }.forEach {
            manager.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
