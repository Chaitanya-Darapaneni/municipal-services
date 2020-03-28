package org.egov.tl.util;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tl.config.TLConfiguration;
import org.egov.tl.producer.Producer;
import org.egov.tl.repository.ServiceRequestRepository;
import org.egov.tl.web.models.*;
import org.egov.tracer.model.CustomException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.egov.tl.util.TLConstants.*;

@Component
@Slf4j
public class NotificationUtil {

	private TLConfiguration config;

	private ServiceRequestRepository serviceRequestRepository;

	private Producer producer;

	@Autowired
	public NotificationUtil(TLConfiguration config, ServiceRequestRepository serviceRequestRepository,
			Producer producer) {
		this.config = config;
		this.serviceRequestRepository = serviceRequestRepository;
		this.producer = producer;
	}

	final String receiptNumberKey = "receiptNumber";

	final String amountPaidKey = "amountPaid";

	/**
	 * Creates customized message based on tradelicense
	 * 
	 * @param license
	 *            The tradeLicense for which message is to be sent
	 * @param localizationMessage
	 *            The messages from localization
	 * @return customized message based on tradelicense
	 */
	public String getCustomizedMsg(RequestInfo requestInfo, TradeLicense license, String localizationMessage) {
		String message = null, messageTemplate;
		String ACTION_STATUS = license.getAction() + "_" + license.getStatus();
		switch (ACTION_STATUS) {

		/*case ACTION_STATUS_INITIATED:
			messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_INITIATED, localizationMessage);
			message = getInitiatedMsg(license, messageTemplate);
			break;*/

		case ACTION_STATUS_APPLIED:
			messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_APPLIED, localizationMessage);
			message = getAppliedMsg(license, messageTemplate,localizationMessage);
			break;

		/*
		 * case ACTION_STATUS_PAID : messageTemplate =
		 * getMessageTemplate(TLConstants.NOTIFICATION_PAID,localizationMessage);
		 * message = getApprovalPendingMsg(license,messageTemplate); break;
		 */

		case ACTION_STATUS_APPROVED:
			//BigDecimal amountToBePaid = getAmountToBePaid(requestInfo, license);
			messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_APPROVED, localizationMessage);
			message = getApprovedMsg(license, messageTemplate, localizationMessage);
			break;

