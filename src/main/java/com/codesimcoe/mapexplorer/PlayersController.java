package com.codesimcoe.mapexplorer;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

import java.nio.IntBuffer;

public class PlayersController implements DungeonMasterPlayersEvents {

    @FXML
    private Pane playersPane;

    @FXML
    private ImageView mapImageView;

    private int width;
    private int height;

    @FXML
    private void initialize() {
        this.mapImageView.fitWidthProperty().bind(this.playersPane.widthProperty());
        this.mapImageView.fitHeightProperty().bind(this.playersPane.heightProperty());

        this.mapImageView.setOnMousePressed(e -> {

            Point2D mousePress = this.imageViewToImage(new Point2D(e.getX(), e.getY()));
            this.mouseDown.set(mousePress);
        });

        this.mapImageView.setOnScroll(e -> {
            double delta = e.getDeltaY();
            Rectangle2D viewport = this.mapImageView.getViewport();

            double scale = this.clamp(Math.pow(1.01, delta),

                // don't scale so we're zoomed in to fewer than MIN_PIXELS in any direction:
                Math.min(10 / viewport.getWidth(), 10 / viewport.getHeight()),

                // don't scale so that we're bigger than image dimensions:
                Math.max(this.width / viewport.getWidth(), this.height / viewport.getHeight())

            );

            Point2D mouse = this.imageViewToImage(new Point2D(e.getX(), e.getY()));

            double newWidth = viewport.getWidth() * scale;
            double newHeight = viewport.getHeight() * scale;

            // To keep the visual point under the mouse from moving, we need
            // (x - newViewportMinX) / (x - currentViewportMinX) = scale
            // where x is the mouse X coordinate in the image

            // solving this for newViewportMinX gives

            // newViewportMinX = x - (x - currentViewportMinX) * scale

            // we then clamp this value so the image never scrolls out
            // of the imageview:

            double newMinX = this.clamp(mouse.getX() - (mouse.getX() - viewport.getMinX()) * scale,
                    0, this.width - newWidth);
            double newMinY = this.clamp(mouse.getY() - (mouse.getY() - viewport.getMinY()) * scale,
                    0, this.height - newHeight);

            this.mapImageView.setViewport(new Rectangle2D(newMinX, newMinY, newWidth, newHeight));
        });
    }

    @Override
    public void onCommit(final Image mapImage, final Image fogImage) {

        // Both image have same dimension
        this.width = (int) mapImage.getWidth();
        this.height = (int) mapImage.getHeight();

        this.reset(this.width, this.height);

        // Merge map and fog to a single image
        WritableImage image = new WritableImage(this.width, this.height);

        WritablePixelFormat<IntBuffer> pixelFormat = PixelFormat.getIntArgbInstance();

        // Initialize pixel arrays
        int length = this.width * this.height;
        int[] imagePixels = new int[length];
        int[] fogPixels = new int[length];
        int[] resultPixels = new int[length];

        // Read content to array
        mapImage.getPixelReader().getPixels(0, 0, this.width, this.height, pixelFormat, imagePixels, 0, this.width);
        fogImage.getPixelReader().getPixels(0, 0, this.width, this.height, pixelFormat, fogPixels, 0, this.width);

        // Apply blend / merge
        for (int i = 0; i < length; i++) {

            int map = imagePixels[i];
            int fog = fogPixels[i];

            int blend;
            int fogAlpha = fog >> 24;
            if (fogAlpha == 0) {
                blend = map;
            } else {
                // Black ARGB
                blend = fog;
            }

            resultPixels[i] = blend;
        }

        image.getPixelWriter().setPixels(0, 0, this.width, this.height, pixelFormat, resultPixels, 0, this.width);

        this.mapImageView.setImage(image);
    }

    @Override
    public void show() {
        // TODO Auto-generated method stub

    }

    private final ObjectProperty<Point2D> mouseDown = new SimpleObjectProperty<>();

    @FXML
    private void manageMouseDrag(final MouseEvent event) {
        System.out.println("PlayersController.manageMouseDrag()");

//        Rectangle2D viewport = this.mapImageView.getViewport();

//        System.out.println(viewport);

        Point2D dragPoint = this.imageViewToImage(new Point2D(event.getX(), event.getY()));
        this.shift(dragPoint.subtract(this.mouseDown.get()));
        this.mouseDown.set(this.imageViewToImage(new Point2D(event.getX(), event.getY())));
    }

    private void shift(final Point2D delta) {
        Rectangle2D viewport = this.mapImageView.getViewport();

        double width = this.mapImageView.getImage().getWidth();
        double height = this.mapImageView.getImage().getHeight();

        double maxX = width - viewport.getWidth();
        double maxY = height - viewport.getHeight();

        double minX = this.clamp(viewport.getMinX() - delta.getX(), 0, maxX);
        double minY = this.clamp(viewport.getMinY() - delta.getY(), 0, maxY);

        this.mapImageView.setViewport(new Rectangle2D(minX, minY, viewport.getWidth(), viewport.getHeight()));
    }

    private double clamp(final double value, final double min, final double max) {

        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    // convert mouse coordinates in the imageView to coordinates in the actual
    // image:
    private Point2D imageViewToImage(final Point2D imageViewCoordinates) {
        double xProportion = imageViewCoordinates.getX() / this.mapImageView.getBoundsInLocal().getWidth();
        double yProportion = imageViewCoordinates.getY() / this.mapImageView.getBoundsInLocal().getHeight();

        Rectangle2D viewport = this.mapImageView.getViewport();
        return new Point2D(
            viewport.getMinX() + xProportion * viewport.getWidth(),
            viewport.getMinY() + yProportion * viewport.getHeight()
        );
    }

    private void reset(final double width, final double height) {
        this.mapImageView.setViewport(new Rectangle2D(0, 0, width, height));
    }
}