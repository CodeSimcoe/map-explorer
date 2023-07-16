module mapexplorer {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.commons.io;

    opens com.codesimcoe.mapexplorer to javafx.graphics, javafx.fxml;
    opens com.codesimcoe.mapexplorer.main to javafx.graphics;
    exports com.codesimcoe.mapexplorer;
}