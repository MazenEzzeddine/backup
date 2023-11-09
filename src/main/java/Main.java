import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;


public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);
    public static void main(String[] args) {
        log.info("Starting the Controller");
        log.info("Warming up for 30 seconds");
        try {
            Thread.sleep(30*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        log.info("Sarting querying prometheus");


        while (true) {
            log.info("Querying Prometheus");
            LaunchQueries.query();
            if(Duration.between(ScaleLogic.lastScaleDecision , Instant.now()).getSeconds() > 60) {
                ScaleLogic.scaleConsumerGroup();
            } else {
                log.info("Cool down no scale");
            }
            log.info("Sleeping for 10 seconds");
            log.info("========================================");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


}
