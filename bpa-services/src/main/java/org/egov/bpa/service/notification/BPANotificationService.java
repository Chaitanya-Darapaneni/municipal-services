package org.egov.bpa.service.notification;

import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.egov.bpa.config.BPAConfiguration;
import org.egov.bpa.repository.ServiceRequestRepository;
import org.egov.bpa.service.BPAService;
import org.egov.bpa.service.UserService;
import org.egov.bpa.util.BPAConstants;
import org.egov.bpa.util.NotificationUtil;
import org.egov.bpa.web.models.*;
import org.egov.bpa.web.models.user.UserDetailResponse;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.jayway.jsonpath.JsonPath;

import org.egov.bpa.web.models.BPASearchCriteria;
@Slf4j
@Service
public class BPANotificationService {

    private BPAConfiguration config;

    private ServiceRequestRepository serviceRequestRepository;

    private NotificationUtil util;
    
    @Autowired
    private UserService userService;
 

    @Autowired
    public BPANotificationService(BPAConfiguration config, ServiceRequestRepository serviceRequestRepository, NotificationUtil util) {
        this.config = config;
        this.serviceRequestRepository = serviceRequestRepository;
        this.util = util;
    }

	
	
	public void process(BPARequest bpaRequest) {
		List<SMSRequest> smsRequests = new LinkedList<>();
		if (null != config.getIsSMSEnabled()) {
			if (config.getIsSMSEnabled()) {
				enrichSMSRequest(bpaRequest, smsRequests);
				if (!CollectionUtils.isEmpty(smsRequests))
					util.sendSMS(smsRequests, config.getIsSMSEnabled());
			}
		}
		if (null != config.getIsUserEventsNotificationEnabled()) {
			if (config.getIsUserEventsNotificationEnabled()) {
				EventRequest eventRequest = getEvents(bpaRequest);
				if (null != eventRequest)
					util.sendEventNotification(eventRequest);
			}
		}
	}



	public EventRequest getEvents(BPARequest bpaRequest) {
		
		List<Event> events = new ArrayList<>();
        String tenantId = bpaRequest.getBPA().getTenantId();
        String localizationMessages = util.getLocalizationMessages(tenantId,bpaRequest.getRequestInfo());  //--need localization service changes.
        String message = util.getEventsCustomizedMsg(bpaRequest.getRequestInfo(), bpaRequest.getBPA(), localizationMessages);  //--need localization service changes.
		BPA bpaApplication = bpaRequest.getBPA();
		Map<String, String> mobileNumberToOwner = getUserList(bpaRequest);


            List<SMSRequest> smsRequests = util.createSMSRequest(message,mobileNumberToOwner);
        	Set<String> mobileNumbers = smsRequests.stream().map(SMSRequest :: getMobileNumber).collect(Collectors.toSet());
        	Map<String, String> mapOfPhnoAndUUIDs = fetchUserUUIDs(mobileNumbers, bpaRequest.getRequestInfo(), bpaRequest.getBPA().getTenantId());
    		
            Map<String,String > mobileNumberToMsg = smsRequests.stream().collect(Collectors.toMap(SMSRequest::getMobileNumber, SMSRequest::getMessage));		
            for(String mobile: mobileNumbers) {
    			if(null == mapOfPhnoAndUUIDs.get(mobile) || null == mobileNumberToMsg.get(mobile)) {
    				log.error("No UUID/SMS for mobile {} skipping event", mobile);
    				continue;
    			}
				List<String> toUsers = new ArrayList<>();
				toUsers.add(mapOfPhnoAndUUIDs.get(mobile));
				Recepient recepient = Recepient.builder().toUsers(toUsers).toRoles(null).build();
				List<String> payTriggerList = Arrays.asList(config.getPayTriggers().split("[,]"));
				Action action = null;
				if(payTriggerList.contains(bpaApplication.getStatus())) {
					List<ActionItem> items = new ArrayList<>();
					String actionLink = config.getPayLink().replace("$mobile", mobile)
							.replace("$applicationNo", bpaApplication.getApplicationNo())
							.replace("$tenantId", bpaApplication.getTenantId());
					actionLink = config.getUiAppHost() + actionLink;
					ActionItem item = ActionItem.builder().actionUrl(actionLink).code(config.getPayCode()).build();
					items.add(item);
					action = Action.builder().actionUrls(items).build();
				}


				events.add(Event.builder().tenantId(bpaApplication.getTenantId()).description(mobileNumberToMsg.get(mobile))
						.eventType(BPAConstants.USREVENTS_EVENT_TYPE).name(BPAConstants.USREVENTS_EVENT_NAME)
						.postedBy(BPAConstants.USREVENTS_EVENT_POSTEDBY).source(Source.WEBAPP).recepient(recepient)
						.eventDetails(null).actions(action).build());
			}

        if(!CollectionUtils.isEmpty(events)) {
    		return EventRequest.builder().requestInfo(bpaRequest.getRequestInfo()).events(events).build();
        }else {
        	return null;
        }
		
	}



