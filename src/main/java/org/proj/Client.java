package org.proj;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    private String host = "127.0.0.1";
    private int port = 8888;

    public Client() {
        establishSocketConnection();
    }

    private void establishSocketConnection() {
        try {
            Socket comSocket = new Socket(host, port);
            PrintWriter out = new PrintWriter(comSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(comSocket.getInputStream()));
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

//            String inputLine;
//            while ((inputLine = in.readLine()) != null) {
//                if (inputLine.equals("bye"))
//                    break;
//                out.println(inputLine);
//            }

        } catch (IOException e) {
            System.out.println("An exception has occurred on port " + port);
            System.out.println(e.getMessage());
        }
    }
}
