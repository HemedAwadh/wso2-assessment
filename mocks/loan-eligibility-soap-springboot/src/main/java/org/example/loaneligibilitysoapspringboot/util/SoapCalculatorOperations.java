package org.example.loaneligibilitysoapspringboot.util;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.regex.Pattern;

public final class SoapCalculatorOperations {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("^\\s*-?\\d+\\s*$");
    private static final long MIN_INT32 = Integer.MIN_VALUE; // -2147483648
    private static final long MAX_INT32 = Integer.MAX_VALUE; //  2147483647

    // Custom exception to carry SOAP fault details
    @Getter
    public static class SoapFaultException extends RuntimeException {
        private final String code;
        private final String message;

        public SoapFaultException(String code, String message) {
            super(code + ": " + message);
            this.code = code;
            this.message = message;
        }

    }

    public static SoapFaultException clientFault(String msg) {
        return new SoapFaultException("soap:Client", msg);
    }

    public static SoapFaultException serverFault(String msg) {
        return new SoapFaultException("soap:Server", msg);
    }

    public static long parseInt32(String raw, String name) {
        if (raw == null) {
            throw clientFault("Server was unable to read request. ---> Missing element " + name + ".");
        }
        if (!INTEGER_PATTERN.matcher(raw).matches()) {
            throw clientFault("Server was unable to read request. ---> Input string was not in a correct format.");
        }
        
        long n;
        try {
            n = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw clientFault("Server was unable to read request. ---> Input string was not in a correct format.");
        }

        if (n < MIN_INT32 || n > MAX_INT32) {
            throw clientFault("Server was unable to read request. ---> Value was either too large or too small for an Int32.");
        }
        return n;
    }

    private static long checkRange(long n) {
        if (n < MIN_INT32 || n > MAX_INT32) {
            throw serverFault("Server was unable to process request. ---> Arithmetic operation resulted in an overflow.");
        }
        return n;
    }

    @FunctionalInterface
    public interface SoapOperation {
        long apply(long a, long b);
    }

    // Operations supported by the mock, keyed by SOAP operation name.
    // TODO: Add "Add", "Subtract", or your actual WSDL operation names here.
    public static final Map<String, SoapOperation> OPERATIONS = Map.of(
            "Multiply", (a, b) -> checkRange(a * b),
            "Divide", (a, b) -> {
                if (b == 0) {
                    // Matches the Go code's explicit fault message for divide by zero
                    throw serverFault("Server was unable to process request. ---> Arithmetic operation resulted in an overflow.");
                }
                // BigDecimal with HALF_EVEN is the exact Java equivalent of Go's math.RoundToEven
                BigDecimal bdA = BigDecimal.valueOf(a);
                BigDecimal bdB = BigDecimal.valueOf(b);
                long result = bdA.divide(bdB, 0, RoundingMode.HALF_EVEN).longValue();
                return checkRange(result);
            }
    );

    private SoapCalculatorOperations() {} // Prevent instantiation
}