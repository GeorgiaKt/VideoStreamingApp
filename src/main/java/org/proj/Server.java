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


public class Server {
    private final String videosPath = "src/main/resources/videos";
    private File videosDir;
    private File[] files;
    FFprobe ffprobe;
    FFmpeg ffmpeg;
    private Table<String, String, Integer> availableFiles;
    private int port;
    private PrintWriter out;
    private BufferedReader in;
    private ObjectInputStream inputStream;
    private ServerSocket serverSocket;
    private Socket comSocket;




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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        server.establishSocketConnection();


        //arguments = download speed & format that client sends to server
        Object[] arguments = new Object[2];
        try {
            arguments = (Object[]) server.inputStream.readObject();
        } catch (SocketException e) {
            System.err.println("SocketException: Connection reset by peer");
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        int downloadSpeed = (int) arguments[0];
        String format = (String) arguments[1];

        System.out.println("format: " + format + " downloadSpeed: " + downloadSpeed);

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
            String targetFormat; //new's file extension
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
                    //create remaining files based on the resolution - with resolution smaller than the original one
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
        //override is false in order not to create files that already exist
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        executor.createJob(builder).run();
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

    private void storeAvailableFiles() throws IOException {
        files = videosDir.listFiles(); //refresh list
        //table structure: name (rowKey), format (columnKey), resolution (value)
        if (files.length > 0) {
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
        } else
            System.out.println("No videos found in folder !");

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
            serverSocket = new ServerSocket(port); //create socket
            System.out.println("Server is running...");
            comSocket = serverSocket.accept(); //accept client & create socket for the communication
            System.out.println("Connected to client at " + comSocket.getInetAddress() + ":" + comSocket.getPort());

            out = new PrintWriter(comSocket.getOutputStream(), true); //what server sends to client
            in = new BufferedReader(new InputStreamReader(comSocket.getInputStream())); //what server receives from client

            inputStream = new ObjectInputStream(comSocket.getInputStream()); //objects that server receives from client

//            System.out.println("Connection established.");

        } catch (IOException e) {
            System.out.println("An exception occurred !");
            System.out.println(e.getMessage());
        }
    }


}
