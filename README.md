# MultimediaApp
A server-client application for video streaming, built with Java, JavaFX, and FFmpeg.
## Description

### To be noted:
- All videos must be placed in videos folder.
- Videos cannot be generated in resolutions higher than the original.
- Supported formats, resolutions, protocols:
    - formats: avi, mp4, mkv
    - resolutions: 240p, 360p, 480p, 720p, 1080p
    - protocols: TCP, UDP, RTP/UDP
- The available for streaming videos depend on the client's download speed, as well as the resolution of the selected video. The maximum video bitrate per resolution is used, based on the Youtube resolutions and bitrates table that follows.
  | Resolution | 240p  | 360p  | 480p  | 720p  | 1080p  |
  |------------|-------|-------|-------|-------|--------|
  | Maximum Video Bitrate | 700 Kbps | 1000 Kbps | 2000 Kbps | 4000 Kbps | 6000 Kbps |
  | Recommended Video Bitrate | 400 Kbps | 750 Kbps | 1000 Kbps | 2500 Kbps | 4500 Kbps |
  | Minimum Video Bitrate | 300 Kbps | 400 Kbps | 500 Kbps | 1500 Kbps | 3000 Kbps |

- If the user does not manually select a protocol, it is chosen automatically, according to the following table.
  |  Resolution  |  Protocol  |
  |--------------|------------|
  |    240p      |    TCP     |
  |  360p, 480p  |    UDP     |
  | 720p, 1080p  |   RTP/UDP  |


## Built with:
- JDK 20.0.1
- JavaFX 22.0.1
- FFMPEG 7.0.
- [FFMPEG Wrapper for Java 0.8.0](https://github.com/bramp/ffmpeg-cli-wrapper)
- [JSpeedTest 1.32.1](https://github.com/bertrandmartel/speed-test-lib)
- Scene Builder 21.0.0
- Log4j2 2.13.1
- Guava 32.1.3
