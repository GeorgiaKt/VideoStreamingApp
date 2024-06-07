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

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class Client extends Application {
    private final CountDownLatch latch = new CountDownLatch(1); //used for synchronization
    private Socket comSocket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private String host = "127.0.0.1";
    private int port = 8888;
    private int downloadSpeed; //download speed
    private String selectedFormat;
    private String ffplayPath = "C:/ffmpeg-7.0-full_build/bin/ffplay.exe";

    public static void main(String[] args) {
        launch(); //launch gui

    }

//    public String getSelectedFormat() {
//        return selectedFormat;
//    }
//
//    public void setSelectedFormat(String selectedFormat) {
//        this.selectedFormat = selectedFormat;
//    }
//
//    public int getDownloadSpeed() {
//        return downloadSpeed;
//    }
//
//    public void setDownloadSpeed(int downloadSpeed) {
//        this.downloadSpeed = downloadSpeed;
//    }

    @Override
    public void start(Stage stage) throws IOException {
        establishSocketConnection();
        downloadSpeedTest();

        //stop the execution of the main thread until the download speed test is completed
        try {
            latch.await(); //wait for download speed test to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        FXMLLoader fxmlLoader = new FXMLLoader(Client.class.getResource("clientGUI.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 700, 500);
        ClientController controller = fxmlLoader.getController();
        controller.setClient(this);
        stage.setTitle("Client");
        stage.setScene(scene);

        //when window is closed, close connection with server
//        stage.setOnCloseRequest(event ->{
//            closeConnection();
//            Platform.exit();
//        });

        stage.show();
    }

    private void establishSocketConnection() {
        try {
            comSocket = new Socket(host, port); //create socket for the communication between client and a specific host in a specific port

            outputStream = new ObjectOutputStream(comSocket.getOutputStream()); //objects client sends to server
            inputStream = new ObjectInputStream(comSocket.getInputStream());

            System.out.println("Client connected");

        } catch (IOException e) {
            System.out.println("An exception has occurred on port " + port);
            System.out.println(e.getMessage());
        }
    }

    private void downloadSpeedTest() {
        final SpeedTestSocket speedTestSocket = new SpeedTestSocket();
        speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {

            @Override
            public void onCompletion(final SpeedTestReport report) {
                //called when download/upload is complete
                System.out.println("[COMPLETED] Download rate in bit/s: " + report.getTransferRateBit());
//                System.out.println("[COMPLETED] SpeedTestMode: " + report.getSpeedTestMode());
//                System.out.println("[COMPLETED] TotalPacketSize: " + report.getTotalPacketSize());

                BigDecimal transferRate = report.getTransferRateBit();
                BigDecimal transferRateKbps = convertToKbps(transferRate); //convert to Kbps
                System.out.println("[COMPLETED] Download rate in Kbps: " + transferRateKbps); //convert Big Decimal to int
                downloadSpeed = transferRateKbps.intValue();
                System.out.println("Download speed in Kbps (int): " + downloadSpeed);

                latch.countDown(); //notify main thread that the download speed test is completed
            }

            @Override
            public void onError(final SpeedTestError speedTestError, final String errorMessage) {
            }

            @Override
            public void onProgress(final float percent, final SpeedTestReport downloadReport) {
//                System.out.println("Percent: " + percent + " DownloadReport: " + downloadReport);
//                System.out.println("on progress...");

            }
        });

        speedTestSocket.startFixedDownload("ftp://speedtest:speedtest@ftp.otenet.gr/test1Mb.db",
                5000);

    }

    private BigDecimal convertToKbps(BigDecimal transferRateBit) {
        return transferRateBit.divide(BigDecimal.valueOf(1000));
    }

    private void closeConnection() {
        System.out.println("Closing connection...");
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


    public void sendFormatAndSpeed(String format) {
        System.out.println("speed " + downloadSpeed + " format " + format);
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
        System.out.println("RECEIVED: " + videos);
        return videos;

    }

    public void sendSelectedVideoAndProtocol(String selectedVideo, String protocol) {
        System.out.println("selected video: " + selectedVideo + " protocol: " + protocol);
        Object[] videoProtocol = new Object[2];
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

        System.out.println("RECEIVED: " + res);
        return (int) res;
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
                    Process process = pb.start();
                    process.waitFor();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
    }

}
