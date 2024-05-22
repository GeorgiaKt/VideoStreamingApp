package org.proj;

public class MultimediaApp {
    public static void main(String[] args) {
        Server server = new Server(8888);
        Client client = new Client();
        server.mainMethod();
    }
}