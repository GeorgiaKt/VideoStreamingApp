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
import java.util.concurrent.CountDownLatch;

public class Client extends Application {
    private final CountDownLatch latch = new CountDownLatch(1); //used for synchronization
    private Socket comSocket;
    private PrintWriter out;
    private BufferedReader in;
    private BufferedReader stdIn;
    private BufferedWriter stdOut;
    private ObjectOutputStream outputStream;
    private String host = "127.0.0.1";
    private int port = 8888;

    private int downloadSpeed; //download speed
    private String selectedFormat;
    private String selectedProtocol;

    public static void main(String[] args) {
        launch(); //launch gui

    }

    public String getSelectedFormat() {
        return selectedFormat;
    }

    public void setSelectedFormat(String selectedFormat) {
        this.selectedFormat = selectedFormat;
    }

    public int getDownloadSpeed() {
        return downloadSpeed;
    }

    public void setDownloadSpeed(int downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }

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
            out = new PrintWriter(comSocket.getOutputStream(), true); //what client sends to server
            in = new BufferedReader(new InputStreamReader(comSocket.getInputStream())); //what client receives from server
            stdIn = new BufferedReader(new InputStreamReader(System.in)); //what client sends to user
            stdOut = new BufferedWriter(new OutputStreamWriter(System.out)); //what client receives from user

            outputStream = new ObjectOutputStream(comSocket.getOutputStream()); //objects client sends to server

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
                System.out.println("Percent: " + percent + " DownloadReport: " + downloadReport);
                System.out.println("on progress...");

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
            if (out != null)
                out.close();
            if (in != null)
                in.close();
            if (stdIn != null)
                stdIn.close();
            if (stdOut != null)
                stdOut.close();
            if (outputStream != null)
                outputStream.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public void sendFormatAndSpeed(String format, String protocol) {
        System.out.println("speed " + downloadSpeed + " format " + format + " protocol " + protocol);
        selectedFormat = format;
        selectedProtocol = protocol;
        //storing arguments (download speed & format) in Object array
        Object[] arguments = new Object[3];
        arguments[0] = downloadSpeed;
        arguments[1] = selectedFormat;
        arguments[2] = selectedProtocol;

        //send arguments array to server
        try {
            this.outputStream.writeObject(arguments);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
