//package org.client;
//
//import javafx.scene.control.ComboBox;
//import javafx.scene.control.Label;
//import javafx.scene.layout.VBox;
//
//public class ClientController {
//    public ComboBox protocolComboBox;
//    public ComboBox formatComboBox;
//    public VBox VBoxRight;
//    public Label label;
//}


package org.proj;

        import javafx.fxml.FXML;
        import javafx.scene.control.Button;
        import javafx.scene.control.ComboBox;
        import javafx.scene.control.Label;
        import javafx.scene.control.MenuBar;
        import javafx.scene.layout.VBox;

public class ClientController {

    @FXML
    private MenuBar MenuBar;

    @FXML
    private VBox VBoxRight;

    @FXML
    private VBox VBoxBottom;

    @FXML
    private Button btnSelect;

    @FXML
    private ComboBox<?> formatComboBox;

    @FXML
    private Label label;

    @FXML
    private ComboBox<?> protocolComboBox;

}
