package org.example.customerservicespringboot.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Address(
        String street,
        String suite,
        String city,
        @JsonProperty("zipcode") String zipcode,
        Geo geo
) {}
