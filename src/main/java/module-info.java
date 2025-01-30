module VideoStreamingApp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires jspeedtest;
    requires ffmpeg;
    requires com.google.common;
    requires org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;


    opens org.proj to javafx.fxml;
    exports org.proj;
}