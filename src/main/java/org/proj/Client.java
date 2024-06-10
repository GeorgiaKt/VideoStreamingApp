package org.proj;

import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.util.ArrayList;

public class Client extends Application {
    static Logger log = LogManager.getLogger(Server.class);
    private static ClientController controller;
    private final String host = "127.0.0.1";
    private final int port = 8888;
    private Socket comSocket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private int downloadSpeed; //download speed
    private String selectedFormat;
    private String ffplayPath = "/ffmpeg-7.0-full_build/bin/ffplay.exe";
    private Process videoProcess;

    public static void main(String[] args) {
        launch(); //launch gui
    }

    @Override
    public void start(Stage stage) throws IOException {
        //connect to server
        while (true) {
            if (establishSocketConnection()) //if client connects to server successfully then break the loop
                break;
        }

        //load gui
        FXMLLoader fxmlLoader = new FXMLLoader(Client.class.getResource("clientGUI.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 700, 500);
        controller = fxmlLoader.getController();
        controller.setClient(this, log);
        stage.setTitle("Client");
        stage.setScene(scene);

        //when window is closed, close connection with server
        stage.setOnCloseRequest(event -> {
            closeConnection();
            if (videoProcess != null && videoProcess.isAlive()) { //if the video window is still open, close it
                videoProcess.destroy();
            }
            Platform.exit();
            System.exit(0);
        });

        stage.show();

        new Thread(this::runClient).start();

    }

    private void runClient() {
        //run on a different thread
        downloadSpeedTest();
    }

    private boolean establishSocketConnection() {
        try {
            comSocket = new Socket(host, port); //create socket for the communication between client and a specific host in a specific port

            outputStream = new ObjectOutputStream(comSocket.getOutputStream()); //objects client sends to server
            inputStream = new ObjectInputStream(comSocket.getInputStream());

            log.debug("Client connected at: " + host + ":" + port);
            return true;

        } catch (IOException e) {
            log.error("Failed to connect at port: " + port);
            System.out.println(e.getMessage());
            return false;
        }
    }

    private void downloadSpeedTest() {
        final SpeedTestSocket speedTestSocket = new SpeedTestSocket();
        log.info("Speed Test Started");
        speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
            @Override
            public void onCompletion(final SpeedTestReport report) {
                //called when download/upload is complete
                BigDecimal transferRate = report.getTransferRateBit();
                BigDecimal transferRateKbps = convertToKbps(transferRate); //convert to Kbps
                downloadSpeed = transferRateKbps.intValue(); //convert Big Decimal to int
                log.info("Speed Test Completed");
                log.debug("Download Speed: " + downloadSpeed + " Kbps");
                controller.enableBtn();
            }

            @Override
            public void onError(final SpeedTestError speedTestError, final String errorMessage) {
                log.error("Speed Test Failed: " + errorMessage);
            }

            @Override
            public void onProgress(final float percent, final SpeedTestReport downloadReport) {
                //do nothing
            }
        });

        speedTestSocket.startFixedDownload("ftp://speedtest:speedtest@ftp.otenet.gr/test1Mb.db", 5000); //run speed test for 5 seconds
    }

    private BigDecimal convertToKbps(BigDecimal transferRateBit) {
        return transferRateBit.divide(BigDecimal.valueOf(1000));
    }

    private void closeConnection() {
        log.info("Closing connection...");
        try {
            if (comSocket != null && comSocket.isClosed())
                comSocket.close();
            if (outputStream != null)
                outputStream.close();
            if (inputStream != null)
                inputStream.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //methods for exchange information between server & client
    public void sendFormatAndSpeed(String format) {
        log.debug("Sent: format: " + format + ", speed: " + downloadSpeed);
        selectedFormat = format;
        //storing arguments (download speed & format) in Object array
        Object[] speedFormat = new Object[2];
        speedFormat[0] = selectedFormat;
        speedFormat[1] = downloadSpeed;

        //send speedFormat array to server
        try {
            outputStream.writeObject(speedFormat);
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ArrayList<String> receiveSuitableVideos() {
        ArrayList<String> videos;
        Object sVideos;
        try {
            sVideos = inputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        videos = (ArrayList<String>) sVideos;
        if (videos != null) { //videos can also be null
            for (int i = 0; i < videos.size(); i++)
                videos.set(i, videos.get(i) + "." + selectedFormat);
        } //add at the end of every video name, the format
        log.debug("Received: available videos for streaming: " + videos);
        return videos;
    }

    public void sendSelectedVideoAndProtocol(String selectedVideo, String protocol) {
        log.debug("Sent: selected video's name: " + selectedVideo + ", protocol: " + protocol);
        Object[] videoProtocol = new Object[2];
        selectedVideo = StringUtils.removeEnd(selectedVideo, "." + selectedFormat); //remove the format from the end
        videoProtocol[0] = selectedVideo;
        videoProtocol[1] = protocol;
        try {
            outputStream.writeObject(videoProtocol);
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int receiveVideoResolution() {
        Object res;
        try {
            res = inputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        log.debug("Received: resolution: " + res);
        if (res == null)
            res = 0;
        return (int) res;
    }

    public boolean receiveIsVideoFound() {
        Object res;
        try {
            res = inputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        if ((int) res == 0) {
            log.debug("Received: code: " + res + " (video not found)");
            return false;
        } else {
            log.debug("Received: code: " + res + " (video found)");
            return true;
        }

    }

    public void playVideo(String protocol, int resolution) {
        if (protocol == null) { //if client hasn't selected protocol
            if (resolution == 240) {
                protocol = "TCP";
            } else if (resolution == 360 || resolution == 480) {
                protocol = "UDP";
            } else if (resolution == 720 || resolution == 1080) {
                protocol = "RTP/UDP";
            }
        }

        String[] command;

        if (protocol != null) {
            if (protocol.equals("TCP")) {
                command = new String[]{
                        ffplayPath,
                        "tcp://" + host + ":" + 7771
                };
            } else if (protocol.equals("UDP")) {
                command = new String[]{
                        ffplayPath,
                        "udp://" + host + ":" + 7772
                };
            } else {//protocol RTP/UDP
                command = new String[]{
                        ffplayPath,
                        "-protocol_whitelist",
                        "file,rtp,udp",
                        "-i",
                        "video.sdp"
                };
            }
            new Thread(() -> {
                ProcessBuilder pb = new ProcessBuilder(command).inheritIO();
                try {
                    videoProcess = pb.start();
                    log.info("Video started");
                    videoProcess.waitFor();
                    log.info("Video ended");
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

}