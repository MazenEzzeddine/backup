import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Util {


    private static final Logger log = LogManager.getLogger(LaunchQueries.class);


    static Double parseJsonArrivalRate(String json) {
        //json string from prometheus
        //{"status":"success","data":{"resultType":"vector","result":[{"metric":{"topic":"testtopic1"},"value":[1659006264.066,"144.05454545454546"]}]}}
      //  log.info(json);
        try {
            JSONObject jsonObject = JSONObject.parseObject(json);
            JSONObject j2 = (JSONObject) jsonObject.get("data");
            JSONArray inter = j2.getJSONArray("result");
            JSONObject jobj = (JSONObject) inter.get(0);
            JSONArray jreq = jobj.getJSONArray("value");
            return Double.parseDouble(jreq.getString(1));
        } catch (Exception e) {
            return -1.0;
        }
        //TODO handle ctach stmt.
    }


    static Double parseJsonArrivalLag(String json, int p) {
        //json string from prometheus
        //{"status":"success","data":{"resultType":"vector","result":[{"metric":{"topic":"testtopic1"},"value":[1659006264.066,"144.05454545454546"]}]}}
        //log.info(json);
        try {
            JSONObject jsonObject = JSONObject.parseObject(json);
            JSONObject j2 = (JSONObject) jsonObject.get("data");
            JSONArray inter = j2.getJSONArray("result");
            JSONObject jobj = (JSONObject) inter.get(0);
            JSONArray jreq = jobj.getJSONArray("value");
            return Double.parseDouble(jreq.getString(1));
        } catch (Exception e) {
           // e.printStackTrace();
            return 0.0;
        }
    }



   // {"status":"success","data":{"resultType":"vector",
    // "result":[{"metric":{"container":"latency","endpoint":"brom","instance":"10.100.3.4:8080","job":"default/demoobservabilitypodmonitor","namespace":"default","pod":"latency-549484b867-dhrdb"},
    // "value":[1695723729.803,"4.306822493494973"]}]}}

    static Double parseJsonLatency(String json) {
        //json string from prometheus
        //{"status":"success","data":{"resultType":"vector","result":[{"metric":{"topic":"testtopic1"},"value":[1659006264.066,"144.05454545454546"]}]}}

        log.info ("String json {}", json);
        try {
            //log.info(json);
            JSONObject jsonObject = JSONObject.parseObject(json);
            JSONObject j2 = (JSONObject) jsonObject.get("data");
            JSONArray inter = j2.getJSONArray("result");
            JSONObject jobj = (JSONObject) inter.get(0);
            JSONArray jreq = jobj.getJSONArray("value");
       /* log.info("the partition is {}", p);
        log.info("partition lag  {}",  Double.parseDouble( jreq.getString(1)));*/
            return Double.parseDouble(jreq.getString(1));
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }
}
