package com.chiou.javabridge.Models;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.net.Socket;

public class ClientContext {
    final String clientId;
    final Socket socket;
    final BufferedReader reader;

    public final BufferedWriter Writer;
    public String modName = "Unknown";

    public ClientContext(String clientId, Socket socket, BufferedReader reader, BufferedWriter writer) {
        this.clientId = clientId;
        this.socket = socket;
        this.reader = reader;
        Writer = writer;
    }
}