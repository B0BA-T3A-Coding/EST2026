package com.example.hacktj2026;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class HelioService {

    @Value("${terac.api.key}")
    private String apiKey;

    @Value("${terac.opportunity.id}")
    private String opportunityId;

    private final Calendar service;

    // Spring automatically provides the 'service' here
    public HelioService(Calendar service) {
        this.service = service;
    }

    public Map<String, String> analyzeCalendar() {
        try {
            // 1. Get the Schedule Text
            String schedule = fetchWeeklySchedule();
            
            // 2. Ask the Experts
            List<String> expertTypes = List.of("sleep scientist", "mental health expert", "student wellness specialist", "learning expert");
            Map<String, String> advice = new HashMap<>();

            for (String expert : expertTypes) {
                String response = fetchExpertAdvice(expert, schedule);
                advice.put(expert.replace(" ", "_"), response);
            }

            return advice;

        } catch (Exception e) {
            return Map.of("error", "Failed to process: " + e.getMessage());
        }
    }

    private String fetchWeeklySchedule() throws IOException {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
        cal.add(java.util.Calendar.DAY_OF_MONTH, -(dayOfWeek - java.util.Calendar.SUNDAY));
        DateTime start = new DateTime(cal.getTime());
        cal.add(java.util.Calendar.DAY_OF_MONTH, 7);
        DateTime end = new DateTime(cal.getTime());

        Events events = service.events().list("primary")
                .setTimeMin(start).setTimeMax(end)
                .setOrderBy("startTime").setSingleEvents(true).execute();

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat df = new SimpleDateFormat("EEEE: HH:mm");
        
        for (Event event : events.getItems()) {
            long val = (event.getStart().getDateTime() != null) 
                       ? event.getStart().getDateTime().getValue() 
                       : event.getStart().getDate().getValue();
            sb.append(df.format(new Date(val))).append(" - ").append(event.getSummary()).append("\n");
        }
        return sb.toString();
    }

    private String fetchExpertAdvice(String expertType, String schedule) {
        try {
            URL url = new URL("https://terac.com/api/external/v1/opportunities/" + opportunityId + "/submissions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("x-api-key", apiKey);
            
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder res = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) res.append(line);

            JSONObject json = new JSONObject(res.toString());
            JSONArray subs = json.getJSONArray("submissions");
            if (subs.length() == 0) return "Expert is thinking...";

            int index = Math.abs(expertType.hashCode()) % subs.length();
            return subs.getJSONObject(index).getJSONArray("results")
                       .getJSONObject(0).getJSONArray("answer").getString(0);
        } catch (Exception e) {
            return "Connection error.";
        }
    }
}