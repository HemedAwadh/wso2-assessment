package org.example.loaneligibilitysoapspringboot.controller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.loaneligibilitysoapspringboot.util.SoapCalculatorOperations; // <-- ADDED IMPORT
import org.example.loaneligibilitysoapspringboot.util.SoapCalculatorOperations.SoapFaultException; // <-- ADDED IMPORT
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@Slf4j
public class SoapCalculatorMockController {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENVELOPE_OPEN = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
            "<soap:Body>";

    private static final String ENVELOPE_CLOSE = "</soap:Body></soap:Envelope>";

    // REMOVED: Inline OPERATIONS map (now handled by SoapCalculatorOperations)

    private static final List<String> OP_NAMES = new ArrayList<>(SoapCalculatorOperations.OPERATIONS.keySet());
    private static final Map<String, Pattern> OP_PATTERNS = new HashMap<>();
    private static final Pattern INT_A_PATTERN = Pattern.compile("<(?:\\w+:)?intA>([^<]*)</(?:\\w+:)?intA>");
    private static final Pattern INT_B_PATTERN = Pattern.compile("<(?:\\w+:)?intB>([^<]*)</(?:\\w+:)?intB>");

    static {
        for (String name : OP_NAMES) {
            OP_PATTERNS.put(name, Pattern.compile("<(?:\\w+:)?" + name + "[\\s>]"));
        }
        Collections.sort(OP_NAMES);
    }

    @Value("classpath:wsdl/loan-eligibility.wsdl")
    private Resource wsdlResource;

    private String wsdlContent;

    @jakarta.annotation.PostConstruct
    public void init() throws IOException {
        this.wsdlContent = StreamUtils.copyToString(wsdlResource.getInputStream(), StandardCharsets.UTF_8);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping(value = "/calculator.asmx", params = "wsdl")
    public void getWsdl(HttpServletResponse response) throws IOException {
        response.setContentType("text/xml; charset=utf-8");
        response.getWriter().write(wsdlContent);
    }

    @PostMapping("/calculator.asmx")
    public void handleSoapRequest(@RequestHeader(value = "SOAPAction", required = false) String soapAction,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        if (body.length() > 1024 * 1024) {
            writeFault(response, "soap:Client", "Server was unable to read request (exceeds 1MB limit).");
            return;
        }

        SoapRequest req = parseRequest(body);
        logRequest(soapAction, req);

        try {
            // 1. Look up operation from the utility class
            SoapCalculatorOperations.SoapOperation op = SoapCalculatorOperations.OPERATIONS.get(req.op);
            if (op == null) {
                throw SoapCalculatorOperations.clientFault("Server did not recognize the value of HTTP Header SOAPAction.");
            }

            // 2. Parse Int32s using the utility class (throws SoapFaultException on failure)
            long a = SoapCalculatorOperations.parseInt32(req.intA, "intA");
            long b = SoapCalculatorOperations.parseInt32(req.intB, "intB");

            // 3. Execute and return
            long result = op.apply(a, b);

            response.setContentType("text/xml; charset=utf-8");
            String responseXml = String.format("%s<%sResponse xmlns=\"http://tempuri.org/\"><%sResult>%d</%sResult></%sResponse>%s",
                    ENVELOPE_OPEN, req.op, req.op, result, req.op, req.op, ENVELOPE_CLOSE);
            response.getWriter().write(responseXml);

        } catch (SoapFaultException e) {
            // Catch the custom exception from the utility class
            writeFault(response, e.getCode(), e.getMessage());
        } catch (Exception e) {
            writeFault(response, "soap:Server", "An unexpected error occurred: " + e.getMessage());
        }
    }

    private SoapRequest parseRequest(String xml) {
        SoapRequest req = new SoapRequest();
        for (String name : OP_NAMES) {
            if (OP_PATTERNS.get(name).matcher(xml).find()) {
                req.op = name;
                break;
            }
        }
        req.intA = extractField(xml, INT_A_PATTERN);
        req.intB = extractField(xml, INT_B_PATTERN);
        return req;
    }

    private String extractField(String xml, Pattern pattern) {
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // REMOVED: Inline invoke() and parseInt32() methods (now handled by SoapCalculatorOperations)

    private void writeFault(HttpServletResponse response, String code, String message) throws IOException {
        String faultString = "System.Web.Services.Protocols.SoapException: " + message +
                "\n   at Calculator.Invoke() in C:\\inetpub\\calculator\\Calculator.asmx.cs:line 42";

        response.setContentType("text/xml; charset=utf-8");
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        String faultXml = String.format("%s<soap:Fault><faultcode>%s</faultcode><faultstring>%s</faultstring><detail /></soap:Fault>%s",
                ENVELOPE_OPEN, code, HtmlUtils.htmlEscape(faultString), ENVELOPE_CLOSE);
        response.getWriter().write(faultXml);
    }

    private void logRequest(String soapAction, SoapRequest req) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("soapAction", soapAction);
            entry.put("op", req.op);
            entry.put("intA", req.intA);
            entry.put("intB", req.intB);
            log.info(objectMapper.writeValueAsString(entry));
        } catch (Exception e) {
            log.error("Failed to serialize request log", e);
        }
    }

    private static class SoapRequest {
        String op;
        String intA;
        String intB;
    }
}