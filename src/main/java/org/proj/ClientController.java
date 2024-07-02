package org.proj;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import javafx.stage.Stage;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class ClientController implements Initializable {
    @FXML
    public Label speedTestLabel;
    @FXML
    private MenuItem aboutMenuItem;
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

    private Logger log;
    private Client client;
    private String selectedVideo;
    private String formatSelected;
    private String protocolSelected;
    private int resolution = 0;
    private boolean noVideos;
    private int downloadSpeed;

    public void setClient(Client client, Logger log) {
        this.client = client;
        this.log = log;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        ObservableList<String> formatList = FXCollections.observableArrayList("mkv", "mp4", "avi");
        formatComboBox.setItems(formatList);
        ObservableList<String> protocolList = FXCollections.observableArrayList("TCP", "UDP", "RTP/UDP");
        protocolComboBox.setItems(protocolList);
        label.setText("Waiting for speed test to complete...");

        btn.setDisable(true); //disable button until download speed test is completed
        listView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            //add listener for when a list view item is selected
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                selectedVideo = listView.getSelectionModel().getSelectedItem();
                if (selectedVideo != null) {
                    label.setText("Selected video: " + selectedVideo);
                    client.sendSelectedVideoAndProtocol(selectedVideo, protocolSelected);

                    if (protocolSelected == null) {
                        resolution = client.receiveVideoResolution();
                    }

                    boolean videoFound = client.receiveIsVideoFound();
                    if (videoFound)
                        client.playVideo(protocolSelected, resolution);
                    else
                        label.setText("Video not Found !");

                    //after the video starts playing, unselects the selected item on list view - ability to select the same video afterwards
                    new Thread(() -> {
                        try {
                            Thread.sleep(100); //sleep for 100 ms in order to make sure the video has started playing
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        listView.getSelectionModel().clearSelection(); //unselect list view item
                    }).start();

                }
            }
        });

        //add listener for when the button gets enabled
        btn.disabledProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observableValue, Boolean aBoolean, Boolean t1) { //t1 is the new value the btn gets (enabled/disabled)
                //when the button gets enabled
                if (!t1)
                    label.setText("Select Format"); //update label text
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
            label.setText("");
            client.sendFormatAndSpeed(formatSelected); //send to server download speed & format

            loadListView();
            if (!noVideos) { //change label text based on noVideos flag
                label.setText("Select video to play and protocol (optional)");
            } else
                label.setText("No videos available !");
        }
    }

    private void loadListView() {
        ArrayList<String> videos = client.receiveSuitableVideos();
        //change flag for each case
        if (videos == null) {
            noVideos = true;
            log.error("No videos available !");
        } else {
            //load list view items only when there are available videos from server
            noVideos = false;
            listView.getItems().clear(); //delete all items that are on the list view
            listView.getItems().addAll(videos); //and add all the new ones
            log.info("List view items loaded");
        }
    }

    public void enableBtn() { //enable button when speed test is completed
        Platform.runLater(() -> btn.setDisable(false)); //needs to be run on the ui-modifying thread
    }

    public void updateSpeedLabel(int speed){
        downloadSpeed = speed;
        Platform.runLater(() -> speedTestLabel.setText("Download Speed:\n" + downloadSpeed + " Kbps"));
    }

    public void aboutMenuItemSelect(ActionEvent actionEvent) {
        Stage aboutStage = new Stage();
        aboutStage.setTitle("About");
        Label builtLabel = new Label(
                """
                        Built with:
                        · JDK 20.0.1
                        · JavaFX 22.0.1
                        · FFMPEG 7.0.
                        · FFMPEG Wrapper for Java 0.8.0
                        · JSpeedTest 1.32.1
                        · Scene Builder 21.0.0
                        · Log4j2 2.13.1
                        · Guava 32.1.3""");
        Label devLabel = new Label("Application Developed by: GeorgiaKt");
        StackPane stackPane =  new StackPane();
        stackPane.setAlignment(Pos.CENTER);
        stackPane.getChildren().addAll(builtLabel, devLabel);
        StackPane.setAlignment(builtLabel, Pos.CENTER);
        StackPane.setAlignment(devLabel, Pos.BOTTOM_CENTER);

        Scene aboutScene = new Scene(stackPane, 550, 400);
        aboutStage.setScene(aboutScene);
        aboutStage.show();

    }
}