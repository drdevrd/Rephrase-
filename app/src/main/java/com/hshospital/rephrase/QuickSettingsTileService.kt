package com.hshospital.rephrase

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        RephraseAccessibilityService.isEnabled = !RephraseAccessibilityService.isEnabled
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        if (RephraseAccessibilityService.isEnabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "RePhrase ON"
            tile.contentDescription = "RePhrase is active"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "RePhrase OFF"
            tile.contentDescription = "RePhrase is disabled"
        }
        tile.updateTile()
    }
}
