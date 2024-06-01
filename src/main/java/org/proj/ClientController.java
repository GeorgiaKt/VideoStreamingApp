package org.proj;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

public class ClientController implements Initializable {

    @FXML
    private MenuBar MenuBar;

    @FXML
    private VBox VBoxRight;

    @FXML
    private VBox VBoxBottom;

    @FXML
    private Button btn;

    @FXML
    private ComboBox<String> formatComboBox;

    @FXML
    private Label label;

    @FXML
    private ComboBox<String> protocolComboBox;

    private Client client;

    public void setClient(Client client) {
        this.client = client;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        ObservableList<String> formatList = FXCollections.observableArrayList("mkv", "mp4", "avi");
        formatComboBox.setItems(formatList);
        ObservableList<String> protocolList = FXCollections.observableArrayList("TCP", "UDP", "RTP/UDP");
        protocolComboBox.setItems(protocolList);
        label.setText("Select Format");
    }

    public void formatSelect(ActionEvent actionEvent) {
//        String formatSelected = formatComboBox.getSelectionModel().getSelectedItem();
//        System.out.println("Format Selected: " + formatSelected);
    }

    public void protocolSelect(ActionEvent actionEvent) {
//        String protocolSelected = protocolComboBox.getSelectionModel().getSelectedItem();
//        System.out.println("Protocol Selected: " + protocolSelected);
    }


    public void btnSelect(ActionEvent actionEvent) {
        //format is required to proceed
        if (formatComboBox.getSelectionModel().getSelectedItem() == null) //if user doesnt select format
            label.setText("You need to select format !");
        else {
            String protocolSelected;
            if (protocolComboBox.getSelectionModel().getSelectedItem() == null)
                protocolSelected = "";
            else
                protocolSelected = protocolComboBox.getSelectionModel().getSelectedItem();
            String formatSelected = formatComboBox.getSelectionModel().getSelectedItem();

            System.out.println("Format Selected: " + formatSelected);
            System.out.println("Protocol Selected: " + protocolSelected);

            client.sendFormatAndSpeed(formatSelected, protocolSelected);

        }

    }
}
