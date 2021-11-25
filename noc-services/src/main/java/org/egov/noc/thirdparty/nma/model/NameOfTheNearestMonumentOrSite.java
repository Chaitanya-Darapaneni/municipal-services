package org.egov.noc.thirdparty.nma.model;

import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Validated
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class NameOfTheNearestMonumentOrSite {
	@JsonProperty("MonumentName") 
    public String monumentName;
    @JsonProperty("State") 
    public String state;
    @JsonProperty("District") 
    public String district;
    @JsonProperty("Taluk") 
    public String taluk;
    @JsonProperty("Locality") 
    public String locality;
}