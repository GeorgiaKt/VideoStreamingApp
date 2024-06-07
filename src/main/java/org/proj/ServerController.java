package org.proj;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

public class ServerController {

    @FXML
    private TextArea textArea;

    public void addText(String s){
        textArea.appendText(">" + s + "\n");
    }

}
