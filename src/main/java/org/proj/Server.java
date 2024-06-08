package org.proj;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;


public class Server extends Application {
    private final CountDownLatch latch = new CountDownLatch(1); //used for synchronization
    private final String videosDirPath = "src/main/resources/videos";
    FFprobe ffprobe;
    FFmpeg ffmpeg;
    private File videosDir;
    private File[] files;
    private Table<String, String, Integer> availableFiles;
    private int port;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private ServerSocket serverSocket;
    private Socket comSocket;
    private boolean noVideos;
    private String ipClient;
    private int portClient;
    private String ffmpegPath = "C:/ffmpeg-7.0-full_build/bin/ffmpeg.exe";
    private static ServerController controller; //static in order not to be collected by the garbage collector :(


    public Server() {
        this.port = 8888; //initialize socket port
        try {
            ffprobe = new FFprobe("/ffmpeg-7.0-full_build/bin/ffprobe");
            ffmpeg = new FFmpeg("/ffmpeg-7.0-full_build/bin/ffmpeg");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        launch(); //launch gui
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(Server.class.getResource("serverGUI.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 700, 500);
        controller = fxmlLoader.getController();
        stage.setTitle("Server");
        stage.setScene(scene);

        //when window is closed, close server socket & application
        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                if (serverSocket != null && serverSocket.isClosed()) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                Platform.exit();
                System.exit(0);
            }

        });

        stage.show();

        new Thread(this::runServer).start();
    }

