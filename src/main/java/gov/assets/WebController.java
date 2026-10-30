package gov.assets;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class WebController {
    @GetMapping({"/", "/login", "/reports", "/simulation", "/blockchain", "/assets/new", "/assets/{id}/edit"})
    public String frontend() { return "forward:/index.html"; }
}
