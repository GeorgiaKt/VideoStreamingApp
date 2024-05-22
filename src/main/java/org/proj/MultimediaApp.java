package org.proj;

public class MultimediaApp {
    public static void main(String[] args) {
        try {
            Server server = new Server(8888);
            Thread serverTh = new Thread(new Runnable() {
                @Override
                public void run() {
                    server.mainMethod();
                }
            });
            serverTh.start();


            if (!Server.isReady.get()) {
                Thread.sleep(100);
            }

            Client client = new Client();
            client.mainMethod();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}