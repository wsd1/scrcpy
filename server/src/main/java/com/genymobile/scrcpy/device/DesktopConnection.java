package com.genymobile.scrcpy.device;

import com.genymobile.scrcpy.control.ControlChannel;
import com.genymobile.scrcpy.util.IO;
import com.genymobile.scrcpy.util.StringUtils;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;


import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


import java.lang.reflect.Field;


public final class DesktopConnection implements Closeable {

    private static final int DEVICE_NAME_FIELD_LENGTH = 64;

    private static final String SOCKET_NAME_PREFIX = "scrcpy";

    private final Socket videoSocket;
    private final FileDescriptor videoFd;

    private final Socket audioSocket;
    private final FileDescriptor audioFd;

    private final Socket controlSocket;
    private final ControlChannel controlChannel;

    private DesktopConnection(Socket videoSocket, Socket audioSocket, Socket controlSocket) throws IOException {
        this.videoSocket = videoSocket;
        this.audioSocket = audioSocket;
        this.controlSocket = controlSocket;



        try {
            this.videoFd = videoSocket != null ? getFileDescriptor(videoSocket) : null;
            this.audioFd = audioSocket != null ? getFileDescriptor(audioSocket) : null;
            this.controlChannel = controlSocket != null ? new ControlChannel(controlSocket) : null;
        } catch (Exception e) {
            // 将异常转换为 IOException
            throw new IOException("DesktopConnection:Failed to get socket file descriptor", e);
        }


    }

    private static FileDescriptor getFileDescriptor(Socket socket) throws IOException {
        try {
            // 尝试获取 Socket 的 impl 字段
            Field implField = Socket.class.getDeclaredField("impl");
            implField.setAccessible(true);
            Object impl = implField.get(socket);
            
            // 尝试从实现类中获取文件描述符
            Class<?> clazz = impl.getClass();
            
            // 尝试可能的字段名 - Android 可能使用不同的字段名
            String[] possibleFieldNames = {"fd", "fileDescriptor", "fis", "socket"};
            
            for (String fieldName : possibleFieldNames) {
                try {
                    Field fdField = clazz.getDeclaredField(fieldName);
                    fdField.setAccessible(true);
                    Object result = fdField.get(impl);
                    if (result instanceof FileDescriptor) {
                        return (FileDescriptor) result;
                    }
                } catch (NoSuchFieldException e) {
                    // 尝试下一个可能的字段名
                    continue;
                }
            }
            
            throw new IOException("Could not find file descriptor field in socket implementation");
        } catch (Exception e) {
            throw new IOException("Failed to get socket file descriptor", e);
        }
    }


    private static LocalSocket connect(String abstractName) throws IOException {
        LocalSocket localSocket = new LocalSocket();
        localSocket.connect(new LocalSocketAddress(abstractName));
        return localSocket;
    }

    private static String getSocketName(int scid) {
        if (scid == -1) {
            // If no SCID is set, use "scrcpy" to simplify using scrcpy-server alone
            return SOCKET_NAME_PREFIX;
        }

        return SOCKET_NAME_PREFIX + String.format("_%08x", scid);
    }

    public static DesktopConnection open(int scid, boolean tunnelForward, boolean video, boolean audio, boolean control, boolean sendDummyByte)
            throws IOException {
        String socketName = getSocketName(scid);

        Socket videoSocket = null;
        Socket audioSocket = null;
        Socket controlSocket = null;
        try {
            if (tunnelForward) {
                try (ServerSocket serverSocket = new ServerSocket(12340, 50, InetAddress.getByName("0.0.0.0"))) {
                    if (video) {
                        videoSocket = serverSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            videoSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                }

                try (ServerSocket serverSocket = new ServerSocket(12342, 50, InetAddress.getByName("0.0.0.0"))) {
                    if (audio) {
                        audioSocket = serverSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            audioSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                }

                try (ServerSocket serverSocket = new ServerSocket(12344, 50, InetAddress.getByName("0.0.0.0"))) {
                    if (control) {
                        controlSocket = serverSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            controlSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                }
            }
            /*
            
             else {
                if (video) {
                    videoSocket = connect(socketName);
                }
                if (audio) {
                    audioSocket = connect(socketName);
                }
                if (control) {
                    controlSocket = connect(socketName);
                }
            }
            */
        } catch (IOException | RuntimeException e) {
            if (videoSocket != null) {
                videoSocket.close();
            }
            if (audioSocket != null) {
                audioSocket.close();
            }
            if (controlSocket != null) {
                controlSocket.close();
            }
            throw e;
        }

        return new DesktopConnection(videoSocket, audioSocket, controlSocket);
    }

    private Socket getFirstSocket() {
        if (videoSocket != null) {
            return videoSocket;
        }
        if (audioSocket != null) {
            return audioSocket;
        }
        return controlSocket;
    }

    public void shutdown() throws IOException {
        if (videoSocket != null) {
            videoSocket.shutdownInput();
            videoSocket.shutdownOutput();
        }
        if (audioSocket != null) {
            audioSocket.shutdownInput();
            audioSocket.shutdownOutput();
        }
        if (controlSocket != null) {
            controlSocket.shutdownInput();
            controlSocket.shutdownOutput();
        }
    }

    public void close() throws IOException {
        if (videoSocket != null) {
            videoSocket.close();
        }
        if (audioSocket != null) {
            audioSocket.close();
        }
        if (controlSocket != null) {
            controlSocket.close();
        }
    }

    public void sendDeviceMeta(String deviceName) throws IOException {
        byte[] buffer = new byte[DEVICE_NAME_FIELD_LENGTH];

        byte[] deviceNameBytes = deviceName.getBytes(StandardCharsets.UTF_8);
        int len = StringUtils.getUtf8TruncationIndex(deviceNameBytes, DEVICE_NAME_FIELD_LENGTH - 1);
        System.arraycopy(deviceNameBytes, 0, buffer, 0, len);
        // byte[] are always 0-initialized in java, no need to set '\0' explicitly


        try {
            FileDescriptor fd = getFileDescriptor(getFirstSocket());
            IO.writeFully(fd, buffer, 0, buffer.length);
        } catch (Exception e) {
            // 将异常转换为 IOException
            throw new IOException("sendDeviceMeta:Failed to get socket file descriptor", e);
        }



    }

    public FileDescriptor getVideoFd() {
        return videoFd;
    }

    public FileDescriptor getAudioFd() {
        return audioFd;
    }

    public ControlChannel getControlChannel() {
        return controlChannel;
    }
}
