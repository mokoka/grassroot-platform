package za.org.grassroot.webapp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.webapp.enums.USSDSection;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Created by luke on 2015/12/04.
 */
public class USSDUrlUtil {

    private static final Logger log = LoggerFactory.getLogger(USSDUrlUtil.class);

    public static final String homePath = "/ussd/";

    // todo: consider using getters (may cause issues with @RequestParam annotation requiring constants though)
    public static final String
            phoneNumber = "msisdn",
            userInputParam = "request",
            groupIdParam = "groupId",
            eventIdParam = "eventId",
            previousMenu = "prior_menu",
            yesOrNoParam = "confirmed",
            interruptedFlag = "interrupted",
            interruptedInput = "prior_input"; // may want to change this last one

    public static final String
            groupIdUrlSuffix = ("?" + groupIdParam + "="),
            eventIdUrlSuffix = ("?" + eventIdParam + "="),
            setInterruptedFlag = "&" + interruptedFlag + "=1";

    public static String assemblePaginatedURI(String menuPrompt, String existingGroupUri, String newGroupUri, Integer pageNumber) {
        String newGroupParameter = (newGroupUri != null) ? "&newUri=" + encodeParameter(newGroupUri) : "";
        return "group_page?prompt=" + encodeParameter(menuPrompt) + "&existingUri=" + encodeParameter(existingGroupUri)
                + newGroupParameter + "&page=" + pageNumber;
    }

    public static String encodeParameter(String stringToEncode) {
        try {
            return URLEncoder.encode(stringToEncode, "UTF-8");
        } catch (UnsupportedEncodingException e) { // todo: handle errors better
            return stringToEncode;
        }
    }

    public static String createInterruptedString(String path, Map<String, String> parameters) {
        // note: this might be more efficient with a URLBuilder or the like, but those (AFAIK) impose fully written paths
        StringBuilder urlBuilder = new StringBuilder(path + "?");
        for (Map.Entry<String, String> parameter : parameters.entrySet())
            urlBuilder.append(parameter.getKey() + "=" + encodeParameter(parameter.getValue()));
        return urlBuilder.toString();
    }

    // note: this expects the entityId string fully formed (e.g., "groupId=1"), else have to create many duplicate methods
    public static String saveMenuUrlWithInput(USSDSection section, String menu, String entityId, String input) {
        String divisor = (entityId != null) ? entityId : "?"; // if we are passed groupId=1, or similar, it comes with the "?" character
        return section.toPath() + menu + divisor + setInterruptedFlag + "&" + interruptedInput + "=" + encodeParameter(input);
    }

    public static String saveMeetingMenu(String menu, Long eventId, boolean revising) {
        String revisingFlag = (revising) ? "&revising=1" : "";
        return USSDSection.MEETINGS.toPath() + menu + "?eventId=" + eventId + setInterruptedFlag;
    }

    public static String saveGroupMenu(String menu, Long groupId) {
        return USSDSection.GROUP_MANAGER.toPath() + menu + "?groupId=" + groupId + setInterruptedFlag;
    }

    public static String saveVoteMenu(String menu, Long eventId) {
        return USSDSection.VOTES.toPath() + menu + "?eventId=" + eventId + setInterruptedFlag; // include the +1
    }

}
