package com.codesimcoe.mapexplorer;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class MapExplorer {

    private static final String DUNGEON_MASTER_FXML = "/fxml/DungeonMasterApp.fxml";
    private static final String PLAYERS_FXML = "/fxml/PlayersApp.fxml";

    // Dungeon Master
    private final Stage dmStage;

    public MapExplorer(final Stage dmStage) throws IOException {

        // DM's stage
        this.dmStage = dmStage;

        URL dmResource = MapExplorer.class.getResource(DUNGEON_MASTER_FXML);
        FXMLLoader dmLoader = new FXMLLoader(dmResource);
        dmLoader.load();

        DungeonMasterController dmController = dmLoader.getController();
        Parent dmRoot = dmLoader.getRoot();

        // DM's Scene
        Scene dmScene = new Scene(dmRoot);
        this.dmStage.setScene(dmScene);

        // Players' stage
        URL playersResource = MapExplorer.class.getResource(PLAYERS_FXML);
        FXMLLoader playersLoader = new FXMLLoader(playersResource);
        playersLoader.load();

        // Players
        PlayersController playersController = playersLoader.getController();
        Parent playersRoot = playersLoader.getRoot();

        dmController.setDungeonMasterPlayersEvents(playersController);

        Stage playersStage = new Stage();
        Scene playersScene = new Scene(playersRoot, 800, 800);
        playersStage.setScene(playersScene);
        playersStage.show();

        dmStage.setOnCloseRequest(e -> this.exitApplication());
    }

    private void exitApplication() {
        System.exit(0);
    }

    public void start() {
        this.dmStage.show();
    }
}