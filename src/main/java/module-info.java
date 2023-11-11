module mapexplorer {
  requires javafx.controls;
  requires javafx.fxml;
  requires org.apache.commons.io;

  opens com.codesimcoe.mapexplorer to javafx.graphics, javafx.fxml;
  opens com.codesimcoe.mapexplorer.main to javafx.graphics;
  exports com.codesimcoe.mapexplorer;
  exports com.codesimcoe.mapexplorer.save;
  opens com.codesimcoe.mapexplorer.save to javafx.fxml, javafx.graphics;
  exports com.codesimcoe.mapexplorer.style;
  opens com.codesimcoe.mapexplorer.style to javafx.fxml, javafx.graphics;
}