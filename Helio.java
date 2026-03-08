// HackTJ 2026 - Sohini Ray, Tiya Gezie, Elisha Kim
// 3-7-26, 4:04 AM

// Import classes for making HTTP requests to Google APIs
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;

// Import classes for handling OAuth2 authentication flow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;

// Import helper classes for installed applications to authorize users
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;

// Import classes for handling JSON data
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

// Import the class to store OAuth2 tokens on disk
import com.google.api.client.util.store.FileDataStoreFactory;

// Import Google Calendar API scopes (permissions)
import com.google.api.services.calendar.CalendarScopes;

// Import models representing Calendar events
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.Event;

// Google API DateTime class to send/receive date and time values
import com.google.api.client.util.DateTime;

// JSON parsing library
import org.json.JSONObject;
import org.json.JSONArray;

// Java imports for file handling
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

// Additional IO utilities
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.BufferedReader;

// Exceptions for security and general I/O
import java.security.GeneralSecurityException;

// Networking classes for API requests
import java.net.HttpURLConnection;
import java.net.URL;

// Utilities for formatting dates for display
import java.text.SimpleDateFormat;

// Imports for working with lists and collections
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Map.Entry;

/*
 * ===============================================================
 * MAIN CLASS FOR HELIO
 * Integrates Google Calendar with Terac Expert API
 * ===============================================================
 */
public class Helio 
{

   // Name of the application (shown in Google API console and prompts)
   private static final String APPLICATION_NAME = "Helio";

   // JSON parser instance using Gson
   private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

   // Directory where OAuth tokens will be stored
   private static final String TOKENS_DIRECTORY_PATH = "tokens";

   // Path to downloaded credentials JSON file from Google Cloud Console
   private static final String CREDENTIALS_FILE_PATH = "credentials.json";

   // Scope of access (read-only calendar access)
   private static final List<String> SCOPES =
           Collections.singletonList(CalendarScopes.CALENDAR_READONLY);

   // Terac Production API configuration
   private static final String OPPORTUNITY_ID = "eqcqvajxfoc1aeysheywn6l4";
   private static final String API_KEY = "tk_AkDXfKsLXtSSWBePDkzwusZpgoYvgtLM";


   /*
    * ===============================================================
    * AUTHENTICATE WITH GOOGLE USING OAUTH2
    * ===============================================================
    *
    * This method:
    * 1. Loads credentials.json from disk
    * 2. Starts OAuth authentication flow
    * 3. Opens a browser window for user login
    * 4. Stores tokens locally so login isn't needed again
    *
    * Returns a Credential object used by Google APIs
    */
   private static com.google.api.client.auth.oauth2.Credential getCredentials()
           throws IOException, GeneralSecurityException {
   
      // Load client secrets from credentials.json
      GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
              JSON_FACTORY,
              new FileReader(CREDENTIALS_FILE_PATH));
   
