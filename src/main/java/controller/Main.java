package controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class Main {


    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        AssignmentServer server = new AssignmentServer(5002);
        Controller controller = new Controller();
        Thread serverthread = new Thread(server);
        Thread controllerthread = new Thread(controller);
        serverthread.start();
        log.info("Warming 5  minutes.");
        Thread.sleep(60*5 * 1000);
        controllerthread.start();

    }
}
