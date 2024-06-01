module MultimediaApp {
    requires javafx.controls;
    requires javafx.fxml;
    requires jspeedtest;
    requires ffmpeg;
    requires com.google.common;
    requires org.apache.commons.io;
    requires org.apache.commons.lang3;


    opens org.proj to javafx.fxml;
    exports org.proj;
}