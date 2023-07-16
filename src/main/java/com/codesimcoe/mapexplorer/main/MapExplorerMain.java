package com.codesimcoe.mapexplorer.main;

import com.codesimcoe.mapexplorer.MapExplorer;

import javafx.application.Application;
import javafx.stage.Stage;

public class MapExplorerMain extends Application {

    public static void main(final String[] args) {
        launch(args);
    }

    @Override
    public void start(final Stage primaryStage) throws Exception {

        MapExplorer mapExplorer = new MapExplorer(primaryStage);
        mapExplorer.start();
    }
}