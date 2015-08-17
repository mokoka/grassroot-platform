package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import za.org.grassroot.webapp.controller.BaseController;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class HomeController extends BaseController{

    private static final Logger LOGGER = LoggerFactory.getLogger(HomeController.class);

    @RequestMapping("/")
    public String getIndexPage() {
        LOGGER.debug("Getting home page");
        return "index";
    }

    @RequestMapping("/home")
    public String getHomePage()
    {
        return "home";
    }
}
