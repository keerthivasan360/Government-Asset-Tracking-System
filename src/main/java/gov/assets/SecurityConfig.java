package gov.assets;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean UserDetailsService users(UserRepository users){return name -> users.findByUsername(name)
        .map(u -> User.withUsername(u.username).password(u.password).roles(u.role).build())
        .orElseThrow(() -> new UsernameNotFoundException(name));}
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.FORWARD,jakarta.servlet.DispatcherType.ERROR).permitAll()
            .requestMatchers("/","/login","/reports","/simulation","/simulation/**","/blockchain","/assets/new","/assets/*/edit","/index.html","/ui/**","/api/session","/error").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/**").authenticated()
            .requestMatchers("/api/assets/**").hasRole("ADMIN")
            .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((request,response,error) -> response.sendError(401)))
            .formLogin(f -> f.loginPage("/login").loginProcessingUrl("/api/login")
                .successHandler((request,response,authentication) -> response.setStatus(204))
                .failureHandler((request,response,error) -> response.sendError(401,"Username or password is incorrect.")).permitAll())
            .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((request,response,authentication) -> response.setStatus(204)).permitAll()).build();
    }
}
