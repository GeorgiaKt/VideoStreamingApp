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
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;


public class Server extends Application {
    static Logger log = LogManager.getLogger(Server.class);
    private static ServerController controller; //static in order not to be collected by the garbage collector :(
    private final CountDownLatch latch = new CountDownLatch(1); //used for synchronization - wait for data to be stored in a Table
    private final String videosFolderPath = "src/main/resources/videos";
    private final String[] extensions = {"mp4", "avi", "mkv"}; //3 extensions in total
    private final Integer[] resolutions = {1080, 720, 480, 360, 240}; // 5 resolutions in total
    private final int port; //socket port
    private final String ffmpegPath = "C:/ffmpeg-7.0-full_build/bin/ffmpeg.exe";
    private final FFprobe ffprobe;
    private final FFmpeg ffmpeg;
    private static File videosFolder;
    private File[] files;
    private boolean noVideos; //flag, false if there are videos, true if there are no videos
    private String filePath;
    private String fileName;
    private String fileExtension;
    private Table<String, String, Integer> availableFiles; //videos available - name (rowKey), format (columnKey), resolution (value)
    private ServerSocket serverSocket;
    private Socket comSocket;
    private String ipClient;
    private int portClient;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;


    public Server() {
        //initialize socket port & ffprobe, ffmpeg
        this.port = 8888;
        try {
            ffprobe = new FFprobe("/ffmpeg-7.0-full_build/bin/ffprobe");
            ffmpeg = new FFmpeg("/ffmpeg-7.0-full_build/bin/ffmpeg");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        filePath = "";
        fileName = "";
        fileExtension = "";

    }

    public static void main(String[] args) {
        launch(); //launch gui
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

    @Override
    public void start(Stage stage) throws Exception {
        //load gui
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

                Platform.exit(); //exit application
                System.exit(0);
            }

        });

        stage.show();

