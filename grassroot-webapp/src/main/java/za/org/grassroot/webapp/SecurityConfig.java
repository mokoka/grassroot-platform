package za.org.grassroot.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.sql.DataSource;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Order(SecurityProperties.ACCESS_OVERRIDE_ORDER)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;
    private final Environment environment;

    @Autowired
    public SecurityConfig(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder, DataSource dataSource, Environment environment) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring()
                .antMatchers("/static/**")
                .antMatchers("/assets/**")
                .antMatchers("/i18n/**")
                .antMatchers("/api/**")
                .antMatchers("/test/**")
                .antMatchers("/sms/**")
                .antMatchers("/image/**");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                    .antMatchers("/index").permitAll()
                    .antMatchers("/signup").permitAll()
                    .antMatchers("/signup/extra").permitAll()
                    .antMatchers("/user/recovery").permitAll()
                    .antMatchers("/user/recovery/success").permitAll()
                    .antMatchers("/grass-root-verification/*").permitAll()
                    .antMatchers("/cardauth/**").permitAll()
                    .antMatchers("/ussd/**").hasIpAddress(environment.getProperty("grassroot.ussd.gateway", "127.0.0.1"))
                    .anyRequest().authenticated()
                    .and()
                .formLogin()
                    .successHandler(savedRequestAwareAuthenticationSuccessHandler())
                    .defaultSuccessUrl("/home")
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .permitAll()
                    .and()
                .logout()
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .logoutSuccessUrl("/login")
                    .permitAll().and()
                .rememberMe()
                    .rememberMeServices(rememberMeServices())
                    .useSecureCookie(true).and()
                .headers().frameOptions().sameOrigin().and() // in future see if can path restrict this
                .csrf().csrfTokenRepository(new HttpSessionCsrfTokenRepository());
    }

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .authenticationProvider(rememberMeAuthenticationProvider())
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder);
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl db = new JdbcTokenRepositoryImpl();
        db.setDataSource(dataSource);
        return db;
    }

    @Bean
    public SavedRequestAwareAuthenticationSuccessHandler
    savedRequestAwareAuthenticationSuccessHandler() {
        SavedRequestAwareAuthenticationSuccessHandler auth
                = new SavedRequestAwareAuthenticationSuccessHandler();
        auth.setTargetUrlParameter("targetUrl");
        return auth;
    }

    @Bean
    public RememberMeServices rememberMeServices() {
        PersistentTokenBasedRememberMeServices services = new PersistentTokenBasedRememberMeServices(
                environment.getProperty("RM_KEY", "grassrootremembers"),
                userDetailsService, persistentTokenRepository());
        services.setTokenValiditySeconds(1209600); // two weeks
        return services;
    }

    @Bean
    public RememberMeAuthenticationProvider rememberMeAuthenticationProvider() {
        return new RememberMeAuthenticationProvider(environment.getProperty("RM_KEY", "grassrootremembers"));
    }

    @Bean
    public MethodInvokingFactoryBean methodInvokingFactoryBean() {
        MethodInvokingFactoryBean methodInvokingFactoryBean = new MethodInvokingFactoryBean();
        methodInvokingFactoryBean.setTargetClass(SecurityContextHolder.class);
        methodInvokingFactoryBean.setTargetMethod("setStrategyName");
        methodInvokingFactoryBean.setArguments(new String[]{SecurityContextHolder.MODE_INHERITABLETHREADLOCAL});
        return methodInvokingFactoryBean;
    }

}
