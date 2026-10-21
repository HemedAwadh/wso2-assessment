package org.example.customerservicespringboot.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.customerservicespringboot.models.MockUserData;
import org.example.customerservicespringboot.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;



import java.time.Duration;
import java.util.*;

@RestController
@Slf4j
public class CustomerMockController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerMockController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SLOW_ID = "999";
    private static final String ERROR_ID = "500";

    private final Duration slowDelay;

    public CustomerMockController(@Value("${mock.slow-delay:6s}") Duration slowDelay) {
        this.slowDelay = slowDelay;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        // Not logged, as it is probed frequently by healthchecks
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers(HttpServletRequest request, HttpServletResponse response) {
        logRequest(request);
        setMockHeaders(response);
        return ResponseEntity.ok(MockUserData.USERS);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        logRequest(request);

        // 1. Simulate Internal Server Error
        if (ERROR_ID.equals(id)) {
            setMockHeaders(response);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal Server Error"));
        }

        // 2. Simulate Slow Response (Timeout testing)
        if (SLOW_ID.equals(id)) {
            try {
                Thread.sleep(slowDelay.toMillis());
                setMockHeaders(response);
                return ResponseEntity.ok(MockUserData.USERS.get(0));
            } catch (InterruptedException e) {
                // Client disconnected or thread interrupted
                Thread.currentThread().interrupt();
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build();
            }
        }

        // 3. Normal Lookup
        Optional<User> foundUser = MockUserData.USERS.stream()
                .filter(u -> u.id().toString().equals(id))
                .findFirst();

        setMockHeaders(response);

        if (foundUser.isPresent()) {
            return ResponseEntity.ok(foundUser.get());
        } else {
            // JSONPlaceholder returns 404 with an empty object for unknown ids
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyMap());
        }
    }

    private void setMockHeaders(HttpServletResponse response) {
        response.setHeader("Content-Type", "application/json; charset=utf-8");
        // Internal headers a real backend might leak; the gateway must strip them
        response.setHeader("X-Powered-By", "Express");
        response.setHeader("X-Backend-Node", "customer-svc-02");
        response.setHeader("Server", "mock-customer-service/1.0");
    }

    private void logRequest(HttpServletRequest request) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("method", request.getMethod());
        logData.put("path", request.getRequestURI());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", request.getServerName() +
                (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : ""));

        // Collect and lowercase header names to match Go behavior
        Collections.list(request.getHeaderNames()).forEach(name ->
                headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name))
        );

        logData.put("headers", headers);

        try {
            // Serialize to a single JSON line, exactly like the Go version
            String logLine = objectMapper.writeValueAsString(logData);
            logger.info(logLine);
        } catch (Exception e) {
            logger.error("Failed to serialize request log", e);
        }
    }
}