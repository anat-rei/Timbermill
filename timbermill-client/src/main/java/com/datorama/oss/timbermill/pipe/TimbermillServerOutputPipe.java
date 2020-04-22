package com.datorama.oss.timbermill.pipe;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.http.HttpHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datorama.oss.timbermill.unit.Event;
import com.datorama.oss.timbermill.unit.EventsWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class TimbermillServerOutputPipe implements EventOutputPipe {

    private static final int HTTP_TIMEOUT = 5000;
    private static final int MAX_RETRY = 5;
    private static final Logger LOG = LoggerFactory.getLogger(TimbermillServerOutputPipe.class);
    private static volatile boolean keepRunning = true;
    private int maxCharsAllowedForNonAnalyzedFields;
    private int maxCharsAllowedForAnalyzedFields;
    private URL timbermillServerUrl;
    private LinkedBlockingQueue<Event> buffer;
    private Thread thread;
    private int maxEventsBatchSize;
    private long maxSecondsBeforeBatchTimeout;

    private TimbermillServerOutputPipe() {
    }

    TimbermillServerOutputPipe(TimbermillServerOutputPipeBuilder builder){
        if (builder.timbermillServerUrl == null){
            throw new RuntimeException("Must enclose the Timbermill server URL");
        }
        try {
            HttpHost httpHost = HttpHost.create(builder.timbermillServerUrl);
            timbermillServerUrl = new URL(httpHost.toURI() + "/events");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        maxEventsBatchSize = builder.maxEventsBatchSize;
        maxSecondsBeforeBatchTimeout = builder.maxSecondsBeforeBatchTimeout;
        maxCharsAllowedForNonAnalyzedFields = builder.maxCharsAllowedForNonAnalyzedFields;
        maxCharsAllowedForAnalyzedFields = builder.maxCharsAllowedForAnalyzedFields;
        buffer = new LinkedBlockingQueue<>(builder.maxBufferSize);
        thread = new Thread(() -> {
            do {
                try {
                    List<Event> eventsToSend = getEventsToSend();
                    if (!eventsToSend.isEmpty()) {
                        EventsWrapper eventsWrapper = new EventsWrapper(eventsToSend);
                        sendEvents(eventsWrapper);
                    }
                } catch (Exception e) {
                    LOG.error("Error sending events to Timbermill server" ,e);
                }
            } while (keepRunning);
        });
        thread.setName("timbermill-sender");
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            keepRunning = false;
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }));
    }

    private void sendEvents(EventsWrapper eventsWrapper) throws IOException {
        String eventsWrapperString = getEventsWrapperBytes(eventsWrapper);
        for (int tryNum = 1; tryNum <= MAX_RETRY; tryNum++) {
            try {
                HttpURLConnection httpCon = getHttpURLConnection();
                sendEventsOverConnection(httpCon, eventsWrapperString.getBytes());
                int responseCode = httpCon.getResponseCode();
                if (responseCode == 200) {
                    LOG.debug("{} events were sent to Timbermill server", eventsWrapper.getEvents().size());
                    return;

                } else {
                    LOG.warn("Request #" + tryNum + " to Timbermill return status {}, Attempt: {}/{} Message: {}", responseCode, tryNum, MAX_RETRY, httpCon.getResponseMessage());
                }
            } catch (Exception e){
                LOG.warn("Request #" + tryNum + " to Timbermill failed, Attempt: "+ tryNum + "/" + MAX_RETRY, e);
            }
            try {
                Thread.sleep((long) (Math.pow(2 , tryNum) * 1000)); //Exponential backoff
            } catch (InterruptedException ignored) {
            }
        }
        LOG.error("Can't send events to Timbermill, failed {} attempts.\n Failed request: {} " , MAX_RETRY, eventsWrapperString);
    }

    private void sendEventsOverConnection(HttpURLConnection httpCon, byte[] eventsWrapperBytes) throws IOException {
		try (OutputStream os = httpCon.getOutputStream()) {
			os.write(eventsWrapperBytes);
		}
    }

    private String getEventsWrapperBytes(EventsWrapper eventsWrapper) throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        ObjectWriter ow = om.writerFor(om.getTypeFactory().constructType(EventsWrapper.class));
        return ow.writeValueAsString(eventsWrapper);
    }

    private HttpURLConnection getHttpURLConnection() throws IOException {
        HttpURLConnection httpURLConnection = (HttpURLConnection) timbermillServerUrl.openConnection();
        httpURLConnection.setRequestMethod("POST");
        httpURLConnection.setRequestProperty("content-type", "application/json");
        httpURLConnection.setDoOutput(true);
        httpURLConnection.setConnectTimeout(HTTP_TIMEOUT);
        httpURLConnection.setReadTimeout(HTTP_TIMEOUT);
        return httpURLConnection;
    }

    private List<Event> getEventsToSend() {
        long startBatchTime = System.currentTimeMillis();
        List<Event> eventsToSend = new ArrayList<>();
        try {
            long currentBatchSize = addEventFromBufferToList(eventsToSend);
            while(currentBatchSize <= this.maxEventsBatchSize && !isExceededMaxTimeToWait(startBatchTime)) {
                currentBatchSize  += addEventFromBufferToList(eventsToSend);
            }
        } catch (InterruptedException e) {
            // If blocking queue poll timed out send current batch
        }
        return eventsToSend;
    }

    private long addEventFromBufferToList(List<Event> eventsToSend) throws InterruptedException {
        Event event = buffer.poll();
        if (event == null){
            Thread.sleep(100);
            return 0;
        }
        cleanEvent(event);
        eventsToSend.add(event);
		return event.estimatedSize();
    }

	private void cleanEvent(Event event) {
        Map<String, String> strings = event.getStrings();
        if (strings.isEmpty()){
			event.setStrings(null);
		}
		else{
            trimLongValues(strings, "strings", maxCharsAllowedForNonAnalyzedFields);
        }

        Map<String, String> context = event.getContext();
        if (context.isEmpty()){
            event.setContext(null);
        }
        else {
            trimLongValues(context, "context", maxCharsAllowedForNonAnalyzedFields);
        }

        Map<String, String> text = event.getText();
        if (text.isEmpty()){
            event.setText(null);
        }
        else {
            trimLongValues(text, "text", maxCharsAllowedForAnalyzedFields);
        }
		if (event.getMetrics().isEmpty()){
			event.setMetrics(null);
		}
		if (event.getLogs().isEmpty()){
			event.setLogs(null);
		}

	}

    private void trimLongValues(Map<String, String> map, String mapName, int maxChars) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.length() > maxChars) {
                LOG.debug("Entry with key {} under {} is over max character allowed {}", entry.getKey(), mapName, maxChars);
                entry.setValue(value.substring(0, maxChars));
            }
        }
    }

    private boolean isExceededMaxTimeToWait(long startBatchTime) {
        return System.currentTimeMillis() - startBatchTime > maxSecondsBeforeBatchTimeout * 1000;
    }

    @Override
    public void send(Event e) {
        if(!this.buffer.offer(e)){
            LOG.warn("Event {} was removed from the queue due to insufficient space", e.getTaskId());
        }
    }

	@Override public int getCurrentBufferSize() {
		return buffer.size();
	}

}