		case ACTION_STATUS_REJECTED:
			messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_REJECTED, localizationMessage);
			message = getRejectedMsg(license, messageTemplate, localizationMessage);
			break;

		case ACTION_STATUS_FIELDINSPECTION:
			messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_FIELD_INSPECTION, localizationMessage);
			message = getFieldInspectionMsg(license, messageTemplate);
			break;

		case ACTION_SENDBACKTOCITIZEN_FIELDINSPECTION:
			messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_SENDBACK_CITIZEN, localizationMessage);
			message = getCitizenSendBack(license, messageTemplate);
			break;

		case ACTION_FORWARD_CITIZENACTIONREQUIRED:
			messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_FORWARD_CITIZEN, localizationMessage);
			message = getCitizenForward(license, messageTemplate);
			break;

		case ACTION_CANCEL_CANCELLED:
			messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_CANCELLED, localizationMessage);
			message = getCancelledMsg(license, messageTemplate);
			break;
		}

		return message;
	}

	/**
	 * Extracts message for the specific code
	 * 
	 * @param notificationCode
	 *            The code for which message is required
	 * @param localizationMessage
	 *            The localization messages
	 * @return message for the specific code
	 */
	private String getMessageTemplate(String notificationCode, String localizationMessage) {
		String path = "$..messages[?(@.code==\"{}\")].message";
		path = path.replace("{}", notificationCode);
		String message = null;
		try {
			Object messageObj = JsonPath.parse(localizationMessage).read(path);
			message = ((ArrayList<String>) messageObj).get(0);
		} catch (Exception e) {
			log.warn("Fetching from localization failed", e);
			log.info("Failed for localization code: "+notificationCode);
			return null;
		}
		return message;
	}

	/**
	 * Returns the uri for the localization call
	 * 
	 * @param tenantId
	 *            TenantId of the propertyRequest
	 * @return The uri for localization search call
	 */
	public StringBuilder getUri(String tenantId, RequestInfo requestInfo) {

		if (config.getIsLocalizationStateLevel())
			tenantId = tenantId.split("\\.")[0];

		String locale = NOTIFICATION_LOCALE;
		if (!StringUtils.isEmpty(requestInfo.getMsgId()) && requestInfo.getMsgId().split("|").length >= 2)
			locale = requestInfo.getMsgId().split("\\|")[1];

		StringBuilder uri = new StringBuilder();
		uri.append(config.getLocalizationHost()).append(config.getLocalizationContextPath())
				.append(config.getLocalizationSearchEndpoint()).append("?").append("locale=").append(locale)
				.append("&tenantId=").append(tenantId).append("&module=").append(TLConstants.MODULE)
				.append("&codes=").append(StringUtils.join(NOTIFICATION_CODES,','));

		return uri;
	}

	/**
	 * Returns the uri for the localization call
	 *
	 * @param tenantId
	 *            TenantId of the propertyRequest
	 * @return The uri for localization search call
	 */
	public StringBuilder getUri(String tenantId, RequestInfo requestInfo, List<String> additionalCodes) {

		if (config.getIsLocalizationStateLevel())
			tenantId = tenantId.split("\\.")[0];

		String locale = NOTIFICATION_LOCALE;
		if (!StringUtils.isEmpty(requestInfo.getMsgId()) && requestInfo.getMsgId().split("|").length >= 2)
			locale = requestInfo.getMsgId().split("\\|")[1];

		List<String> codes =  Stream.concat(NOTIFICATION_CODES.stream(), additionalCodes.stream()).collect(Collectors.toList());

		StringBuilder uri = new StringBuilder();
		uri.append(config.getLocalizationHost()).append(config.getLocalizationContextPath())
				.append(config.getLocalizationSearchEndpoint()).append("?").append("locale=").append(locale)
				.append("&tenantId=").append(tenantId).append("&module=").append(TLConstants.MODULE)
				.append("&codes=").append(StringUtils.join(codes,','));

		return uri;
	}

	/**
	 * Fetches messages from localization service
	 * 
	 * @param tenantId
	 *            tenantId of the tradeLicense
	 * @param requestInfo
	 *            The requestInfo of the request
	 * @return Localization messages for the module
	 */
	public String getLocalizationMessages(String tenantId, RequestInfo requestInfo) {
		LinkedHashMap responseMap = (LinkedHashMap) serviceRequestRepository.fetchResult(getUri(tenantId, requestInfo),
				requestInfo);
		String jsonString = new JSONObject(responseMap).toString();
		return jsonString;
	}

	/**
	 * Fetches messages from localization service
	 *
	 * @param tenantId
	 *            tenantId of the tradeLicense
	 * @param requestInfo
	 *            The requestInfo of the request
	 * @return Localization messages for the module
	 */
	public String getLocalizationMessages(String tenantId, RequestInfo requestInfo, List<String> additionalCodes) {
		LinkedHashMap responseMap = (LinkedHashMap) serviceRequestRepository.fetchResult(getUri(tenantId, requestInfo, additionalCodes),
				requestInfo);
		String jsonString = new JSONObject(responseMap).toString();
		return jsonString;
	}

	/**
	 * Creates customized message for initiate
	 * 
	 * @param license
	 *            tenantId of the tradeLicense
	 * @param message
	 *            Message from localization for initiate
	 * @return customized message for initiate
	 */
	private String getInitiatedMsg(TradeLicense license, String message) {
		// message = message.replace("<1>",license.);
	//	message = message.replace("<2>", license.getTradeName());
		message = message.replace("<3>", license.getApplicationNumber());

		return message;
	}

	/**
	 * Creates customized message for apply
	 * 
	 * @param license
	 *            tenantId of the tradeLicense
	 * @param message
	 *            Message from localization for apply
	 * @return customized message for apply
	 */
	private String getAppliedMsg(TradeLicense license, String message, String localizationMessage) {
		// message = message.replace("<1>",);
	//	message = message.replace("<2>", license.getTradeName());
		message = message.replace(NOTIF_APPLICATION_NUMBER_KEY, license.getApplicationNumber());
		String passCategoryName = getName(license.getTradeLicenseDetail().getTradeUnits().get(0).getTradeType(),localizationMessage);
		message = message.replace(NOTIF_PASS_CATEGORY_KEY, passCategoryName);

		return message;
	}

	/**
	 * Creates customized message for submitted
	 * 
	 * @param license
	 *            tenantId of the tradeLicense
	 * @param message
	 *            Message from localization for submitted
	 * @return customized message for submitted
	 */
	private String getApprovalPendingMsg(TradeLicense license, String message) {
		// message = message.replace("<1>",);
		message = message.replace("<2>", license.getTradeName());

		return message;
	}

	/**
	 * Creates customized message for approved
	 * 
	 * @param license
	 *            tenantId of the tradeLicense
	 * @param message
	 *            Message from localization for approved
	 * @return customized message for approved
	 */
	private String getApprovedMsg(TradeLicense license, String message, String localizationMessage) {

		String passCategoryName = getName(license.getTradeLicenseDetail().getTradeUnits().get(0).getTradeType(),localizationMessage);
		message = message.replace(NOTIF_PASS_CATEGORY_KEY, passCategoryName);

		String fromState = getName(license.getTradeLicenseDetail().getAdditionalDetail().get(ADDITIONALDETAILS_KEY_FROMSTATE).asText(),localizationMessage);
		message = message.replace(NOTIF_FROM_STATE_KEY, fromState);

		String fromDistrict = getName(license.getTradeLicenseDetail().getAdditionalDetail().get(ADDITIONALDETAILS_KEY_FROMDISTRICT).asText(),localizationMessage);
		message = message.replace(NOTIF_FROM_DISTRICT_KEY, fromDistrict);

		String toDistrict = getName(license.getTradeLicenseDetail().getAdditionalDetail().get(ADDITIONALDETAILS_KEY_TODISTRICT).asText(),localizationMessage);
		message = message.replace(NOTIF_TO_DISTRICT_KEY, toDistrict);

		String issueDate = new SimpleDateFormat("dd/MM/yyyy").format(license.getIssuedDate());
		message = message.replace(NOTIF_ISSUE_DATE_KEY, issueDate);

		message = message.replace(NOTIF_TRADE_LICENSENUMBER_KEY, license.getLicenseNumber());
		String url = getPDFURL(license);
		message = message.replace(NOTIF_PDF_LINK_KEY, url);
		return message;
	}

	/**
	 * Creates customized message for rejected
	 * 
	 * @param license
	 *            tenantId of the tradeLicense
	 * @param message
	 *            Message from localization for rejected
	 * @return customized message for rejected
	 */
	private String getRejectedMsg(TradeLicense license, String message, String localizationMessage) {
		String passCategoryName = getName(license.getTradeLicenseDetail().getTradeUnits().get(0).getTradeType(),localizationMessage);
		message = message.replace(NOTIF_PASS_CATEGORY_KEY, passCategoryName);
		message = message.replace(NOTIF_HOST_LINK_KEY,config.getHostLink());

		return message;
	}

	/**
	 * Creates customized message for rejected
	 * 
	 * @param license
	 *            tenantId of the tradeLicense
	 * @param message
	 *            Message from localization for rejected
	 * @return customized message for rejected
	 */
	private String getFieldInspectionMsg(TradeLicense license, String message) {
		message = message.replace("<2>", license.getApplicationNumber());
		return message;
	}

	/**
	 * Creates customized message for citizen sendback
	 * 
	 * @param license
	 *            tenantId of the tradeLicense
	 * @param message
	 *            Message from localization for cancelled
	 * @return customized message for cancelled
	 */
	private String getCitizenSendBack(TradeLicense license, String message) {
		message = message.replace("<2>", license.getApplicationNumber());
		message = message.replace("<3>", license.getTradeName());

		return message;
	}

	/**
	 * Creates customized message for citizen forward
	 *
	 * @param license
	 *            tenantId of the tradeLicense
	 * @param message
	 *            Message from localization for cancelled
	 * @return customized message for cancelled
	 */
	private String getCitizenForward(TradeLicense license, String message) {
		message = message.replace("<2>", license.getApplicationNumber());
		message = message.replace("<3>", license.getTradeName());

		return message;
	}

	/**
	 * Creates customized message for cancelled
	 *
	 * @param license
	 *            tenantId of the tradeLicense
	 * @param message
	 *            Message from localization for cancelled
	 * @return customized message for cancelled
	 */
	private String getCancelledMsg(TradeLicense license, String message) {
		message = message.replace("<2>", license.getTradeName());
		message = message.replace("<3>", license.getLicenseNumber());

		return message;
	}

	/**
	 * Creates message for completed payment for owners
	 * 
	 * @param valMap
	 *            The map containing required values from receipt
	 * @param localizationMessages
	 *            Message from localization
	 * @return message for completed payment for owners
	 */
	public String getOwnerPaymentMsg(TradeLicense license, Map<String, String> valMap, String localizationMessages) {
		String messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_PAYMENT_OWNER, localizationMessages);
		messageTemplate = messageTemplate.replace("<2>", valMap.get(amountPaidKey));
		messageTemplate = messageTemplate.replace("<3>", license.getTradeName());
		messageTemplate = messageTemplate.replace("<4>", valMap.get(receiptNumberKey));
		return messageTemplate;
	}

	/**
	 * Creates message for completed payment for payer
	 * 
	 * @param valMap
	 *            The map containing required values from receipt
	 * @param localizationMessages
	 *            Message from localization
	 * @return message for completed payment for payer
	 */
	public String getPayerPaymentMsg(TradeLicense license, Map<String, String> valMap, String localizationMessages) {
		String messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_PAYMENT_PAYER, localizationMessages);
		messageTemplate = messageTemplate.replace("<2>", valMap.get(amountPaidKey));
		messageTemplate = messageTemplate.replace("<3>", license.getTradeName());
		messageTemplate = messageTemplate.replace("<4>", valMap.get(receiptNumberKey));
		return messageTemplate;
	}


	/**
	 *
	 * @param license
	 * @param localizationMessages
	 * @return
	 */
	public String getReminderMsg(TradeLicense license, String localizationMessages) {

		String messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_TL_REMINDER, localizationMessages);
		String expiryDate = new SimpleDateFormat("dd/MM/yyyy").format(license.getValidTo());
		messageTemplate = messageTemplate.replace(NOTIF_TRADE_NAME_KEY, license.getTradeName());
		messageTemplate = messageTemplate.replace(NOTIF_EXPIRY_DATE_KEY, expiryDate);
		messageTemplate = messageTemplate.replace(NOTIF_TRADE_LICENSENUMBER_KEY, license.getLicenseNumber());
		return messageTemplate;
	}


	/**
	 * Send the SMSRequest on the SMSNotification kafka topic
	 * 
	 * @param smsRequestList
	 *            The list of SMSRequest to be sent
	 */
	public void sendSMS(List<SMSRequest> smsRequestList, boolean isSMSEnabled) {
		if (isSMSEnabled) {
			if (CollectionUtils.isEmpty(smsRequestList))
				log.info("Messages from localization couldn't be fetched!");
			for (SMSRequest smsRequest : smsRequestList) {
				producer.push(config.getSmsNotifTopic(), smsRequest);
				log.info("MobileNumber: " + smsRequest.getMobileNumber() + " Messages: " + smsRequest.getMessage());
			}
		}
	}

	/**
	 * Fetches the amount to be paid from getBill API
	 * 
	 * @param requestInfo
	 *            The RequestInfo of the request
	 * @param license
	 *            The TradeLicense object for which
	 * @return
	 */
	private BigDecimal getAmountToBePaid(RequestInfo requestInfo, TradeLicense license) {

		LinkedHashMap responseMap = (LinkedHashMap) serviceRequestRepository.fetchResult(getBillUri(license),
				new RequestInfoWrapper(requestInfo));
		String jsonString = new JSONObject(responseMap).toString();

		BigDecimal amountToBePaid = null;
		try {
			Object obj = JsonPath.parse(jsonString).read(BILL_AMOUNT_JSONPATH);
			amountToBePaid = new BigDecimal(obj.toString());
		} catch (Exception e) {
			throw new CustomException("PARSING ERROR",
					"Failed to parse the response using jsonPath: " + BILL_AMOUNT_JSONPATH);
		}
		return amountToBePaid;
	}

	/**
	 * Creates the uri for getBill by adding query params from the license
	 * 
	 * @param license
	 *            The TradeLicense for which getBill has to be called
	 * @return The uri for the getBill
	 */
	private StringBuilder getBillUri(TradeLicense license) {
		StringBuilder builder = new StringBuilder(config.getBillingHost());
		builder.append(config.getFetchBillEndpoint());
		builder.append("?tenantId=");
		builder.append(license.getTenantId());
		builder.append("&consumerCode=");
		builder.append(license.getApplicationNumber());
		builder.append("&businessService=");
		builder.append(TRADE_LICENSE_MODULE_CODE);
		return builder;
	}

	/**
	 * Creates sms request for the each owners
	 * 
	 * @param message
	 *            The message for the specific tradeLicense
	 * @param mobileNumberToOwnerName
	 *            Map of mobileNumber to OwnerName
	 * @return List of SMSRequest
	 */
	public List<SMSRequest> createSMSRequest(String message, Map<String, String> mobileNumberToOwnerName) {
		List<SMSRequest> smsRequest = new LinkedList<>();
		for (Map.Entry<String, String> entryset : mobileNumberToOwnerName.entrySet()) {
			String customizedMsg = message.replace("<1>", entryset.getValue());
			customizedMsg = customizedMsg.replace(NOTIF_OWNER_NAME_KEY, entryset.getValue());
			smsRequest.add(new SMSRequest(entryset.getKey(), customizedMsg));
		}
		return smsRequest;
	}

	public String getCustomizedMsg(Difference diff, TradeLicense license, String localizationMessage) {
		String message = null, messageTemplate;
		// StringBuilder finalMessage = new StringBuilder();

		/*
		 * if(!CollectionUtils.isEmpty(diff.getFieldsChanged())){ messageTemplate =
		 * getMessageTemplate(TLConstants.NOTIFICATION_FIELD_CHANGED,localizationMessage
		 * ); message = getEditMsg(license,diff.getFieldsChanged(),messageTemplate);
		 * finalMessage.append(message); }
		 * 
		 * if(!CollectionUtils.isEmpty(diff.getClassesAdded())){ messageTemplate =
		 * getMessageTemplate(TLConstants.NOTIFICATION_OBJECT_ADDED,localizationMessage)
		 * ; message = getEditMsg(license,diff.getClassesAdded(),messageTemplate);
		 * finalMessage.append(message); }
		 * 
		 * if(!CollectionUtils.isEmpty(diff.getClassesRemoved())){ messageTemplate =
		 * getMessageTemplate(TLConstants.NOTIFICATION_OBJECT_REMOVED,
		 * localizationMessage); message =
		 * getEditMsg(license,diff.getClassesRemoved(),messageTemplate);
		 * finalMessage.append(message); }
		 */
		String applicationType = String.valueOf(license.getApplicationType());
		if(applicationType.equals(APPLICATION_TYPE_RENEWAL)){
			if (!CollectionUtils.isEmpty(diff.getFieldsChanged()) || !CollectionUtils.isEmpty(diff.getClassesAdded())
					|| !CollectionUtils.isEmpty(diff.getClassesRemoved())) {
				messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_OBJECT_RENEW_MODIFIED, localizationMessage);
				if (messageTemplate == null)
					messageTemplate = DEFAULT_OBJECT_RENEWAL_MODIFIED_MSG;
				message = getEditMsg(license, messageTemplate);
			}

		}
		else{
			if (!CollectionUtils.isEmpty(diff.getFieldsChanged()) || !CollectionUtils.isEmpty(diff.getClassesAdded())
					|| !CollectionUtils.isEmpty(diff.getClassesRemoved())) {
				messageTemplate = getMessageTemplate(TLConstants.NOTIFICATION_OBJECT_MODIFIED, localizationMessage);
				if (messageTemplate == null)
					messageTemplate = DEFAULT_OBJECT_MODIFIED_MSG;
				message = getEditMsg(license, messageTemplate);
			}
		}



		return message;
	}

	/**
	 * Creates customized message for field chnaged
	 * 
	 * @param message
	 *            Message from localization for field change
	 * @return customized message for field change
	 */
	private String getEditMsg(TradeLicense license, List<String> list, String message) {
		message = message.replace("<APPLICATION_NUMBER>", license.getApplicationNumber());
		message = message.replace("<FIELDS>", StringUtils.join(list, ","));
		return message;
	}

	private String getEditMsg(TradeLicense license, String message) {
		message = message.replace("<APPLICATION_NUMBER>", license.getApplicationNumber());
		return message;
	}

	/**
	 * Pushes the event request to Kafka Queue.
	 * 
	 * @param request
	 */
	public void sendEventNotification(EventRequest request) {
		producer.push(config.getSaveUserEventsTopic(), request);
	}


	private String getPDFURL(TradeLicense license){
		String url = config.getCitizenPDFLink();
		url = url.replace(NOTIF_UUID_KEY,license.getId());
		url = url.replace(NOTIF_TENANTID_KEY,license.getTenantId());
		return url;
	}

	/**
	 * Returns localization for code if not found retirn code itself
	 * @param code
	 * @param localizationMessages
	 * @return
	 */
	private String getName(String code,String localizationMessages){

		String localizationCode = NOTIFICATION_TL_PREFIX+code;
		String name = getMessageTemplate(localizationCode, localizationMessages);

		if(name==null)
			return code;

		return name;

	}

	public List<String> getLocalizationCodes(TradeLicenseRequest request){

		List<TradeLicense> licenses = request.getLicenses();
		Set<String> codes = new HashSet<>();

		licenses.forEach(license -> {
			String passCategoryCode = NOTIFICATION_TL_PREFIX+license.getTradeLicenseDetail().getTradeUnits().get(0).getTradeType();
			String fromState = NOTIFICATION_TL_PREFIX+license.getTradeLicenseDetail().getAdditionalDetail().get(ADDITIONALDETAILS_KEY_FROMSTATE).asText();
			String fromDistrict = NOTIFICATION_TL_PREFIX+license.getTradeLicenseDetail().getAdditionalDetail().get(ADDITIONALDETAILS_KEY_FROMDISTRICT).asText();
			String toDistrict = NOTIFICATION_TL_PREFIX+license.getTradeLicenseDetail().getAdditionalDetail().get(ADDITIONALDETAILS_KEY_TODISTRICT).asText();

			codes.add(passCategoryCode);
			codes.add(fromState);
			codes.add(fromDistrict);
			codes.add(toDistrict);

		});

		return new LinkedList<>(codes);
	}

}
