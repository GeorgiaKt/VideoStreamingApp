package org.proj;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    Socket comSocket;
    PrintWriter out;
    BufferedReader in;
    BufferedReader stdIn;
    private String host = "127.0.0.1";
    private int port = 8888;

    private void establishSocketConnection() {
        try {
            comSocket = new Socket(host, port);
            out = new PrintWriter(comSocket.getOutputStream(), true); //what client sends to server
            in = new BufferedReader(new InputStreamReader(comSocket.getInputStream())); //what client receives from server
            stdIn = new BufferedReader(new InputStreamReader(System.in)); //what client receives from user

            String input;
//            input = stdIn.readLine();
//            System.out.println("User wrote: " + input);
//
//            System.out.println("Client Received " + in.readLine());
//            out.println("Client received back ");


        } catch (IOException e) {
            System.out.println("An exception has occurred on port " + port);
            System.out.println(e.getMessage());
        }
    }

    public void mainMethod() {
        establishSocketConnection();
        try {
            in.close();
            out.close();
            stdIn.close();
            comSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
