package com.codesimcoe.mapexplorer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FilenameUtils;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.stage.Stage;

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

    private static final String SAVE_FILE_EXTENSION = "mapexplorer";

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
    private Rectangle toolOverlay;

    @FXML
    private ToggleButton fogToolToggleButton;

    @FXML
    private ToggleButton eraserToolToggleButton;

    @FXML
    private ToggleGroup toolToggleGroup;

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
        this.toolModeProperty = new SimpleObjectProperty<ToolMode>();
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

            this.toolOverlay.setVisible(toolOverlayVisible);
        });
        this.toolToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == this.fogToolToggleButton) {
                this.toolModeProperty.set(ToolMode.FOG);
                this.toolOverlay.setStroke(ColorConstants.BRUSH_OVERLAY);
            } else if (newValue == this.eraserToolToggleButton) {
                this.toolModeProperty.set(ToolMode.ERASER);
                this.toolOverlay.setStroke(ColorConstants.ERASER_OVERLAY);
            } else {
                this.toolModeProperty.set(ToolMode.NONE);
                this.toolOverlay.setStroke(Color.WHITE);
            }
        });
        this.toolModeProperty.set(ToolMode.ERASER);

        this.bindSlider(this.fogOpacitySlider, MIN_FOG_OPACITY, MAX_FOG_OPACITY, 0, 0.25, this.fogOpacityProperty);
        this.fogOpacitySlider.valueProperty().bindBidirectional(this.fogOpacityProperty);

        this.bindSlider(this.toolSizeSlider, MIN_TOOL_SIZE, MAX_TOOL_SIZE, 100, 50, this.toolSizeProperty);
        this.toolSizeSlider.valueProperty().bindBidirectional(this.toolSizeProperty);

        // Tool overlay
        this.toolOverlay.setFill(Color.TRANSPARENT);
        this.toolOverlay.widthProperty().bind(this.toolSizeProperty);
        this.toolOverlay.heightProperty().bind(this.toolSizeProperty);

        // Fog of war
        this.fogCanvas.opacityProperty().bind(this.fogOpacityProperty);

        // XXX
        // Load
        String img = "C:\\Users\\clem\\Desktop\\GL_VampireMansion_NormalMansion_Day.jpg";
        try (FileInputStream inputStream = new FileInputStream(img)) {
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
//            System.out.println(ratio);

//            this.draw();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.restoreFog();
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
        this.dungeonMasterPlayersEvents.onCommit(this.mapImage, this.fogImage);
    }

    @FXML
    private void save() {

        int width = (int) this.mapImage.getWidth();
        int height = (int) this.mapImage.getHeight();
        int size = width * height;
        byte[] pixels = new byte[2 * 4 * size];

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

        SaveData data = new SaveData(width, height, pixels);

        // Compress
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
            ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut);
            objectOut.writeObject(data);
            objectOut.close();
            byte[] bytes = baos.toByteArray();

            String filename = "C:\\Users\\clem\\Downloads\\explorer." + SAVE_FILE_EXTENSION;
            try (FileOutputStream fileStream = new FileOutputStream(filename)) {
                fileStream.write(bytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void load() {

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
        CompletableFuture.runAsync(() -> this.loadFile(file));
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

        double tx = transform.getTx();
        double ty = transform.getTy();

        double mxx = transform.getMxx();
        double myy = transform.getMyy();

//        System.out.println(tx + " " + tx / mxx);


//        try {
//            Point2D p = transform.inverseTransform(tx, ty);
////            System.out.println(p);
//        } catch (NonInvertibleTransformException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }

//        // Left
//        if (tx > 0) {
//            // Allow only left pan
//            dx = Math.min(0, dx);
//        }
//
//        // Right
//        if (tx + this.imageWidth - CANVAS_WIDTH < 0) {
//            dx = Math.max(0, dx);
//        }
//
//        // Top
//        if (ty > 0) {
//            dy = Math.min(0, dy);
//        }
//
//        // Bottom
//        if (ty + this.imageHeight - CANVAS_HEIGHT < 0) {
//            dy = Math.max(0, dy);
//        }

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
        this.toolOverlay.setVisible(true);
    }

    @FXML
    private void manageMouseExited() {
        this.toolOverlay.setVisible(false);
    }

    @FXML
    private void manageKeyPress(final KeyEvent event) {

        KeyCode code = event.getCode();
        switch (code) {
            case ADD -> this.updateToolSize(TOOL_SIZE_STEP);
            case SUBTRACT -> this.updateToolSize(-TOOL_SIZE_STEP);
            default -> {
            }
            // Nothing to do
        }
    }

    private void updateToolSize(final int increaseValue) {

        int currentValue = this.toolSizeProperty.get();
        int newValue = currentValue + increaseValue;

        this.toolSizeProperty.set(newValue);
    }

    private void draw() {

        Affine currentTransform = this.imageGraphicsContext.getTransform();

        this.updateTransform(this.identityTransform);

        this.fogGraphicsContext.clearRect(0, 0, this.imageWidth, this.imageHeight);
        this.imageGraphicsContext.setFill(Color.BLACK);
        this.imageGraphicsContext.fillRect(0, 0, this.imageWidth, this.imageHeight);

        this.updateTransform(currentTransform);

        this.imageGraphicsContext.drawImage(this.mapImage, 0, 0);
        this.fogGraphicsContext.drawImage(this.fogImage, 0, 0);
    }

    private void updateToolOverlayPosition(final double x, final double y) {

        double halfSize = this.toolSizeProperty.get() / 2.0;

        this.toolOverlay.setTranslateX(x - halfSize);
        this.toolOverlay.setTranslateY(y - halfSize);
    }

    private void applyTool(final MouseEvent event) {

        int size = this.toolSizeProperty.get();

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
            Point2D point = transform.inverseTransform(event.getX(), event.getY());
            size /= transform.getMxx();
            int halfSize = size / 2;

            for (int i = 0; i < size; i++) {

                int x = (int) point.getX() + i - halfSize;
                if (x > 0 && x < this.imageWidth) {

                    for (int j = 0; j < size; j++) {

                        int y = (int) point.getY() + j - halfSize;
                        if (y > 0 && y < this.imageHeight) {

                            // Erase or restore fog (depending on applied color)

                            //
//                            int transition = size / 5;
//
//                            int currentColor = this.fogImage.getPixelReader().getArgb(x, y);
////                            Color currentColor = this.fogImage.getPixelReader().getColor(x, y);
//
//                            boolean edge = false;
//                            for (int t = 0; t < transition; t++) {
//
//                                float factor = 1 - ((float) t) / transition;
//
//                                if (i == t || i == size - 1 - t || j == t || j == size - 1 - t) {
//
//                                    int alpha = 0xff & (currentColor >> 24);
//                                    int newAlpha = Math.round(alpha * factor);
//
//                                    int a = newAlpha;
//                                    int r = (int) (color.getRed() * 255);
//                                    int g = (int) (color.getGreen() * 255);
//                                    int b = (int) (color.getBlue() * 255);
//                                    int argb = (a << 24) | (r << 16) | (g << 8) | b;
//
//                                    this.fogImage.getPixelWriter().setArgb(
//                                        x,
//                                        y,
//                                        argb
//                                    );
//
//                                    edge = true;
//                                    break;
//                                }
//                            }
//
//                            if (!edge) {
//                                this.fogImage.getPixelWriter().setColor(
//                                    x,
//                                    y,
//                                    Color.TRANSPARENT
//                                );
//                            }

                            // No blur around edges
                            this.fogImage.getPixelWriter().setColor(
                                x,
                                y,
                                color
                            );
                        }
                    }
                }
            }

        } catch (NonInvertibleTransformException e) {
            e.printStackTrace();
        }

        this.draw();
    }

    private void loadFile(final File file) {
        String extension = FilenameUtils.getExtension(file.getName());

        switch (extension) {
            case SAVE_FILE_EXTENSION:
                // Decompress
                try {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                    GZIPInputStream gzipIn = new GZIPInputStream(bais);
                    ObjectInputStream objectIn = new ObjectInputStream(gzipIn);
                    SaveData data = (SaveData) objectIn.readObject();
                    objectIn.close();

                    int size = data.width() * data.height();
                    byte[] mapBuffer = new byte[size];
                    byte[] fogBuffer = new byte[size];
                    System.arraycopy(data.pixels(), 0, mapBuffer, 0, size);
                    System.arraycopy(data.pixels(), size, fogBuffer, 0, size);

                    WritableImage map = new WritableImage(data.width(), data.height());
                    map.getPixelWriter().setPixels(
                        0,
                        0,
                        data.width(),
                        data.height(),
                        PixelFormat.getByteBgraInstance(),
                        mapBuffer,
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
                        fogBuffer,
                        0,
                        data.width() * 4
                    );

                    this.draw();

                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }

                System.out.println("LOADED");

            break;
        }
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
}