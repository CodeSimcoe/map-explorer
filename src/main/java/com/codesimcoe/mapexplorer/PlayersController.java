package com.codesimcoe.mapexplorer;

import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Affine;
import javafx.stage.Stage;

import java.nio.IntBuffer;

public class PlayersController implements DungeonMasterPlayersEvents {

    private final Stage stage;

    @FXML
    private Pane root;

    @FXML
    private Canvas canvas;

    private GraphicsContext graphicsContext;

    private int width;
    private int height;

    private int[] imagePixels;
    private int[] fogPixels;
    private int[] resultPixels;
    private WritablePixelFormat<IntBuffer> pixelFormat;
    private WritableImage image;
    private int length;

    private double dragStartX;
    private double dragStartY;

    private final Affine identity = new Affine();

    public PlayersController() {
        this.stage = new Stage();
    }

    @FXML
    private void initialize() {

        Scene playersScene = new Scene(this.root, 800, 800);
        this.stage.setScene(playersScene);
        this.stage.show();

        this.canvas.widthProperty().bind(this.root.widthProperty());
        this.canvas.heightProperty().bind(this.root.heightProperty());

        this.graphicsContext = this.canvas.getGraphicsContext2D();

        // When ENTER is pressed, switch to fullscreen
        playersScene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER -> this.switchToFullScreen();
                case ESCAPE -> this.stage.setFullScreen(false);
            }
        });

        this.canvas.setOnScroll(e -> {
            double delta = e.getDeltaY();

            double scale = delta > 0 ? 1.1 : 0.9;
            Affine transform = this.graphicsContext.getTransform();
            transform.prependScale(scale, scale, e.getX(), e.getY());
            this.graphicsContext.setTransform(transform);

            this.draw();
        });

        this.canvas.setOnMousePressed(e -> {
            this.dragStartX = e.getX();
            this.dragStartY = e.getY();
        });

        this.canvas.setOnMouseDragged(e -> {
            double deltaX = e.getX() - this.dragStartX;
            double deltaY = e.getY() - this.dragStartY;

            Affine transform = this.graphicsContext.getTransform();
            transform.prependTranslation(deltaX, deltaY);
            this.graphicsContext.setTransform(transform);
            this.draw();

            this.dragStartX = e.getX();
            this.dragStartY = e.getY();
        });
    }

    private void switchToFullScreen() {
        this.stage.setFullScreen(true);
        this.draw();
    }

    @Override
    public void onCommit(final Image mapImage, final Image fogImage) {

        // Lazy initialization
        if (this.width == 0) {

            // Both image have same dimension
            this.width = (int) mapImage.getWidth();
            this.height = (int) mapImage.getHeight();

            // Merge map and fog to a single image
            this.image = new WritableImage(this.width, this.height);

            this.pixelFormat = PixelFormat.getIntArgbInstance();

            // Initialize pixel arrays
            this.length = this.width * this.height;
            this.imagePixels = new int[this.length];
            this.fogPixels = new int[this.length];
            this.resultPixels = new int[this.length];

            // Default zoom
            double ratio = this.canvas.getWidth() / this.width;
            Affine transform = this.graphicsContext.getTransform();
            transform.prependScale(ratio, ratio);
            this.graphicsContext.setTransform(transform);
        }

        // Read content to array
        mapImage.getPixelReader().getPixels(
          0,
          0,
          this.width,
          this.height,
          this.pixelFormat,
          this.imagePixels,
          0,
          this.width
        );
        fogImage.getPixelReader().getPixels(
            0,
            0,
            this.width,
            this.height,
            this.pixelFormat,
            this.fogPixels,
            0,
            this.width
        );

        // Apply blend / merge
        for (int i = 0; i < this.length; i++) {

            int map = this.imagePixels[i];
            int fog = this.fogPixels[i];

            int blend;
            int fogAlpha = fog >> 24;
            if (fogAlpha == 0) {
                blend = map;
            } else {
                // Black ARGB
                blend = fog;
            }

          this.resultPixels[i] = blend;
        }

        this.image.getPixelWriter().setPixels(
            0,
            0,
            this.width,
            this.height,
            this.pixelFormat,
            this.resultPixels,
            0,
            this.width
        );

        this.draw();
    }

    private void draw() {
        Affine transform = this.graphicsContext.getTransform();
        this.graphicsContext.setTransform(this.identity);
        this.graphicsContext.clearRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());

        this.graphicsContext.setTransform(transform);
        this.graphicsContext.drawImage(this.image, 0, 0);
    }
}