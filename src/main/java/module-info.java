module MultimediaApp {
    requires javafx.controls;
    requires javafx.fxml;
    requires jspeedtest;
    requires ffmpeg;
    requires com.google.common;
    requires org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires javafx.graphics;
    requires org.apache.logging.log4j;


    opens org.proj to javafx.fxml;
    exports org.proj;
}