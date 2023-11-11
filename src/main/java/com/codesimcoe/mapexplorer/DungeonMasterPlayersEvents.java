package com.codesimcoe.mapexplorer;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * Dungeon Master events for players
 */
public interface DungeonMasterPlayersEvents {

  void onCommit(Image mapImage, Image fogImage);

  void onBackgroundColorChanged(Color color);
}