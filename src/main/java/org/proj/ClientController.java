package org.proj;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ArrayList;
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
    private ListView<String> listView;

    @FXML
    private ComboBox<String> formatComboBox;

    @FXML
    private Label label;

    @FXML
    private ComboBox<String> protocolComboBox;

    private Client client;
    private String selectedVideo;
    private String formatSelected;
    private String protocolSelected;
    private int resolution = 0;

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

        listView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            //add listener for when a list view item is selected
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                selectedVideo = listView.getSelectionModel().getSelectedItem();
//                selectedVideo = t1;
                if (selectedVideo != null) {
                    label.setText("Selected video: " + selectedVideo);
                    client.sendSelectedVideoAndProtocol(selectedVideo, protocolSelected);

                    if(protocolSelected == null){
                        resolution = client.receiveVideoResolution();
                    }

                    client.playVideo(protocolSelected, resolution);
                }
            }
        });
    }

    public void formatSelect(ActionEvent actionEvent) {
        formatSelected = formatComboBox.getSelectionModel().getSelectedItem();
    }

    public void protocolSelect(ActionEvent actionEvent) {
        if (protocolComboBox.getSelectionModel().getSelectedItem() == null)
            protocolSelected = "";
        else
            protocolSelected = protocolComboBox.getSelectionModel().getSelectedItem();
    }


    public void btnSelect(ActionEvent actionEvent) {
        //format is required to proceed
        if (formatComboBox.getSelectionModel().getSelectedItem() == null) //if user doesnt select format
            label.setText("Format is required !");
        else {

            System.out.println("Format Selected: " + formatSelected);
            System.out.println("Protocol Selected: " + protocolSelected);

            label.setText("");
            client.sendFormatAndSpeed(formatSelected); //send to server download speed & format


            loadListView();
            label.setText("Select video to play");
        }
    }

    private void loadListView() {
        ArrayList<String> videos = client.receiveSuitableVideos();
        listView.getItems().clear(); //delete all items that are on the list view
        listView.getItems().addAll(videos); //and add all the new ones

    }
}
