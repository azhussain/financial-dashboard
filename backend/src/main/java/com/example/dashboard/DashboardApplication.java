package com.example.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DashboardApplication {

    public static void main(String[] args) {
        // Yahoo Finance rejects requests without a browser User-Agent (HTTP 429).
        // The yahoofinance library uses HttpURLConnection, which picks up the
        // User-Agent from the http.agent system property.
        System.setProperty("http.agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        SpringApplication.run(DashboardApplication.class, args);
    }
}
