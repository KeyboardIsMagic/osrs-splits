package com.Utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Scanner;

public class HttpUtil {
    public static String postRequest(String urlString, String jsonInput) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Write the JSON input to the request body
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonInput.getBytes());
            os.flush();
        }

        // Get the response
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (Scanner scanner = new Scanner(conn.getInputStream())) {
                scanner.useDelimiter("\\A"); // Read the entire stream
                return scanner.hasNext() ? scanner.next() : "";
            }
        } else {
            throw new Exception("HTTP error code: " + responseCode);
        }
    }

    public static String getRequest(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        // Get the response
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (Scanner scanner = new Scanner(conn.getInputStream())) {
                scanner.useDelimiter("\\A"); // Read the entire stream
                return scanner.hasNext() ? scanner.next() : "";
            }
        } else {
            throw new Exception("HTTP GET error code: " + responseCode);
        }
    }
    public static void sendUniqueDiscord(String urlString, List<String> partyList, String leader, String itemName, File imageFile) throws IOException {
        String boundary = "Boundary-" + System.currentTimeMillis();
        String LINE_FEED = "\r\n";

        // Open connection
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream outputStream = connection.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)) {

            // Add player names as form field
            writer.append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"player_names\"").append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED).append(LINE_FEED);
            writer.append(String.join(",", partyList)).append(LINE_FEED);
            writer.flush();

            // Add leader as form field
            writer.append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"leader\"").append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED).append(LINE_FEED);
            writer.append(leader).append(LINE_FEED);
            writer.flush();

            // Add item name as form field
            writer.append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"item_name\"").append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED).append(LINE_FEED);
            writer.append(itemName).append(LINE_FEED); // Assuming itemName is a String
            writer.flush();

            // Add image file as form field
            writer.append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"screenshot\"; filename=\"" + imageFile.getName() + "\"").append(LINE_FEED);
            writer.append("Content-Type: " + Files.probeContentType(imageFile.toPath())).append(LINE_FEED);
            writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED).append(LINE_FEED);

            writer.flush();

            // Write file content
            try (FileInputStream inputStream = new FileInputStream(imageFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
            writer.append(LINE_FEED).flush();

            // End of multipart
            writer.append("--").append(boundary).append("--").append(LINE_FEED);
            writer.flush();
        }

        // Get response
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                System.out.println("Response: " + response.toString());
            }
        } else {
            System.out.println("Request failed with response code: " + responseCode);
        }
        connection.disconnect();
    }

    public static void sendPartyUpdate(String urlString, String confirmedNames, String leader, File imageFile) throws IOException {
        String boundary = "Boundary-" + System.currentTimeMillis();
        String LINE_FEED = "\r\n";

        // Open connection
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream outputStream = connection.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)) {

            // Add player names as form field
            writer.append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"confirmedNames\"").append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED).append(LINE_FEED);
            writer.append(confirmedNames).append(LINE_FEED);
            writer.flush();

            // Add leader as form field
            writer.append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"leader\"").append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED).append(LINE_FEED);
            writer.append(leader).append(LINE_FEED);
            writer.flush();

            // Add image file as form field
            writer.append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"screenshot\"; filename=\"" + imageFile.getName() + "\"").append(LINE_FEED);
            writer.append("Content-Type: " + Files.probeContentType(imageFile.toPath())).append(LINE_FEED);
            writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED).append(LINE_FEED);

            writer.flush();

            // Write file content
            try (FileInputStream inputStream = new FileInputStream(imageFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
            writer.append(LINE_FEED).flush();

            // End of multipart
            writer.append("--").append(boundary).append("--").append(LINE_FEED);
            writer.flush();
        }

        // Get response
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                System.out.println("Response: " + response.toString());
            }
        } else {
            System.out.println("Request failed with response code: " + responseCode);
        }
        connection.disconnect();
    }

}
