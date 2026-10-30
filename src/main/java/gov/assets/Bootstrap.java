package gov.assets;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Component
public class Bootstrap implements CommandLineRunner {
    private final UserRepository users; private final PasswordEncoder encoder;
    @Value("${tracking.admin-password}") private String admin;
    @Value("${tracking.officer-password}") private String officer;
    public Bootstrap(UserRepository users,PasswordEncoder encoder){this.users=users;this.encoder=encoder;}
    public void run(String...args){
        String adminPass = admin != null && !admin.isBlank() ? admin : "admin";
        String officerPass = officer != null && !officer.isBlank() ? officer : "officer";
        upsert("admin", "ADMIN", adminPass);
        upsert("officer", "OFFICER", officerPass);
    }
    private void upsert(String name, String role, String password) {
        AppUser user = users.findByUsername(name).orElseGet(() -> {
            AppUser u = new AppUser();
            u.username = name;
            u.role = role;
            return u;
        });
        user.password = encoder.encode(password);
        users.save(user);
        LoggerFactory.getLogger(getClass()).info("User '{}' configured with password.", name);
    }
}