    private void runServer() {
        Server server = new Server();
        server.files = server.getListOfFiles();
        System.out.println("List of available files in folder:"); //print files in folder
        for (File file : server.files) {
            System.out.println(file.getName() + " " + file.getAbsolutePath());
        }

        try {
            server.createRemainingVideos();
            server.storeAvailableFiles();
            server.latch.await(); //wait the data to be stored
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            server.serverSocket = new ServerSocket(server.port); //create socket
            System.out.println("Server is running...");
            controller.addText("Server is running...");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        while (true) { //server is always running - waiting for client to connect
            server.establishSocketConnection(); //connection server-client

            int downloadSpeed;
            String format, protocol, selectedVideo;
            boolean argInitialized = false; //false for receiving 1st time format & speed, true for more than once

            Object[] arguments = new Object[2];
            while (true) { //read from stream till client disconnects - load files multiple times
                if (server.comSocket == null || server.comSocket.isClosed()) {
                    System.out.println("Client disconnected - ");
                    controller.addText("Client disconnected - ");
                    break;
                } else {
                    try {
                        if (!argInitialized) { //read from stream only if its first time running
                            //receive format & download speed from client
                            arguments = (Object[]) server.inputStream.readObject(); //read from client
                        }

                        if (arguments[0] != null && arguments[1] != null) {
                            format = (String) arguments[0];
                            downloadSpeed = (int) arguments[1];

                            controller.addText("Download speed: " + downloadSpeed + " Kbps");
//                            System.out.println("format: " + format + " downloadSpeed: " + downloadSpeed);

                            ArrayList<String> videos = new ArrayList<>(); //list of video's available for streaming that are going to be sent to user
                            if (!server.noVideos) { //if there are videos in folder
                                server.findSuitableVideos(downloadSpeed, format, videos, server); //find suitable videos based on the format & speed - fill arraylist videos

//                                System.out.println(videos);

                                //sent list with videos' names to client
                                Object sVideos;
                                sVideos = videos;
                                server.outputStream.writeObject(sVideos);
                                server.outputStream.flush();

                                while (true) { //choose video file multiple times - stream multiple times
                                    arguments = (Object[]) server.inputStream.readObject(); //receive selected video & protocol from client
                                    //if 1st argument is one of the formats, stream the selected video, else break the loop and continue from receiving format & speed
                                    if (!arguments[0].equals("mkv") && !arguments[0].equals("mp4") && !arguments[0].equals("avi")) {
                                        selectedVideo = (String) arguments[0];
                                        protocol = (String) arguments[1]; //protocol can be null

                                        System.out.println("Requested Video Info: " + " Name: " + selectedVideo + " Format: " + format + " Protocol: " + protocol);
                                        controller.addText("Requested Video Info: " + " Name: " + selectedVideo + ", Format: " + format + ", Protocol: " + protocol);

                                        //get path & resolution of the selected video
                                        String path = server.getVideoPath(selectedVideo, format, server);
                                        int resolution = server.getVideoResolution(selectedVideo, server);

                                        if (path != null) {
                                            if (protocol == null) {
                                                //sent video's res in case client hasn't selected protocol
                                                //that way the client will run the right command to the video
                                                server.outputStream.writeObject(resolution);
                                                server.outputStream.flush();
                                            }

                                            System.out.println("Path: " + path + " Res: " + resolution);
                                            server.streamVideo(protocol, path, resolution); //stream video to client
                                        } else
                                            System.out.println("Video not found");


                                    } else {
                                        //if the arguments are format & speed, initialize variables and break the loop
                                        format = (String) arguments[0];
                                        downloadSpeed = (int) arguments[1];
                                        argInitialized = true;
                                        break;
                                    }

                                }
                            } else
                                System.out.println("No videos in folder");
                        } else {
                            System.out.println("Null arguments");
                            break;
                        }

                    } catch (SocketException e) {
                        //client disconnected
                        System.out.println("Client disconnected !");
                        controller.addText("Client disconnected !");
                        break;
                    } catch (EOFException e) {
                        //stream closed (EOF reached)
                        System.out.println("Stream closed by client !");
                        controller.addText("Stream closed by client !");
                        break;
                    } catch (IOException | ClassNotFoundException e) {
                        System.out.println("Error reading from client: " + e.getMessage());
                        controller.addText("Error reading from client: " + e.getMessage());
                        e.printStackTrace();
                        break;
                    }
                }

            }
            server.closeClientConnection();
        }

    }

    private static int getTargetWidth(int height) {
        //set matching target width for each height
        switch (height) {
            case 1080:
                return 1920;
            case 720:
                return 1280;
            case 480:
                return 854;
            case 360:
                return 640;
            default:
                return 426;
        }
    }

    private int getSuitableRes(int downloadSpeed) {
        if (downloadSpeed <= 700) {
            return 240;
        } else if (downloadSpeed > 700 && downloadSpeed <= 1000) {
            return 360;
        } else if (downloadSpeed > 1000 && downloadSpeed <= 2000) {
            return 480;
        } else if (downloadSpeed > 2000 && downloadSpeed <= 4000) {
            return 720;
        } else {
            return 1080;
        }
    }

    private int getVideoResolution(String selectedVideo, Server server) {
        for (Table.Cell<String, String, Integer> cell : server.availableFiles.cellSet()) {
            if (cell.getRowKey().equals(selectedVideo)) {
                return cell.getValue();
            }
        }
        return 0;
    }

    private void streamVideo(String protocol, String path, int resolution) {
        if (path != null) {
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
                            ffmpegPath,
                            "-i",
                            path,
                            "-f",
                            "mpegts",
                            "tcp:/" + ipClient + ":" + 7771 + "?listen"
                    };

                } else if (protocol.equals("UDP")) {
                    command = new String[]{
                            ffmpegPath,
                            "-re",
                            "-i",
                            path,
                            "-f",
                            "mpegts",
                            "udp:/" + ipClient + ":" + 7772
                    };
                } else { //protocol RTP/UDP
                    command = new String[]{
                            ffmpegPath,
                            "-re",
                            "-i",
                            path,
                            "-an",
                            "-c:v",
                            "copy",
                            "-f",
                            "rtp",
                            "-sdp_file",
                            "video.sdp",
                            "rtp:/" + ipClient + ":" + 7773
                    };

                }
                //run the command in a thread
                Thread thread = new Thread(() -> {
                    ProcessBuilder pb = new ProcessBuilder(command).inheritIO();
                    try {
                        Process process = pb.start();
                        controller.addText("Streaming Video...");
                        process.waitFor();
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
                thread.start();

                try {
                    // Wait for the thread to finish
                    thread.join();
                    System.out.println("Streaming completed");
                    controller.addText("Streaming completed");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

        }
    }

    private void findSuitableVideos(int downloadSpeed, String format, ArrayList<String> videos, Server server) {
        for (Table.Cell<String, String, Integer> cell : server.availableFiles.cellSet()) {
            //add name of the videos that have the format requested & has smaller or the same resolution that can be transmitted with the downloadSpeed of the client
            if (cell.getColumnKey().equals(format)) {
                if (cell.getValue() <= getSuitableRes(downloadSpeed)) { //if video's resolution is smaller/or same than what the download speed allows
                    videos.add(cell.getRowKey());
                }
            }
        }
    }

    private String getVideoPath(String selectedVideo, String format, Server server) {
        for (Table.Cell<String, String, Integer> cell : server.availableFiles.cellSet()) {
            if (cell.getColumnKey().equals(format)) {
                if (cell.getRowKey().equals(selectedVideo)) {
                    return videosDirPath + "/" + cell.getRowKey() + "." + cell.getColumnKey();
                }
            }
        }
        return null;
    }

    private File[] getListOfFiles() {
        videosDir = new File(videosDirPath);
        files = videosDir.listFiles();
        //print list of files in videos directory
        if (files == null) {
            System.out.println("FAILED: Folder not found !");
            controller.addText("FAILED: Folder not found !");
        } else if (files.length == 0) {
            System.out.println("FAILED: No videos found in folder !");
            controller.addText("FAILED: No videos found in folder !");
        } else {
            return files;
        }
        return new File[0];
    }

    private void createRemainingVideos() throws IOException {
        controller.addText("Creating videos...");
        //create videos in order each one to exist in avi, mkv, mp4 and in all resolutions smaller that the original
        for (File file : files) {
            //initialize local variables filePath, fileName, fileExtension for each file
            String filePath = file.getAbsolutePath();
            String fileName = FilenameUtils.getBaseName(filePath);
            String fileExtension = FilenameUtils.getExtension(filePath);

            System.out.println("path " + filePath + " name " + fileName + " ext " + fileExtension);

            FFmpegProbeResult probeResult = ffprobe.probe(filePath);
            FFmpegFormat format = probeResult.getFormat();
            FFmpegStream stream = probeResult.getStreams().get(0);
//            System.out.format("%nFile: '%s' ; Format: '%s' ; Duration: %.3fs",
//                    format.filename,
//                    format.format_long_name,
//                    format.duration
//            );
//            System.out.format("%nCodec: '%s' ; Width: %dpx ; Height: %dpx",
//                    stream.codec_long_name,
//                    stream.width,
//                    stream.height //resolution
//            );

            //initialize 'allowed' extensions & resolutions
            String[] extensions = {"mp4", "avi", "mkv"}; //3 extensions in total
            Integer[] resolutions = {1080, 720, 480, 360, 240}; // 5 resolutions in total
            String targetFormat; //new file's extension
            for (String ext : extensions) {
                //create remaining files based on the extension
                if (ext.equals("mkv")) //mkv has different format
                    targetFormat = "matroska";
                else
                    targetFormat = ext;

                //create files with different extensions than original
                if (!fileExtension.equals(ext)) {
                    File targetFile = new File(videosDir, fileName + "." + ext);
                    createTargetFile(ffprobe, ffmpeg, targetFile, filePath, targetFormat, stream.height);
                }

                for (Integer res : resolutions) {
                    //create remaining files based on the resolution - with resolution smaller than the original
                    if (stream.height > res) {
                        String targetFileName = fileName;
                        //in case there is already a file with the same file name (that includes resolution)
                        if (fileName.endsWith(stream.height + "p")) {
                            targetFileName = StringUtils.removeEnd(targetFileName, stream.height + "p"); //remove resolution from file name
                        }
                        File targetFile = new File(videosDir, targetFileName + res + "p." + ext); //add resolution at the end of the name
                        createTargetFile(ffprobe, ffmpeg, targetFile, filePath, targetFormat, res);
                    }
                }

            }

        }
    }

    private void createTargetFile(FFprobe ffprobe, FFmpeg ffmpeg, File file, String filePath, String targetFormat, Integer targetHeight) {
        int targetWidth = getTargetWidth(targetHeight); //get matching width for the specific height
        //create the output file
        System.out.println("Creating file: " + file.getAbsolutePath());
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(filePath)
                .overrideOutputFiles(false)
                .addOutput(file.getAbsolutePath())
                .setFormat(targetFormat)
                .setVideoResolution(targetWidth, targetHeight)
                .done();
        //override is false in order not to create files that already exist (based on the name of the file)
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        executor.createJob(builder).run();
    }

    private void storeAvailableFiles() throws IOException {
        files = videosDir.listFiles(); //refresh list
        //table structure: name (rowKey), format (columnKey), resolution (value)
        if (files.length > 0) {
            noVideos = false;
            availableFiles = HashBasedTable.create();
            for (File file : files) {
                //initialize local variables filePath, fileName, fileExtension for each file
                String filePath = file.getAbsolutePath();
                String fileName = FilenameUtils.getBaseName(filePath);
                String fileExtension = FilenameUtils.getExtension(filePath);

                FFmpegProbeResult probeResult = ffprobe.probe(filePath);
                FFmpegStream stream = probeResult.getStreams().get(0);

                //store file to Table
                availableFiles.put(fileName, fileExtension, stream.height);
            }
        } else {
            noVideos = true;
            System.out.println("No videos found in folder !");
        }

        latch.countDown(); //notify main thread that the data has been stored

//        //print all elements stored in the table
//        for (Table.Cell<String, String, Integer> cell : availableFiles.cellSet()) {
//            System.out.println("Name = " + cell.getRowKey() + ", Format = " + cell.getColumnKey() + ", Resolution = " + cell.getValue());
//            //System.out.println(availableFiles.);
//        }
//        //print all elements per 'dimension'
//        System.out.println(availableFiles.rowKeySet()); // values() for all resolutions-values, columnKeySet() for all formats-columnKey, rowKeySet() for all different names-rowKey
//        //print all together
//        System.out.println(availableFiles);

    }

    private void establishSocketConnection() {
        try {
            comSocket = serverSocket.accept(); //accept client & create socket for the communication
            ipClient = String.valueOf(comSocket.getInetAddress());
            portClient = comSocket.getPort();
            System.out.println("Connected to client at " + ipClient + ":" + portClient);
            controller.addText("Connected to client at " + ipClient + ":" + portClient);

            outputStream = new ObjectOutputStream(comSocket.getOutputStream());
            inputStream = new ObjectInputStream(comSocket.getInputStream()); //objects that server receives from client

//            System.out.println("Connection established.");

        } catch (IOException e) {
            System.out.println("An exception occurred !");
            System.out.println(e.getMessage());
        }
    }

    private void closeClientConnection() {
        controller.addText("Closing Client Connection...");
        try {
            if (inputStream != null)
                inputStream.close();
            if (outputStream != null)
                outputStream.close();
            if (comSocket != null && comSocket.isClosed())
                comSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}