      // Build the OAuth2 flow for installed apps
      GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
              GoogleNetHttpTransport.newTrustedTransport(),
              JSON_FACTORY,
              clientSecrets,
              SCOPES)
         
              // Store tokens locally so user doesn't need to re-login
              .setDataStoreFactory(
                      new FileDataStoreFactory(
                              new java.io.File(TOKENS_DIRECTORY_PATH)))
         
              // Request refresh token so app can access calendar later
              .setAccessType("offline")
              .build();
   
      // Launch local web server to handle OAuth callback
      return new AuthorizationCodeInstalledApp(
              flow,
              new LocalServerReceiver())
              .authorize("user");
   }


   /*
    * ===============================================================
    * MAIN PROGRAM ENTRY POINT
    * ===============================================================
    *
    * Steps:
    * 1. Connect to Google Calendar API
    * 2. Retrieve events from current week
    * 3. Print them to console and file
    * 4. Build schedule text
    * 5. Send schedule to Terac experts
    */
   public static void main(String[] args)
           throws IOException, GeneralSecurityException {
   
      // Build the Google Calendar service object
      com.google.api.services.calendar.Calendar service =
              new com.google.api.services.calendar.Calendar.Builder(
                      GoogleNetHttpTransport.newTrustedTransport(),
                      JSON_FACTORY,
                      getCredentials())
                      .setApplicationName(APPLICATION_NAME)
                      .build();
   
   
      /*
       * ---------------------------------------------------------------
       * CALCULATE WEEK RANGE (LAST SUNDAY → NEXT SUNDAY)
       * ---------------------------------------------------------------
       */
   
      java.util.Calendar cal = java.util.Calendar.getInstance();
   
      // Reset time to midnight for consistency
      cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
      cal.set(java.util.Calendar.MINUTE, 0);
      cal.set(java.util.Calendar.SECOND, 0);
      cal.set(java.util.Calendar.MILLISECOND, 0);
   
      // Get the day of week
      int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
   
      // Move backward to last Sunday
      cal.add(java.util.Calendar.DAY_OF_MONTH,
              -(dayOfWeek - java.util.Calendar.SUNDAY));
   
      DateTime weekStart = new DateTime(cal.getTime());
   
      // Move forward 7 days to next Sunday
      cal.add(java.util.Calendar.DAY_OF_MONTH, 7);
      DateTime weekEnd = new DateTime(cal.getTime());
   
   
      /*
       * ---------------------------------------------------------------
       * FETCH EVENTS FROM GOOGLE CALENDAR
       * ---------------------------------------------------------------
       */
   
      Events events = service.events().list("primary")
              .setTimeMin(weekStart)
              .setTimeMax(weekEnd)
              .setOrderBy("startTime")
              .setSingleEvents(true)
              .execute();
   
      List<Event> items = events.getItems();
   
      if (items.isEmpty()) {
         System.out.println("No events found this week.");
         return;
      }
   
   
      /*
       * ---------------------------------------------------------------
       * SET UP DATE FORMATTING
       * ---------------------------------------------------------------
       */
   
      SimpleDateFormat dayFormat =
              new SimpleDateFormat("EEEE, MMM d");
   
      SimpleDateFormat timeFormat =
              new SimpleDateFormat("HH:mm");
   
      String lastPrintedDay = "";
   
      // StringBuilder to store full schedule text
      StringBuilder scheduleText = new StringBuilder();
   
   
      /*
       * ---------------------------------------------------------------
       * WRITE EVENTS TO FILE + BUILD SCHEDULE STRING
       * ---------------------------------------------------------------
       */
   
      try (PrintWriter writer =
                   new PrintWriter(new FileWriter("weekly_events.txt"))) {
      
         writer.println("Events for the week starting last Sunday:\n");
         System.out.println("Events for the week starting last Sunday:\n");
      
         for (Event event : items) {
         
            // Determine event start time
            java.util.Date eventStart =
                    event.getStart().getDateTime() != null ?
                            new java.util.Date(
                                    event.getStart().getDateTime().getValue())
                            :
                            new java.util.Date(
                                    event.getStart().getDate().getValue());
         
            String currentDay = dayFormat.format(eventStart);
         
            // Print day header if day changed
            if (!currentDay.equals(lastPrintedDay)) {
            
               writer.println("\n=== " + currentDay + " ===");
               System.out.println("\n=== " + currentDay + " ===");
            
               scheduleText.append("\n")
                       .append(currentDay)
                       .append("\n");
            
               lastPrintedDay = currentDay;
            }
         
            // Format event time
            String startTime =
                    (event.getStart().getDateTime() != null)
                            ? timeFormat.format(eventStart)
                            : "All day";
         
            String line =
                    startTime + " - " + event.getSummary();
         
            writer.println(line);
            System.out.println(line);
         
            // Add event to schedule text sent to experts
            scheduleText.append(line).append("\n");
         }
      
         System.out.println("\nWeekly events exported to weekly_events.txt");
      
      
         /*
          * ------------------------------------------------------------
          * SEND SCHEDULE TO TERAC EXPERTS
          * ------------------------------------------------------------
          */
      
         List<String> expertTypes = List.of(
                 "sleep scientist",
                 "mental health expert",
                 "student wellness specialist",
                 "learning expert"
            );
      
         Map<String, String> allAdvice = new HashMap<>();
      
         for (String expert : expertTypes) {
            System.out.println("\nRequesting advice from " + expert + "...");
            String response = fetchExpertAdvice(expert, scheduleText.toString());
         
            System.out.println("Advice received for " + expert);
            allAdvice.put(expert, response);
         
            System.out.println("================================================");
         }
         saveResultsToJson(allAdvice);
      }
   }


   /*
    * ===============================================================
    * FETCH EXPERT ADVICE FROM TERAC (PRODUCTION MODE)
    * ===============================================================
    *
    * Steps:
    * 1. Create a submission request
    * 2. Send schedule + expert type
    * 3. Terac routes request to experts
    * 4. Poll API until response is ready
    * 5. Return expert answer
    */
   public static String fetchExpertAdvice(String expertType, String schedule) 
   {
      try {
        // Direct GET request to the Opportunity's submissions list
         URL url = new URL("https://terac.com/api/external/v1/opportunities/" + OPPORTUNITY_ID + "/submissions");
         HttpURLConnection conn = (HttpURLConnection) url.openConnection();
         conn.setRequestMethod("GET");
         conn.setRequestProperty("x-api-key", API_KEY);
         conn.setRequestProperty("Accept", "application/json");
      
         int responseCode = conn.getResponseCode();
        
        // Detailed Error Reporting
         if (responseCode != 200) {
            InputStream es = conn.getErrorStream();
            String errorMsg = "";
            if (es != null) {
               BufferedReader br = new BufferedReader(new InputStreamReader(es));
               errorMsg = br.readLine();
            }
            return "Server Error (" + responseCode + "): " + errorMsg;
         }
      
        // Parse Response
         BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         StringBuilder sb = new StringBuilder();
         String line;
         while ((line = reader.readLine()) != null) {
            sb.append(line);
         }
      
         JSONObject json = new JSONObject(sb.toString());
         JSONArray submissions = json.getJSONArray("submissions");
      
         if (submissions.length() == 0) {
            return "Waiting for human experts to finish responding to the Opportunity...";
         }
      
        /* * LOGIC: Since you have 4 experts, we'll pick different submissions 
         * from your Active Opportunity to represent each expert.
         */
         int index = Math.abs(expertType.hashCode()) % submissions.length();
         JSONObject submission = submissions.getJSONObject(index);
         JSONArray results = submission.getJSONArray("results");
        
        // Return the text answer from the human expert
         return results.getJSONObject(0).getJSONArray("answer").getString(0);
      
      } catch (Exception e) {
         return "Connection Error: " + e.getMessage();
      }
   }
   
   private static void saveResultsToJson(Map<String, String> adviceMap) 
   {
      JSONObject finalOutput = new JSONObject();
    
    // Add the timestamp and the advice
      finalOutput.put("timestamp", System.currentTimeMillis());
    
      JSONObject experts = new JSONObject();
      for (Map.Entry<String, String> entry : adviceMap.entrySet()) {
         experts.put(entry.getKey().replace(" ", "_"), entry.getValue());
      }
      finalOutput.put("expert_reports", experts);
   
      try (FileWriter file = new FileWriter("terac_reports.json")) {
         file.write(finalOutput.toString(4)); // Indent with 4 spaces
         System.out.println("Successfully saved reports to terac_reports.json");
      } catch (IOException e) 
      {
         System.err.println("Error writing JSON file: " + e.getMessage());
      }
   }
}