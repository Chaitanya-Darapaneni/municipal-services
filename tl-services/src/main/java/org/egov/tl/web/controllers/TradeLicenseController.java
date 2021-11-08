package org.egov.tl.web.controllers;



import org.egov.tl.service.PaymentUpdateService;
import org.egov.tl.service.TradeLicenseService;
import org.egov.tl.service.notification.PaymentNotificationService;
import org.egov.tl.util.ResponseInfoFactory;
import org.egov.tl.web.models.*;
import org.egov.tl.web.models.calculation.Calculation;
import org.egov.tl.web.models.calculation.CalculationRes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

import javax.validation.constraints.*;
import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;

@RestController
    @RequestMapping("/v1")
    public class TradeLicenseController {

        private final ObjectMapper objectMapper;

        private final HttpServletRequest request;

        private final TradeLicenseService tradeLicenseService;

        private final ResponseInfoFactory responseInfoFactory;

    @Autowired
    public TradeLicenseController(ObjectMapper objectMapper, HttpServletRequest request,
                                  TradeLicenseService tradeLicenseService, ResponseInfoFactory responseInfoFactory) {
        this.objectMapper = objectMapper;
        this.request = request;
        this.tradeLicenseService = tradeLicenseService;
        this.responseInfoFactory = responseInfoFactory;
    }


    @PostMapping({"/{servicename}/_create", "/_create"})
    public ResponseEntity<TradeLicenseResponse> create(@Valid @RequestBody TradeLicenseRequest tradeLicenseRequest,
                                                       @PathVariable(required = false) String servicename) {
        List<TradeLicense> licenses = tradeLicenseService.create(tradeLicenseRequest, servicename);
        TradeLicenseResponse response = TradeLicenseResponse.builder().licenses(licenses).responseInfo(
                responseInfoFactory.createResponseInfoFromRequestInfo(tradeLicenseRequest.getRequestInfo(), true))
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    @PostMapping({"/{servicename}/_estimate", "/_estimate"})
    public ResponseEntity<CalculationRes> estimate(@Valid @RequestBody TradeLicenseRequest tradeLicenseRequest,
                                                       @PathVariable(required = false) String servicename) {
    	CalculationRes calculationResponse = tradeLicenseService.estimate(tradeLicenseRequest, servicename);
    	
    	CalculationRes response = CalculationRes.builder().calculations(calculationResponse.getCalculations())
				.responseInfo(
						responseInfoFactory.createResponseInfoFromRequestInfo(tradeLicenseRequest.getRequestInfo(), true))
				.build();
    	
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = {"/{servicename}/_search", "/_search"}, method = RequestMethod.POST)
    public ResponseEntity<TradeLicenseResponse> search(@Valid @RequestBody RequestInfoWrapper requestInfoWrapper,
                                                       @Valid @ModelAttribute TradeLicenseSearchCriteria criteria,
                                                       @PathVariable(required = false) String servicename
            , @RequestHeader HttpHeaders headers) {
        List<TradeLicense> licenses = tradeLicenseService.search(criteria, requestInfoWrapper.getRequestInfo(), servicename, headers);

        TradeLicenseResponse response = TradeLicenseResponse.builder().licenses(licenses).responseInfo(
                responseInfoFactory.createResponseInfoFromRequestInfo(requestInfoWrapper.getRequestInfo(), true))
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = {"/{servicename}/_update", "/_update"}, method = RequestMethod.POST)
    public ResponseEntity<TradeLicenseResponse> update(@Valid @RequestBody TradeLicenseRequest tradeLicenseRequest,
                                                       @PathVariable(required = false) String servicename) {
        List<TradeLicense> licenses = tradeLicenseService.update(tradeLicenseRequest, servicename);

        TradeLicenseResponse response = TradeLicenseResponse.builder().licenses(licenses).responseInfo(
                responseInfoFactory.createResponseInfoFromRequestInfo(tradeLicenseRequest.getRequestInfo(), true))
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = {"/{servicename}/{jobname}/_batch", "/_batch"}, method = RequestMethod.POST)
    public ResponseEntity sendReminderSMS(@Valid @RequestBody RequestInfoWrapper requestInfoWrapper,
                                          @PathVariable(required = false) String servicename,
                                          @PathVariable(required = true) String jobname) {

        tradeLicenseService.runJob(servicename, jobname, requestInfoWrapper.getRequestInfo());

        return new ResponseEntity(HttpStatus.ACCEPTED);
    }

    @RequestMapping(value="/_plainsearch", method = RequestMethod.POST)
    public ResponseEntity<TradeLicenseResponse> plainsearch(@Valid @RequestBody RequestInfoWrapper requestInfoWrapper,
                                                            @Valid @ModelAttribute TradeLicenseSearchCriteria criteria){

        List<TradeLicense> licenses = tradeLicenseService.plainSearch(criteria,requestInfoWrapper.getRequestInfo());

        TradeLicenseResponse response = TradeLicenseResponse.builder().licenses(licenses).responseInfo(
                responseInfoFactory.createResponseInfoFromRequestInfo(requestInfoWrapper.getRequestInfo(), true))
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = {"/{servicename}/_updatedscdetails", "/_updatedscdetails"}, method = RequestMethod.POST)
    public ResponseEntity<TradeLicenseResponse> updateDscDetails(@Valid @RequestBody TradeLicenseRequest tradeLicenseRequest,
                                                       @PathVariable(required = false) String servicename) {
        List<TradeLicense> licenses = tradeLicenseService.updateDscDetails(tradeLicenseRequest, servicename);

        TradeLicenseResponse response = TradeLicenseResponse.builder().licenses(licenses).responseInfo(
                responseInfoFactory.createResponseInfoFromRequestInfo(tradeLicenseRequest.getRequestInfo(), true))
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    
    @RequestMapping(value = {"/{servicename}/_searchdscdetails", "/_searchdscdetails"}, method = RequestMethod.POST)
    public ResponseEntity<DigitalSignCertificateResponse> searchDscDetails(@Valid @RequestBody RequestInfoWrapper requestInfoWrapper,
                                                       @Valid @ModelAttribute TradeLicenseSearchCriteria criteria,
                                                       @PathVariable(required = false) String servicename
            , @RequestHeader HttpHeaders headers) {
        List<DscDetails> dscDetails = tradeLicenseService.searchDscDetails(criteria,requestInfoWrapper.getRequestInfo());

        DigitalSignCertificateResponse response = DigitalSignCertificateResponse.builder().dscDetails(dscDetails).responseInfo(
                responseInfoFactory.createResponseInfoFromRequestInfo(requestInfoWrapper.getRequestInfo(), true))
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    
    }
    
    
}
