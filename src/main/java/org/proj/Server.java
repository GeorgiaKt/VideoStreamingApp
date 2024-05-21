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

import java.io.File;
import java.io.IOException;


public class Server {
    //String videosPath = "C:\\Users\\geo\\OneDrive\\_Programming\\Java\\IntelliJ\\MultimediaApp\\src\\main\\resources\\videos";
    String videosPath = "src/main/resources/videos";
    File videosDir;
    File[] files;

    //method for setting matching target width for each height
    private static int getTargetWidth(int height) {
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

    private void createTargetFile(FFprobe ffprobe, FFmpeg ffmpeg, File file, String filePath, String targetFormat, Integer targetHeight) {
        int targetWidth = getTargetWidth(targetHeight); //get matching width
        //create the output file
        System.out.println("Creating file: " + file.getAbsolutePath());
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(filePath)
                .overrideOutputFiles(false)
                .addOutput(file.getAbsolutePath())
                .setFormat(targetFormat)
                .setVideoResolution(targetWidth, targetHeight)
                .done();
        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        executor.createJob(builder).run();
    }

    private void createRemainingVideos() throws IOException {

        FFprobe ffprobe = new FFprobe("C:\\ffmpeg-7.0-full_build\\bin\\ffprobe");
        FFmpeg ffmpeg = new FFmpeg("C:\\ffmpeg-7.0-full_build\\bin\\ffmpeg");
        for (File file : files) {
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


            String[] extensions = {"mp4", "avi", "mkv"};
            Integer[] resolutions = {1080, 720, 480, 360, 240}; // 5 resolutions in total
            String targetFormat;
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
                    //create remaining files based on the resolution - with resolution smaller than the current one
                    if (stream.height > res) {
                        String targetFileName = fileName;
                        //in case there is already a file with the same file name (that includes resolution)
                        if (fileName.endsWith(stream.height + "p")) {
                            targetFileName = StringUtils.removeEnd(targetFileName, stream.height + "p"); //remove resolution from file name
                        }
                        File targetFile = new File(videosDir, targetFileName + res + "p." + ext);
                        createTargetFile(ffprobe, ffmpeg, targetFile, filePath, targetFormat, res);
                    }
                }

            }

        }
    }


    public void mainMethod() {
        File[] files = getListOfFiles();
        System.out.println("List of available videos in folder:");
        for (File file : files) {
            System.out.println(file.getName() + file.getAbsolutePath());
        }

        try {
            createRemainingVideos();
            storeAvailableFiles();
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    private void storeAvailableFiles() throws IOException{
        files = videosDir.listFiles(); //refresh list

        //table structure: name (rowKey), format (columnKey), resolution (value)
        Table<String, String, Integer> availableFiles = HashBasedTable.create();
        FFprobe ffprobe = new FFprobe("C:\\ffmpeg-7.0-full_build\\bin\\ffprobe");
        for (File file : files) {
            String filePath = file.getAbsolutePath();
            String fileName = FilenameUtils.getBaseName(filePath);
            String fileExtension = FilenameUtils.getExtension(filePath);

            FFmpegProbeResult probeResult = ffprobe.probe(filePath);
            FFmpegStream stream = probeResult.getStreams().get(0);

            //store file to Table
            availableFiles.put(fileName, fileExtension, stream.height);
        }

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
}
