package io.github.mariechantal.heuresolaire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Changement d'heure légale (été/hiver, fuseau) : on recalcule tout de suite le décalage. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SolarTimeService.requestUpdate(context)
    }
}
