package com.genymobile.scrcpy.control;

import android.net.LocalSocket;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;


import java.io.IOException;

public final class ControlChannel {

    private final ControlMessageReader reader;
    private final DeviceMessageWriter writer;

    public ControlChannel(Socket controlSocket) throws IOException {
        reader = new ControlMessageReader(controlSocket.getInputStream());
        writer = new DeviceMessageWriter(controlSocket.getOutputStream());
    }

    public ControlMessage recv() throws IOException {
        return reader.read();
    }

    public void send(DeviceMessage msg) throws IOException {
        writer.write(msg);
    }
}
