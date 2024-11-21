package org.apache.sshd.jp.asset.webapp;

import org.apache.sshd.jp.model.req.AssetMessage;
import org.apache.sshd.jp.model.req.WebappAssetMessage;
import org.apache.sshd.jp.service.def.AssetCommandService;
import org.apache.sshd.jp.utils.ApplicationContextHolder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<WebAppFilter> loggingFilter() {
        FilterRegistrationBean<WebAppFilter> registrationBean
                = new FilterRegistrationBean<>();

        registrationBean.setFilter(new WebAppFilter());
//        registrationBean.addUrlPatterns("/**");
        registrationBean.setOrder(0);

        return registrationBean;
    }

    public static class WebAppFilter implements Filter {

        @Override
        public void doFilter(ServletRequest req, ServletResponse response, FilterChain chain) throws IOException, ServletException {

            HttpServletRequest request = (HttpServletRequest) req;
            String requestURI = request.getRequestURI();
            int index = requestURI.indexOf("/", 1);
            String assetId = requestURI.substring(1, index);
            String path = requestURI.substring(index);

            AssetMessage assetMessage = new WebappAssetMessage()
                    .setRequest(request)
                    .setResponse((HttpServletResponse) response)
                    .setAssetId(assetId)
                    .setMessage(path);

            AssetCommandService commandService = ApplicationContextHolder.getBean(AssetCommandService.class);
            Object process = commandService.process(assetMessage);
        }
    }

}
