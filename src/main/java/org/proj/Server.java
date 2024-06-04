package org.proj;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
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


public class Server {
    private final CountDownLatch latch = new CountDownLatch(1); //used for synchronization
    private final String videosPath = "src/main/resources/videos";
    FFprobe ffprobe;
    FFmpeg ffmpeg;
    private File videosDir;
    private File[] files;
    private Table<String, String, Integer> availableFiles;
    private int port;
//    private PrintWriter out;
//    private BufferedReader in;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private ServerSocket serverSocket;
    private Socket comSocket;
    private boolean noVideos;


    public Server(int port) {
        this.port = port; //initialize socket port
        try {
            ffprobe = new FFprobe("/ffmpeg-7.0-full_build/bin/ffprobe");
            ffmpeg = new FFmpeg("/ffmpeg-7.0-full_build/bin/ffmpeg");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Server server = new Server(8888);
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        while (true) { //server is always running - waiting for client to connect
            server.establishSocketConnection(); //connection server-client

            int downloadSpeed;
            String format, protocol, selectedVideo;
            boolean argInitialized = false; //false for sending 1st time format & speed

            Object[] arguments = new Object[2];
            while (true) { //read from stream till client disconnects- load files multiple times
                if (server.comSocket == null || server.comSocket.isClosed()) {
                    System.out.println("Client disconnected - ");
                    break;
                } else {
                    try {
                        if(!argInitialized){ //read from stream only if if its first time running
                            //receive format & download speed from client
                            arguments = (Object[]) server.inputStream.readObject(); //read from client
                        }

                        if (arguments[0] != null && arguments[1] != null) {
                            format = (String) arguments[0];
                            downloadSpeed = (int) arguments[1];

                            System.out.println("format: " + format + " downloadSpeed: " + downloadSpeed);

                            ArrayList<String> videos = new ArrayList<>();
                            if (!server.noVideos) { //if there are videos in folder
                                findSuitableVideos(downloadSpeed, format, videos, server); //find suitable videos based on the format & speed

//                                System.out.println(videos);

                                //sent videos' names to client
                                Object sVideos;
                                sVideos = videos;
                                server.outputStream.writeObject(sVideos);
                                server.outputStream.flush();

                                while (true) { //choose video file multiple times
                                    arguments = (Object[]) server.inputStream.readObject(); //receive selected video & protocol from client
                                    if (!arguments[0].equals("mkv") && !arguments[0].equals("mp4") && !arguments[0].equals("avi")) {
                                        System.out.println("format not selected video");
                                        //protocol can be null
                                        selectedVideo = (String) arguments[0];
                                        protocol = (String) arguments[1];

                                        System.out.println("Selected Video: " + selectedVideo + " protocol: " + protocol);
                                        System.out.println("format: " + format + " speed: " + downloadSpeed + " selectedVideo: " + selectedVideo + " protocol: " + protocol);
                                    }else {
                                        format = (String) arguments[0];
                                        downloadSpeed = (int) arguments[1];
                                        argInitialized = true;
                                        break;
                                    }

                                }
                            } else
                                System.out.println("NO VIDEOS IN FOLDER");
                        } else {
                            System.out.println("Null arguments");
                            break;
                        }

                    } catch (SocketException e) {
                        //client disconnected
                        System.out.println("Client disconnected");
                        break;
                    } catch (EOFException e) {
                        //stream closed (EOF reached)
                        System.out.println("Stream closed by client");
                        break;
                    } catch (IOException | ClassNotFoundException e) {
                        System.out.println("Error reading from client: " + e.getMessage());
                        e.printStackTrace();
                        break;
                    }
                }

            }
            server.closeClientConnection();
        }

    }

    private static void findSuitableVideos(int downloadSpeed, String format, ArrayList<String> videos, Server server) {
        for (Table.Cell<String, String, Integer> cell : server.availableFiles.cellSet()) {
            //add name of the videos that have the format requested & has smaller or the same resolution that can be transmitted with the downloadSpeed of the client
            if (cell.getColumnKey().equals(format)) {
                if (cell.getValue() <= findSuitableRes(downloadSpeed)) { //if video's resolution is smaller/or same than what the download speed allows
                    videos.add(cell.getRowKey());
                }
            }
        }
    }

    private static Integer findSuitableRes(int downloadSpeed) {
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

    private File[] getListOfFiles() {
        videosDir = new File(videosPath);
        files = videosDir.listFiles();
        //print list of files in videos directory
        if (files == null) {
            System.out.println("Folder not found !");
        } else if (files.length == 0)
            System.out.println("No videos found in folder !");
        else {
            return files;
        }
        return new File[0];
    }

    private void createRemainingVideos() throws IOException {
        //create videos in order each one to exist in avi, mkv, mp4 and in all resolutions smaller that the original
        for (File file : files) {
            //initialize local variables filePath, fileName, fileExtension for each file
            String filePath = file.getAbsolutePath();
            String fileName = FilenameUtils.getBaseName(filePath);
            String fileExtension = FilenameUtils.getExtension(filePath);

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
            System.out.println("Connected to client at " + comSocket.getInetAddress() + ":" + comSocket.getPort());

//            out = new PrintWriter(comSocket.getOutputStream(), true); //what server sends to client
//            in = new BufferedReader(new InputStreamReader(comSocket.getInputStream())); //what server receives from client

            outputStream = new ObjectOutputStream(comSocket.getOutputStream());
            inputStream = new ObjectInputStream(comSocket.getInputStream()); //objects that server receives from client


//            System.out.println("Connection established.");

        } catch (IOException e) {
            System.out.println("An exception occurred !");
            System.out.println(e.getMessage());
        }
    }

    private void closeClientConnection() {
        try {
//            if (out != null)
//                out.close();
//            if (in != null)
//                in.close();
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