package za.org.grassroot.webapp.controller.rest.livewire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.group.GroupLocationFilter;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /api/location/list?latlng=30.5595,22.9375&radius=120.44&token=b9998dda-380e-4998-af5f-fa45bc071408
 */
@RestController
@RequestMapping(value = "/api/location", produces = MediaType.APPLICATION_JSON_VALUE)
public class LocationRestController {
	private static final Logger log = LoggerFactory.getLogger(LocationRestController.class);

	private final GeoLocationBroker geoLocationBroker;
	private final ObjectLocationBroker objectLocationBroker;

	@Autowired
	public LocationRestController(ObjectLocationBroker objectLocationBroker, GeoLocationBroker geoLocationBroker) {
		this.objectLocationBroker = objectLocationBroker;
		this.geoLocationBroker = geoLocationBroker;
	}

	@RequestMapping(value = "/list", method = RequestMethod.GET)
	public ResponseEntity<ResponseWrapper> search(@RequestParam(value = "latitude", required = true) Double latitude,
	                                              @RequestParam(value = "longitude", required = true) Double longitude,
	                                              @RequestParam(value = "token", required = true) String token,
	                                              @RequestParam(value = "radius", required = false) Integer radius) {
		// Check radius
		Integer searchRadius = (radius == null ? 5 : radius);

		// Create location
		GeoLocation location = new GeoLocation(latitude, longitude);
		log.info("here is the location: " + location);

		// Mount filter
		ResponseEntity<ResponseWrapper> responseEntity;
		GroupLocationFilter filter = (location != null ? new GroupLocationFilter(location, searchRadius, false) : null);
		log.info("searching for groups and with location filter = {}", filter);

		// Returns list
		List<ObjectLocation> objectsToReturn = new ArrayList<ObjectLocation>();

		// Load groups
		List<ObjectLocation> groups = objectLocationBroker.fetchGroupLocations(location, radius);

		// Save groups
		objectsToReturn.addAll(groups);

		// Load meetings
		if (false) {
			for (ObjectLocation group : groups) {
				// Get meetings
				List<ObjectLocation> meetings = objectLocationBroker.fetchMeetingLocationsByGroup(group, location, radius);
				// Concat the results
				objectsToReturn.addAll(meetings);
			}
		} else {
			List<ObjectLocation> meetings = objectLocationBroker.fetchMeetingLocations(location, radius);
			// Concat the results
			objectsToReturn.addAll(meetings);
		}

		// Check results
		if (objectsToReturn == null || objectsToReturn.isEmpty()) {
			log.info("found no objects ... returning empty ...");
			responseEntity = RestUtil.okayResponseWithData(RestMessage.NO_GROUP_MATCHING_TERM_FOUND, Collections.emptyList());
		} else {
			responseEntity = RestUtil.okayResponseWithData(RestMessage.POSSIBLE_GROUP_MATCHES, objectsToReturn);
		}
		return responseEntity;
	}
}