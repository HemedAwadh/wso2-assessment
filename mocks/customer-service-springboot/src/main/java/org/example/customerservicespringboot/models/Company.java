package org.example.customerservicespringboot.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Company(
        String name,
        @JsonProperty("catchPhrase") String catchPhrase,
        @JsonProperty("bs") String bs
) {}
