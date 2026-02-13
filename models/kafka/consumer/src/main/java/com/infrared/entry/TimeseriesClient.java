package com.infrared.entry;

import com.infrared.kairosdb.client.*;
import com.infrared.kairosdb.client.builder.AggregatorFactory;
import com.infrared.kairosdb.client.builder.QueryBuilder;
import com.infrared.kairosdb.client.builder.QueryMetric;
import com.infrared.kairosdb.client.builder.TimeUnit;
import com.infrared.kairosdb.client.response.GetResponse;
import com.infrared.kairosdb.client.response.QueryResponse;

import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 *
 * @author asitk
 */
public class TimeseriesClient extends HttpClient {
    String m_url;

    public TimeseriesClient(String url) throws MalformedURLException {
        super(url);
        m_url = new String(url);
    }

    public void GetMetricNames() {
        try {
            // Metric names
            System.out.println("GetMetricNames Url =" + m_url);
            HttpClient client = new HttpClient(m_url);
            GetResponse response = client.getMetricNames();

            System.out.println("GetMetricNames Response Code =" + response.getStatusCode());
            System.out.println("GetMetricNames Response Results =");
            for (String name : response.getResults()) {
                System.out.println(name);
            }
            client.shutdown();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public void GetTagNames() {
        try {
            // Tag names
            HttpClient client = new HttpClient(m_url);
            GetResponse response = client.getTagNames();

            System.out.println("GetTagNames Response =" + response.getStatusCode());
            System.out.println("GetTagNames Response Results =");
            for (String name : response.getResults()) {
                System.out.println(name);
            }
            client.shutdown();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public QueryResponse QueryRelative(String metric, TimeUnit unit) {
        try {

            // Query metrics
            QueryBuilder builder = QueryBuilder.getInstance();

          /*
            builder.setStart(1, TimeUnit.HOURS)
            .setEnd(5, TimeUnit.SECONDS)
            .addMetric("cpu.usage_user")
            .addAggregator(AggregatorFactory.createAverageAggregator(5, TimeUnit.MINUTES));
          */

            builder.setStart(1, unit);
            builder.addMetric(metric);

            HttpClient client = new HttpClient(m_url);
            QueryResponse qresponse = client.query(builder);
            System.out.println("Query: " + qresponse.getBody());
            client.shutdown();

            return qresponse;
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return null;
        }
    }

    public String QueryAbsolute(String q) {
        try {
            // Query metrics
            QueryBuilder builder = QueryBuilder.getInstance();


            JSONParser parser2 = new JSONParser();
            //Object obj2 = parser2.parse(new FileReader("/mnt/data/src/bitbucket.org/infrared/apps/statserver/src/main/java/com/infrared/engine/newjson.json"));
            Object obj2 = parser2.parse(q);
            JSONObject jsonObject2 = (JSONObject) obj2;

            // TODO : using millisecond timestamps

            long start_absolute = (long) jsonObject2.get("start_absolute");
            System.out.println("start_absolute " + start_absolute);
            Timestamp StartTS = new Timestamp(start_absolute);
            Date StartDate = new Date(StartTS.getTime());
            builder.setStart(StartDate);
            System.out.println(StartDate);

            long end_absolute = (long) jsonObject2.get("end_absolute");
            System.out.println("end_absolute " + end_absolute);
            Timestamp EndTS = new Timestamp(end_absolute);
            Date EndDate = new Date(EndTS.getTime());
            builder.setEnd(EndDate);
            System.out.println(EndDate);

            long cache_time = (long) jsonObject2.get("cache_time");
            System.out.println("cache_time " + cache_time);
            if (cache_time > 0) {
                builder.setCacheTime((int) cache_time);
            }

            // loop array
            JSONArray metrics = (JSONArray) jsonObject2.get("metrics");
            Iterator<JSONObject> MetricsIterator = metrics.iterator();

            while (MetricsIterator.hasNext()) {
                JSONObject MetricsObj = MetricsIterator.next();
                String MetricStr = (String) MetricsObj.get("name");
                System.out.println("metric " + MetricStr);
                QueryMetric Metric = builder.addMetric(MetricStr);

                List<String> TagList = new ArrayList<String>();
                Map<String, List<String>> TagsMap = new HashMap<String, List<String>>();

                JSONArray Tags = (JSONArray) MetricsObj.get("tags");
                if (Tags != null && !Tags.isEmpty()) {

                    Iterator<JSONObject> TagsIter = Tags.iterator();

                    while (TagsIter.hasNext()) {
                        JSONObject TagObj = TagsIter.next();
                        String TagStr = (String) TagObj.get("name");
                        System.out.println("tag " + TagStr);

                        JSONArray values = (JSONArray) TagObj.get("values");
                        System.out.println("values " + values);
                        Iterator<String> ValuesIter = values.iterator();
                        while (ValuesIter.hasNext()) {
                            String ValStr = ValuesIter.next();
                            TagList.add(ValStr);
                            System.out.println("val: " + ValStr);
                        }
                        TagsMap.put(TagStr, TagList);
                    }

                    Metric.addMultiValuedTags(TagsMap);
                } else {
                    System.out.println("No tags");
                }
            }

            List MetricList = builder.getMetrics();
            System.out.println("TimeseriesClient QueryAbsolute:" + MetricList.toString());

          /*
            builder.setStart(1, TimeUnit.HOURS)
            .setEnd(5, TimeUnit.SECONDS)
            .addMetric("cpu.usage_user")
            .addAggregator(AggregatorFactory.createAverageAggregator(5, TimeUnit.MINUTES));
          */

            //builder.setStart(1, unit);
            //builder.addMetric(metric);

            HttpClient client = new HttpClient(m_url);
            QueryResponse qresponse = client.query(builder);
            System.out.println("TimeseriesClient Query Result: " + qresponse.getBody());
            client.shutdown();

            return qresponse.getBody();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return null;
        }
    }
}