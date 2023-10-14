package com.codesimcoe.mapexplorer;

import javafx.scene.image.Image;

/**
 * Dungeon Master events for players
 */
public interface DungeonMasterPlayersEvents {

    void onCommit(Image mapImage, Image fogImage);
}