        new Thread(this::runServer).start();
    }

    private void runServer() { //main server method
        Server server = new Server();
        server.files = server.getListOfFiles(); //get list of files in videos folder

//        //print files in folder
//        System.out.println("List of available files in folder:"); //print files in folder
//        for (File file : server.files) {
//            System.out.println(file.getName() + " " + file.getAbsolutePath());
//        }

        try {
            //create remaining videos (formats: mkv, mp4, avi & resolutions < original) & store them in Table
            server.createRemainingVideos();
            server.storeAvailableFiles();
            server.latch.await(); //wait the data to be stored
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            server.serverSocket = new ServerSocket(server.port); //create socket
//            System.out.println("Server is running...");
            log.info("Server is running...");
            controller.addText("Server is running...");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        while (true) { //server is always running - waiting for client to connect
            server.establishSocketConnection(); //connection server-client

            //local variables
            int downloadSpeed;
            String format, protocol, selectedVideo;
            boolean argInitialized = false; //false for receiving 1st time format & speed, true for more than once

            Object[] arguments = new Object[2];
            while (true) { //read from stream till client disconnects - load files multiple times
                try {
                    //1st receive format & download speed
                    if (!argInitialized) { //read from stream only if its first time running
                        //receive format & download speed from client
                        arguments = (Object[]) server.inputStream.readObject(); //read from client
                    }

                    format = (String) arguments[0];
                    downloadSpeed = (int) arguments[1];

                    controller.addText("Received: format: " + format + ", Download speed: " + downloadSpeed + " Kbps");
                    log.debug("Received: format: " + format + ", Download speed: " + downloadSpeed + " Kbps");
//                            System.out.println("format: " + format + " downloadSpeed: " + downloadSpeed);

                    ArrayList<String> videos = new ArrayList<>(); //list of video's available for streaming
                    if (!server.noVideos) { //if there are videos in folder
                        server.findSuitableVideos(downloadSpeed, format, videos, server); //find suitable videos based on the speed & format - fill arraylist videos

                        //2nd send list of videos available for streaming
                        //sent list with videos' names to client
                        Object sVideos;
                        sVideos = videos;
                        server.outputStream.writeObject(sVideos);
                        server.outputStream.flush();

                        while (true) { //choose video file multiple times - stream multiple times
                            //3rd receive selected video's name & protocol
                            arguments = (Object[]) server.inputStream.readObject(); //receive selected video & protocol from client
                            //if 1st argument is not one of the formats, stream the selected video, else is format & speed so break
                            if (!arguments[0].equals("mkv") && !arguments[0].equals("mp4") && !arguments[0].equals("avi")) {
                                selectedVideo = (String) arguments[0];
                                protocol = (String) arguments[1]; //protocol can be null

                                log.info("Requested Video Info: " + " Name: " + selectedVideo + ", Format: " + format + ", Protocol: " + protocol);
                                controller.addText("Requested Video Info: " + " Name: " + selectedVideo + ", Format: " + format + ", Protocol: " + protocol);

                                //get path & resolution of the selected video
                                String path = server.getVideoPath(selectedVideo, format, server);
                                int resolution = server.getVideoResolution(selectedVideo, server);

                                if (path != null && fileExists(selectedVideo)) {
                                    if (protocol == null) { //if protocol not selected
                                        //sent video's res, that way the client will run the right command to play the video
                                        server.outputStream.writeObject(resolution);
                                        server.outputStream.flush();
                                    }

                                    log.debug("Selected Video's Path: " + path + ", Resolution: " + resolution);
                                    //4th stream video
                                    server.streamVideo(protocol, path, resolution); //stream video to client
                                } else
                                    log.error("Video not found !");

                            } else {
                                //if the arguments are format & speed, initialize variables and break the loop
                                format = (String) arguments[0];
                                downloadSpeed = (int) arguments[1];
                                argInitialized = true;
                                break;
                            }

                        }
                    } else { //if there are no videos in folder
                        //send a null object
//                        Object noVideosObj = null;
                        server.outputStream.writeObject(null);
                        server.outputStream.flush();
                    }

                } catch (SocketException e) {
                    //client disconnected
                    log.error("Client disconnected !");
                    controller.addText("Client disconnected !");
                    break;
                } catch (EOFException e) {
                    //stream closed (EOF reached)
                    log.error("Stream closed by client !");
                    controller.addText("Stream closed by client !");
                    break;
                } catch (IOException | ClassNotFoundException e) {
                    log.error("Error reading from client: " + e.getMessage());
                    controller.addText("Error reading from client: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }


            }
            server.closeClientConnection();
        }

    }

    private File[] getListOfFiles() {
        videosFolder = new File(videosFolderPath);
        files = videosFolder.listFiles();
        if (files == null) {
//            System.out.println("FAILED: Folder not found !");
            log.error("FAILED: Folder not found !");
            controller.addText("FAILED: Folder not found !");
        } else if (files.length == 0) {
            noVideos = true;
//            System.out.println("FAILED: No videos found in folder !");
            log.error("No videos found in folder !");
            controller.addText("No videos found in folder !");
        } else {
            noVideos = false;
            return files;
        }
        return new File[0];
    }

    private void createRemainingVideos() throws IOException {
        controller.addText("Creating videos...");
        //create videos in order each one to exist in avi, mkv, mp4 and in all resolutions smaller than the original
        for (File file : files) {
            //initialize local variables filePath, fileName, fileExtension for each file
            filePath = file.getAbsolutePath();
            fileName = FilenameUtils.getBaseName(filePath);
            fileExtension = FilenameUtils.getExtension(filePath);

            FFmpegProbeResult probeResult = ffprobe.probe(filePath);
            FFmpegStream stream = probeResult.getStreams().get(0);

            String targetFormat; //new file's extension
            for (String ext : extensions) { //for each supported extension
                //create remaining files based on the extension
                if (ext.equals("mkv")) //mkv has different format
                    targetFormat = "matroska";
                else
                    targetFormat = ext;

                //create files with different extensions than original
                if (!fileExtension.equals(ext)) {
                    File targetFile = new File(videosFolder, fileName + "." + ext);
                    createTargetFile(ffprobe, ffmpeg, targetFile, filePath, targetFormat, stream.height); //resolution stays the same
                }

                for (Integer res : resolutions) {
                    //create remaining files based on the resolution - with resolution smaller than the original
                    if (stream.height > res) { //for each smaller resolution
                        String targetFileName = fileName;
                        //in case there is already a file with the same file name (that includes resolution)
                        if (fileName.endsWith(stream.height + "p")) {
                            targetFileName = StringUtils.removeEnd(targetFileName, stream.height + "p"); //remove resolution from file name
                        }
                        File targetFile = new File(videosFolder, targetFileName + res + "p." + ext); //add resolution at the end of the name
                        createTargetFile(ffprobe, ffmpeg, targetFile, filePath, targetFormat, res);
                    }
                }

            }

        }
    }

    private void createTargetFile(FFprobe ffprobe, FFmpeg ffmpeg, File file, String filePath, String targetFormat, Integer targetHeight) {
        int targetWidth = getTargetWidth(targetHeight); //get matching width for the specific height
        //create the output file
//        System.out.println("Creating file: " + file.getAbsolutePath());
        log.info("Creating file: " + file.getAbsolutePath());
        //create new video
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
        files = videosFolder.listFiles(); //refresh list (needed in order to add the new ones)
        //table structure: name (rowKey), format (columnKey), resolution (value)
        if (!noVideos) { //if there are videos
            availableFiles = HashBasedTable.create();
            for (File file : files) {
                //initialize local variables filePath, fileName, fileExtension for each file
                filePath = file.getAbsolutePath();
                fileName = FilenameUtils.getBaseName(filePath);
                fileExtension = FilenameUtils.getExtension(filePath);

                FFmpegProbeResult probeResult = ffprobe.probe(filePath);
                FFmpegStream stream = probeResult.getStreams().get(0);

                //store file to Table
                availableFiles.put(fileName, fileExtension, stream.height);
            }
        }
        latch.countDown(); //notify main thread that the data has been stored
    }

    private void establishSocketConnection() {
        try {
            comSocket = serverSocket.accept(); //accept client & create socket for the communication
            ipClient = String.valueOf(comSocket.getInetAddress());
            portClient = comSocket.getPort();
            log.info("Connected to client at " + ipClient + ":" + portClient);
            controller.addText("Connected to client at " + ipClient + ":" + portClient);

            outputStream = new ObjectOutputStream(comSocket.getOutputStream()); //object(s) sent to client
            inputStream = new ObjectInputStream(comSocket.getInputStream()); //object(s) received from client

        } catch (IOException e) {
            log.error("Failed to connect to client !");
            System.out.println(e.getMessage());
        }
    }

    private void findSuitableVideos(int downloadSpeed, String format, ArrayList<String> videos, Server server) {
        //find suitable videos based on the download speed & format selected
        for (Table.Cell<String, String, Integer> cell : server.availableFiles.cellSet()) {
            //add name of the videos that have the format requested & has smaller or the same resolution that can be transmitted with the downloadSpeed of the client
            if (cell.getColumnKey().equals(format)) {
                if (cell.getValue() <= getSuitableRes(downloadSpeed)) { //if video's resolution is smaller/or same than what the download speed allows
                    videos.add(cell.getRowKey());
                }
            }
        }
    }

    private int getSuitableRes(int downloadSpeed) {
        //max download speed for each resolution used - returns resolution
        if (downloadSpeed <= 700) {
            return 240;
        } else if (downloadSpeed <= 1000) {
            return 360;
        } else if (downloadSpeed <= 2000) {
            return 480;
        } else if (downloadSpeed <= 4000) {
            return 720;
        } else {
            return 1080;
        }
    }

    private String getVideoPath(String selectedVideo, String format, Server server) {
        for (Table.Cell<String, String, Integer> cell : server.availableFiles.cellSet()) {
            if (cell.getColumnKey().equals(format)) { //format
                if (cell.getRowKey().equals(selectedVideo)) { //name
                    return videosFolderPath + "/" + cell.getRowKey() + "." + cell.getColumnKey();
                }
            }
        }
        return null;
    }

    private int getVideoResolution(String selectedVideo, Server server) {
        for (Table.Cell<String, String, Integer> cell : server.availableFiles.cellSet()) {
            if (cell.getRowKey().equals(selectedVideo)) { //name
                return cell.getValue();
            }
        }
        return 0;
    }

    private boolean fileExists(String selectedVideo) {
        files = videosFolder.listFiles(); //refresh list
        for (File file : files) {
            //initialize local variables filePath, fileName, fileExtension for each file
            filePath = file.getAbsolutePath();
            fileName = FilenameUtils.getBaseName(filePath);
            fileExtension = FilenameUtils.getExtension(filePath);

            if(fileName.equals(selectedVideo))
                return true;

        }
        return false;
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

            //based on the protocol, stream
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
                //run the command in a new thread - in order the gui to remain responsive
                new Thread(() -> {
                    ProcessBuilder pb = new ProcessBuilder(command).inheritIO();
                    try {
                        Process process = pb.start();
                        controller.addText("Streaming Video...");
                        log.info("Streaming started");
                        process.waitFor(); //wait for process to end
                        controller.addText("Streaming completed");
                        log.info("Streaming completed");
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();


            }

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
        log.info("Client connection closed");
    }

}