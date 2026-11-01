package io.github.mariechantal.heuresolaire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Changement d'heure légale (été/hiver, fuseau) : on recalcule le décalage et on reprogramme le réveil. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SolarTimeService.requestUpdate(context)
        Reveil.programmer(context)
    }
}
