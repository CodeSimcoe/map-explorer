package com.codesimcoe.mapexplorer;

import com.codesimcoe.mapexplorer.save.SaveData;
import com.codesimcoe.mapexplorer.save.SaveUtils;
import com.codesimcoe.mapexplorer.style.ColorConstants;
import com.codesimcoe.mapexplorer.style.StyleConstants;
import com.codesimcoe.mapexplorer.style.StyleUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DungeonMasterController {

    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 800;

    private static final int MIN_TOOL_SIZE = 5;
    private static final int MAX_TOOL_SIZE = 250;
    private static final int TOOL_SIZE_STEP = 15;

    private static final double MIN_FOG_OPACITY = 0;
    private static final double MAX_FOG_OPACITY = 1;
    private static final double FOG_STEP = 0.1;

    private static final double ZOOM_SCALE_STEP = 1.10;

    private static final String SAVE_FILE_EXTENSION = "me";
    private static final Set<String> ALLOWED_MAP_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final ExtensionFilter EXTENSION_FILTER = new ExtensionFilter(
      "Map Explorer",
      "*." + SAVE_FILE_EXTENSION
    );

    @FXML
    private StackPane dmStackPane;

    @FXML
    private Canvas fogCanvas;

    @FXML
    private Canvas imageCanvas;

    @FXML
    private Slider fogOpacitySlider;

    @FXML
    private Slider toolSizeSlider;

    @FXML
    private Rectangle rectangleToolOverlay;

    @FXML
    private Circle circleToolOverlay;

    @FXML
    private ToggleButton toolSquareShapeToggleButton;

    @FXML
    private ToggleButton toolCircleShapeToggleButton;

    @FXML
    private ToggleGroup toolShapeToggleGroup;

    @FXML
    private ToggleButton fogToolToggleButton;

    @FXML
    private ToggleButton eraserToolToggleButton;

    @FXML
    private ToggleGroup toolModeToggleGroup;

    @FXML
    private ColorPicker backgroundColorPicker;

    private ObjectProperty<ToolMode> toolModeProperty;

    private final DoubleProperty fogOpacityProperty;
    private final IntegerProperty toolSizeProperty;

    private GraphicsContext fogGraphicsContext;
    private GraphicsContext imageGraphicsContext;

    private final Affine identityTransform = new Affine();

    private WritableImage fogImage;
    private Image mapImage;

    private int imageWidth;
    private int imageHeight;

    private double startX;
    private double startY;

    private DungeonMasterPlayersEvents dungeonMasterPlayersEvents;

    public DungeonMasterController() {
        // Default values
        this.fogOpacityProperty = new SimpleDoubleProperty(0.5);
        this.toolSizeProperty = new SimpleIntegerProperty(50);
    }

    @FXML
    private void initialize() {

        // Canvas
        this.imageCanvas.setWidth(CANVAS_WIDTH);
        this.imageCanvas.setHeight(CANVAS_HEIGHT);
        this.fogCanvas.setWidth(CANVAS_WIDTH);
        this.fogCanvas.setHeight(CANVAS_HEIGHT);

        this.fogGraphicsContext = this.fogCanvas.getGraphicsContext2D();
        this.imageGraphicsContext = this.imageCanvas.getGraphicsContext2D();

        // Tools binding
        ObjectProperty<ToolShape> toolShapeProperty = new SimpleObjectProperty<>();
        toolShapeProperty.addListener((observable, oldValue, newValue) -> {
            switch (newValue) {
                case SQUARE -> this.toolSquareShapeToggleButton.setSelected(true);
                case CIRCLE -> this.toolCircleShapeToggleButton.setSelected(true);
                default -> throw new IllegalStateException("Unexpected value: " + newValue);
            }
        });
        this.toolShapeToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == this.toolSquareShapeToggleButton) {
                toolShapeProperty.set(ToolShape.SQUARE);
            } else if (newValue == this.toolCircleShapeToggleButton) {
                toolShapeProperty.set(ToolShape.CIRCLE);
            } else {
                // No other tool shapes yet
            }
        });
        toolShapeProperty.set(ToolShape.SQUARE);

        this.toolModeProperty = new SimpleObjectProperty<>();
        this.toolModeProperty.addListener((observable, oldValue, newValue) -> {

            boolean toolOverlayVisible;

            switch (newValue) {
                case ERASER -> {
                    toolOverlayVisible = true;
                    this.eraserToolToggleButton.setSelected(true);
                }
                case FOG -> {
                    toolOverlayVisible = true;
                    this.fogToolToggleButton.setSelected(true);
                }
                default -> toolOverlayVisible = false;
            }

            this.setToolVisible(toolOverlayVisible);
        });

        this.toolModeToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == this.fogToolToggleButton) {
                this.toolModeProperty.set(ToolMode.FOG);
                StyleUtils.setStyleClass(this.rectangleToolOverlay, StyleConstants.CLASS_BRUSH_OVERLAY);
                StyleUtils.setStyleClass(this.circleToolOverlay, StyleConstants.CLASS_BRUSH_OVERLAY);
            } else if (newValue == this.eraserToolToggleButton) {
                this.toolModeProperty.set(ToolMode.ERASER);
                StyleUtils.setStyleClass(this.rectangleToolOverlay, StyleConstants.CLASS_ERASER_OVERLAY);
                StyleUtils.setStyleClass(this.circleToolOverlay, StyleConstants.CLASS_ERASER_OVERLAY);
            } else {
                // TODO
                this.toolModeProperty.set(ToolMode.NONE);
                this.rectangleToolOverlay.setStroke(Color.WHITE);
                this.circleToolOverlay.setStroke(Color.WHITE);
            }
        });
        this.toolModeProperty.set(ToolMode.ERASER);

        this.bindSlider(
          this.fogOpacitySlider,
          MIN_FOG_OPACITY,
          MAX_FOG_OPACITY,
          0,
          0.25,
          this.fogOpacityProperty
        );
        this.fogOpacitySlider.valueProperty().bindBidirectional(this.fogOpacityProperty);

        this.bindSlider(
          this.toolSizeSlider,
          MIN_TOOL_SIZE,
          MAX_TOOL_SIZE,
          100,
          50,
          this.toolSizeProperty
        );
        this.toolSizeSlider.valueProperty().bindBidirectional(this.toolSizeProperty);

        // Tool overlay
        this.rectangleToolOverlay.widthProperty().bind(this.toolSizeProperty);
        this.rectangleToolOverlay.heightProperty().bind(this.toolSizeProperty);

        this.circleToolOverlay.radiusProperty().bind(this.toolSizeProperty.divide(2));

        // Fog of war
        this.fogCanvas.opacityProperty().bind(this.fogOpacityProperty);

        // Background color
        this.backgroundColorPicker.setOnAction(e -> {
            Color color = this.backgroundColorPicker.getValue();
            this.dungeonMasterPlayersEvents.onBackgroundColorChanged(color);
        });

        // Help text, centered
        // Take the text size into account
        Text text = new Text("Drag and drop an image");
        Bounds boundsInLocal = text.getBoundsInLocal();

        // Draw text
        this.imageGraphicsContext.fillText(
            "Drag and drop an image",
            (CANVAS_WIDTH - boundsInLocal.getWidth()) / 2.0,
            (CANVAS_HEIGHT - boundsInLocal.getHeight()) / 2.0
        );
    }

    private ToolShape getSelectedToolShape() {
        // Get current selected tool shape
        ToggleButton selectedToolShape = (ToggleButton) this.toolShapeToggleGroup.getSelectedToggle();
        return selectedToolShape == this.toolSquareShapeToggleButton ? ToolShape.SQUARE : ToolShape.CIRCLE;
    }

    private void setToolVisible(final boolean visible) {
        ToolShape shape = this.getSelectedToolShape();
        if (shape == ToolShape.SQUARE) {
            this.rectangleToolOverlay.setVisible(visible);
            this.circleToolOverlay.setVisible(false);
        } else {
            this.circleToolOverlay.setVisible(visible);
            this.rectangleToolOverlay.setVisible(false);
        }
    }

    @FXML
    private void restoreFog() {
        int width = (int) this.mapImage.getWidth();
        int height = (int) this.mapImage.getHeight();
        int[] pixels = new int[width * height];

        // Opaque black
        Arrays.fill(pixels, ColorConstants.BLACK_ARGB);
        IntBuffer intBuffer = IntBuffer.wrap(pixels);
        this.fogImage.getPixelWriter().setPixels(
            0,
            0,
            width,
            height,
            PixelFormat.getIntArgbInstance(),
            intBuffer,
            0
        );

        this.draw();
    }

    @FXML
    private void commit() {
        // Send data to players
        this.dungeonMasterPlayersEvents.onCommit(this.mapImage, this.fogImage);
    }

    @FXML
    private void save() {

        int width = (int) this.mapImage.getWidth();
        int height = (int) this.mapImage.getHeight();
        int size = 4 * width * height;
        byte[] pixels = new byte[2 * size];

        this.mapImage.getPixelReader().getPixels(
            0,
            0,
            width,
            height,
            PixelFormat.getByteBgraInstance(),
            pixels,
            0,
            width * 4
        );

        this.fogImage.getPixelReader().getPixels(
            0,
            0,
            width,
            height,
            PixelFormat.getByteBgraInstance(),
            pixels,
            size,
            width * 4
        );

        // Open a save file chooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save map");
        fileChooser.getExtensionFilters().add(EXTENSION_FILTER);
        File file = fileChooser.showSaveDialog(this.getStageWindow());

        // Effective save
        SaveData data = new SaveData(width, height, pixels);
        try {
            SaveUtils.saveToFile(data, file.getAbsolutePath());
        } catch (IOException e) {
            // TODO
            throw new RuntimeException(e);
        }
    }

    @FXML
    private void openLoadSaveFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load map");
        fileChooser.getExtensionFilters().add(EXTENSION_FILTER);

        File file = fileChooser.showOpenDialog(this.getStageWindow());
        if (file != null) {
          this.loadSaveFile(file);
        }
    }

    private void loadSaveFile(final File file) {
        String extension = FilenameUtils.getExtension(file.getName());

        if (extension.equals(SAVE_FILE_EXTENSION)) {
            // Decompress
            try {
                SaveData data = SaveUtils.loadFromFile(file.getAbsolutePath());

                int size = 4 * data.width() * data.height();

                WritableImage map = new WritableImage(data.width(), data.height());
                map.getPixelWriter().setPixels(
                  0,
                  0,
                  data.width(),
                  data.height(),
                  PixelFormat.getByteBgraInstance(),
                  data.pixels(),
                  0,
                  data.width() * 4
                );
                this.mapImage = map;

                this.fogImage.getPixelWriter().setPixels(
                  0,
                  0,
                  data.width(),
                  data.height(),
                  PixelFormat.getByteBgraInstance(),
                  data.pixels(),
                  size,
                  data.width() * 4
                );

                this.draw();

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void manageDragOver(final DragEvent event) {
        // Accept a single file
        if (event.getDragboard().hasFiles()) {
            if (event.getDragboard().getFiles().size() == 1) {
                event.acceptTransferModes(TransferMode.COPY);
            }
        }
        event.consume();
    }

    @FXML
    private void manageDragDrop(final DragEvent event) {
        // Manage a single file
        List<File> files = event.getDragboard().getFiles();
        File file = files.get(0);

        // Load out of JFX thread
        System.out.println("Loading file " + file.getName());
        CompletableFuture.runAsync(() -> this.loadMapFile(file.getAbsolutePath()));
    }

    @FXML
    private void manageMousePressed(final MouseEvent event) {
        this.startX = event.getX();
        this.startY = event.getY();

        if (event.isSecondaryButtonDown()) {
            this.applyTool(event);
        }
    }

    @FXML
    private void manageMouseReleased(final MouseEvent event) {
        //
    }

    @FXML
    private void manageMouseMoved(final MouseEvent event) {
        this.updateToolOverlayPosition(event.getX(), event.getY());
    }

    @FXML
    private void manageMouseDragged(final MouseEvent event) {
        if (event.isPrimaryButtonDown()) {
            this.pan(event);
        } else if (event.isSecondaryButtonDown()) {
            this.applyTool(event);
        }
        this.updateToolOverlayPosition(event.getX(), event.getY());
    }

    private void pan(final MouseEvent event) {

        double dx = event.getX() - this.startX;
        double dy = event.getY() - this.startY;

        Affine transform = this.imageGraphicsContext.getTransform();
        transform.prependTranslation(dx, dy);
        this.updateTransform(transform);

        this.startX = event.getX();
        this.startY = event.getY();

        this.draw();
    }

    private void updateTransform(final Affine transform) {
        this.imageGraphicsContext.setTransform(transform);
        this.fogGraphicsContext.setTransform(transform);
    }

    @FXML
    private void manageMouseWheel(final ScrollEvent event) {

        double signum = Math.signum(event.getDeltaY());

        if (event.isShiftDown()) {
            // Shift down : tool size update
            // Holding shit means horizontal scroll, so read deltaX
            signum = Math.signum(event.getDeltaX());
            int increaseValue = (int) (TOOL_SIZE_STEP * signum);
            this.updateToolSize(increaseValue);
            this.updateToolOverlayPosition(event.getX(), event.getY());

        } else if (event.isControlDown()) {
            // Control down : fog opacity update
            double fogOpacity = this.fogOpacityProperty.get();
            this.fogOpacityProperty.set(fogOpacity + FOG_STEP * signum);

        } else {
            // Other cases : regular zoom
            double scale = Math.pow(ZOOM_SCALE_STEP, signum);

            Affine transform = this.imageGraphicsContext.getTransform();
            transform.prependScale(scale, scale, event.getX(), event.getY());
            this.updateTransform(transform);

            this.draw();
        }
    }

    @FXML
    private void manageMouseEntered() {
        this.setToolVisible(true);
    }

    @FXML
    private void manageMouseExited() {
        this.setToolVisible(false);
    }

    @FXML
    private void manageKeyPress(final KeyEvent event) {
        KeyCode code = event.getCode();
        switch (code) {
            case ADD -> this.updateToolSize(TOOL_SIZE_STEP);
            case SUBTRACT -> this.updateToolSize(-TOOL_SIZE_STEP);
        }
    }

    @FXML
    private void rotateClockwise() {
        this.rotate(90);
    }

    @FXML
    private void rotateCounterClockwise() {
        this.rotate(-90);
    }

    private void rotate(final double angle) {
        // Rotate map image
        // Pivot is current center
        Affine transform = this.imageGraphicsContext.getTransform();
        transform.prependRotation(angle, CANVAS_WIDTH / 2.0, CANVAS_HEIGHT / 2.0);
        this.updateTransform(transform);
        this.draw();
    }

    // increaseValue can be negative (decrease case)
    private void updateToolSize(final int increaseValue) {

        // New size (increased or decreased)
        int currentValue = this.toolSizeProperty.get();
        int newValue = currentValue + increaseValue;

        this.toolSizeProperty.set(newValue);

        // Re-center tool on current position
        // Otherwise, after size increase it is a bit off
        double shift = increaseValue / 2.0;
        this.rectangleToolOverlay.setTranslateX(this.rectangleToolOverlay.getTranslateX() - shift);
        this.rectangleToolOverlay.setTranslateY(this.rectangleToolOverlay.getTranslateY() - shift);
        this.circleToolOverlay.setTranslateX(this.circleToolOverlay.getTranslateX() - shift);
        this.circleToolOverlay.setTranslateY(this.circleToolOverlay.getTranslateY() - shift);
    }

    private void draw() {

        Affine currentTransform = this.imageGraphicsContext.getTransform();

        this.updateTransform(this.identityTransform);

        // Clear canvas
        this.fogGraphicsContext.clearRect(0, 0, this.imageWidth, this.imageHeight);
        this.imageGraphicsContext.setFill(Color.BLACK);
        this.imageGraphicsContext.fillRect(0, 0, this.imageWidth, this.imageHeight);

        this.updateTransform(currentTransform);

        this.imageGraphicsContext.drawImage(this.mapImage, 0, 0);
        this.fogGraphicsContext.drawImage(this.fogImage, 0, 0);
    }

    private void updateToolOverlayPosition(final double x, final double y) {

        double halfSize = this.toolSizeProperty.get() / 2.0;

        this.rectangleToolOverlay.setTranslateX(x - halfSize);
        this.rectangleToolOverlay.setTranslateY(y - halfSize);

        this.circleToolOverlay.setTranslateX(x);
        this.circleToolOverlay.setTranslateY(y);
    }

    private void applyTool(final MouseEvent event) {

        int toolSize = this.toolSizeProperty.get();

        Affine transform = this.fogGraphicsContext.getTransform();

        ToolMode toolMode = this.toolModeProperty.get();
        Color color;
        switch (toolMode) {
            case ERASER -> color = Color.TRANSPARENT;
            case FOG -> color = Color.BLACK;
            case NONE -> {
                return;
            }
            default -> throw new IllegalArgumentException("Invalid tool mode : " + toolMode);
        }

        try {
            // Apply transform
            Point2D point = transform.inverseTransform(event.getX(), event.getY());
            int size = (int) Math.round(toolSize / transform.getMxx());

            // Get selected tool shape
            ToolShape toolShape = this.getSelectedToolShape();
            switch (toolShape) {
                case SQUARE -> this.applySquareTool(point, size, color);
                case CIRCLE -> this.applyCircleTool(point, size, color);
            }

        } catch (NonInvertibleTransformException e) {
            // Should not happen as transform is set to be invertible
            throw new IllegalStateException("Invalid transform : " + transform);
        }

        this.draw();
    }

    private void applySquareTool(final Point2D center, final int size, final Color color) {

        int halfSize = size / 2;

        // Compute rectangle bounds
        int minX = Math.max(0, (int) center.getX() - halfSize);
        int maxX = Math.min(this.imageWidth - 1, (int) center.getX() + halfSize);
        int minY = Math.max(0, (int) center.getY() - halfSize);
        int maxY = Math.min(this.imageHeight - 1, (int) center.getY() + halfSize);

        // Create a pixel buffer filled with the desired color
        int bufferWidth = maxX - minX + 1;
        int bufferHeight = maxY - minY + 1;
        int bufferSize = bufferWidth * bufferHeight;
        int[] buffer = new int[bufferSize];

        // Color to int representation
        int argb = ((int) (color.getRed() * 255) << 16)
          | ((int) (color.getGreen() * 255) << 8)
          | ((int) (color.getBlue() * 255))
          | ((int) (color.getOpacity() * 255) << 24);
        Arrays.fill(buffer, argb);

        // Set the pixels in a single call for performance purpose
        this.fogImage.getPixelWriter().setPixels(
            minX,
            minY,
            bufferWidth,
            bufferHeight,
            PixelFormat.getIntArgbPreInstance(),
            buffer,
            0,
            bufferWidth
        );
    }

    private void applyCircleTool(final Point2D center, final int size, final Color color) {

        int radius = size / 2;
        int centerX = (int) center.getX();
        int centerY = (int) center.getY();

        // Calculate the squared radius to avoid using Math.sqrt
        int radiusSquared = radius * radius;

//        int roughnessLevels = 5;
//        int[] rs = new int[roughnessLevels];
//        IntStream.range(0, roughnessLevels).forEach(i -> rs[i] = (radius - i) * (radius - i));

        PixelWriter pixelWriter = this.fogImage.getPixelWriter();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                int dx = x - centerX;
                int dy = y - centerY;
                if (dx * dx + dy * dy <= radiusSquared &&
                  x >= 0 && x < this.imageWidth && y >= 0 && y < this.imageHeight) {
                    // Inside the circular region, within image bounds
                    // Set the pixel to the desired color
                    pixelWriter.setColor(x, y, color);
                }
            }
        }


//        int radius = size / 2;
//        int centerX = (int) center.getX();
//        int centerY = (int) center.getY();
//
//        // Calculate the squared radius to avoid using Math.sqrt
//        int radiusSquared = radius * radius;
//
//        PixelWriter pixelWriter = this.fogImage.getPixelWriter();
//
//        for (int x = centerX - radius; x <= centerX + radius; x++) {
//            for (int y = centerY - radius; y <= centerY + radius; y++) {
//                int dx = x - centerX;
//                int dy = y - centerY;
//                if (dx * dx + dy * dy <= radiusSquared &&
//                  x >= 0 && x < this.imageWidth && y >= 0 && y < this.imageHeight) {
//                    // Inside the circular region, within image bounds
//                    // Set the pixel to the desired color
//                    pixelWriter.setColor(x, y, color);
//                }
//            }
//        }
    }

    private void bindSlider(
        final Slider slider,
        final double min,
        final double max,
        final int minorTickCount,
        final double majorTickUnit,
        final Property<Number> property) {

        slider.setMin(min);
        slider.setMax(max);

        slider.setMinorTickCount(minorTickCount);
        slider.setMajorTickUnit(majorTickUnit);

        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);

        slider.valueProperty().bindBidirectional(property);
    }

    void setDungeonMasterPlayersEvents(final DungeonMasterPlayersEvents events) {
        this.dungeonMasterPlayersEvents = events;
    }

    private void loadMapFile(final String filename) {

        // Ensure file is an image (allowed extension are png and jpegs)
        String extension = FilenameUtils.getExtension(filename);
        if (!ALLOWED_MAP_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Invalid file extension : " + extension);
        }

        try (FileInputStream inputStream = new FileInputStream(filename)) {
            this.mapImage = new Image(inputStream);
            this.imageWidth = (int) this.mapImage.getWidth();
            this.imageHeight = (int) this.mapImage.getHeight();

            this.fogImage = new WritableImage(
                this.imageWidth,
                this.imageHeight
            );

            // Default zoom
            double ratio = (double) CANVAS_WIDTH / this.imageWidth;
            Affine transform = this.imageGraphicsContext.getTransform();
            transform.prependScale(ratio, ratio);
            this.updateTransform(transform);

            // Reset fog
            this.restoreFog();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Window getStageWindow() {
        return this.dmStackPane.getScene().getWindow();
    }
}