	private Map<String, String> fetchUserUUIDs(Set<String> mobileNumbers,
			RequestInfo requestInfo, String tenantId) {

    	Map<String, String> mapOfPhnoAndUUIDs = new HashMap<>();
    	StringBuilder uri = new StringBuilder();
    	uri.append(config.getUserHost()).append(config.getUserSearchEndpoint());
    	Map<String, Object> userSearchRequest = new HashMap<>();
    	userSearchRequest.put("RequestInfo", requestInfo);
		userSearchRequest.put("tenantId", tenantId);
		userSearchRequest.put("userType", "CITIZEN");
    	for(String mobileNo: mobileNumbers) {
    		userSearchRequest.put("mobileNumber", mobileNo);
    		try {
    			Object user = serviceRequestRepository.fetchResult(uri, userSearchRequest);
    			if(null != user) {
    				String uuid = JsonPath.read(user, "$.user[0].uuid");
    				mapOfPhnoAndUUIDs.put(mobileNo, uuid);
    			}else {
        			log.error("Service returned null while fetching user for username - "+mobileNo);
    			}
    		}catch(Exception e) {
    			log.error("Exception while fetching user for username - "+mobileNo);
    			log.error("Exception trace: ",e);
    			continue;
    		}
    	}
    	return mapOfPhnoAndUUIDs;
	}



	private void enrichSMSRequest(BPARequest bpaRequest,List<SMSRequest> smsRequests) {
		
		String tenantId = bpaRequest.getBPA().getTenantId();
		String localizationMessages = util.getLocalizationMessages(tenantId,bpaRequest.getRequestInfo()); 
		String message = util.getCustomizedMsg(bpaRequest.getRequestInfo(),bpaRequest.getBPA(),localizationMessages);
		Map<String, String> mobileNumberToOwner = getUserList(bpaRequest);
		smsRequests.addAll(util.createSMSRequest(message,mobileNumberToOwner));
	}

	private Map<String, String> getUserList (BPARequest bpaRequest){
		Map<String,String> mobileNumberToOwner = new HashMap<>();
		String tenantId = bpaRequest.getBPA().getTenantId();

		String stakeUUID= bpaRequest.getBPA().getAuditDetails().getCreatedBy();
		List<String> ownerId = new ArrayList<String>();
		ownerId.add(stakeUUID);
		BPASearchCriteria bpaSearchCriteria = new BPASearchCriteria();
		bpaSearchCriteria.setOwnerIds(ownerId);
		bpaSearchCriteria.setTenantId(tenantId);
		UserDetailResponse userDetailResponse = userService.getUser(bpaSearchCriteria, bpaRequest.getRequestInfo());
		mobileNumberToOwner.put(userDetailResponse.getUser().get(0).getMobileNumber(), userDetailResponse.getUser().get(0).getName());
		if(!bpaRequest.getBPA().getAction().equals("SEND_TO_ARCHITECT")  && (!bpaRequest.getBPA().getStatus().equals( "INPROGRESS") || !bpaRequest.getBPA().getAction().equals("APPROVE") ) ){
		if(bpaRequest.getBPA().getOwners().get(0).getId() == null){
			BPASearchCriteria bpaOwnerSearchCriteria = new BPASearchCriteria();
			List<String> ownerIds = new ArrayList<String>();
			ownerIds.add(bpaRequest.getBPA().getOwners().get(0).getUuid());
			bpaOwnerSearchCriteria.setOwnerIds(ownerIds);
			bpaOwnerSearchCriteria.setTenantId(tenantId);
			UserDetailResponse ownerDetailResponse	 = userService.getUser(bpaOwnerSearchCriteria, bpaRequest.getRequestInfo());
			mobileNumberToOwner.put(ownerDetailResponse.getUser().get(0).getMobileNumber(), ownerDetailResponse.getUser().get(0).getName());
		}else{
		bpaRequest.getBPA().getOwners().forEach(owner -> {
			if(owner.isPrimaryOwner()){
				if (owner.getMobileNumber() != null) {
					mobileNumberToOwner.put(
							owner.getMobileNumber(),
							owner.getName());
				}
			}
		});
		}
		}
		return mobileNumberToOwner;
    }
}

