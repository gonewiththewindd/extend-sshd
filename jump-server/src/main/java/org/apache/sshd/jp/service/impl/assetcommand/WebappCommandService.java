package org.apache.sshd.jp.service.impl.assetcommand;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.jp.model.entity.AssetOperation;
import org.apache.sshd.jp.model.req.WebappAssetMessage;
import org.apache.sshd.jp.service.def.SubAssetCommandService;
import org.apache.sshd.server.shell.test.Asset;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;

import static org.apache.sshd.jp.constants.AssetTypeConstants.ASSET_SERVICE_WEBAPP;

@Slf4j
@Component(ASSET_SERVICE_WEBAPP)
public class WebappCommandService implements SubAssetCommandService<WebappAssetMessage, AssetOperation> {

    private RestTemplate restTemplate = new RestTemplate();

    @Override
    public AssetOperation parse(WebappAssetMessage message) {

//        int index = message.getMessage().indexOf("/");
//        String assetId = message.getMessage().substring(0, index);
//        String path = message.getMessage().substring(index + 1);

        AssetOperation assetOperation = new AssetOperation()
                .setAssetId(message.getAssetId())
                .setOpt(message.getMessage())
                .setAssetMessage(message);

        return assetOperation;
    }

    @Override
    public boolean verify(AssetOperation operation) {
        return false;
    }

    @Override
    public AssetOperation execute(AssetOperation operation) {
        HttpServletRequest localReq = ((WebappAssetMessage) operation.getAssetMessage()).getRequest();
        HttpServletResponse localResp = ((WebappAssetMessage) operation.getAssetMessage()).getResponse();
        try {
            // 请求地址替换
            Asset asset = operation.getAsset();
            String targetUrl = localReq.getScheme()
                    .concat("://")
                    .concat(asset.getAddress())
                    .concat(":")
                    .concat(String.valueOf(asset.getPort()))
                    .concat(operation.getOpt());
            if (StringUtils.isNotBlank(localReq.getQueryString())) {
                targetUrl = targetUrl.concat("?").concat(localReq.getQueryString());
            }
            // TODO 异常透传
            restTemplate.execute(targetUrl, HttpMethod.resolve(localReq.getMethod()), remoteReq -> {
                // 请求头透传
                setupReqHeaders(localReq, remoteReq);
                // 请求体透传
                if (localReq.getInputStream().available() > 0) {
                    int len;
                    for (byte[] buffer = new byte[8092]; (len = localReq.getInputStream().read(buffer)) != -1; ) {
                        remoteReq.getBody().write(buffer, 0, len);
                    }
                }
            }, remoteResp -> {
                // 应答结果保留（json）
                if (remoteResp.getBody().available() > 0) {
                    // 应答头透传
                    setupRespHeaders(remoteResp, localResp);
                    // 应答体透传
                    byte[] retain = remoteResp.getBody().readAllBytes(); //TODO 大文件下载 内存溢出
                    localResp.getOutputStream().write(retain);
                    localResp.getOutputStream().flush();

                    String contentType = remoteResp.getHeaders().get(HttpHeaders.CONTENT_TYPE).get(0);
                    if (contentType.contains("json")) {
                        Object parse = JSON.parse(retain);
                        String jsonString = JSON.toJSONString(parse);
                        log.info("remoteResp:{}", jsonString);
                        operation.setResult(jsonString);
                        return parse;
                    }
                }
                return null;
            });

            return operation;

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (e instanceof RestClientException rce) {
//                localResp.getOutputStream().write(rce.getMessage().getBytes(StandardCharsets.UTF_8));
//                localResp.getOutputStream().flush();
            }
            throw new RuntimeException(e);
        }
    }

    private void setupRespHeaders(ClientHttpResponse remoteResp, HttpServletResponse localResp) {
        remoteResp.getHeaders().entrySet().stream().forEach(entry -> {
            String key = entry.getKey();
            List<String> value = entry.getValue();
            localResp.addHeader(key, value.get(0));
        });
    }

    private void setupReqHeaders(HttpServletRequest localReq, ClientHttpRequest remoteReq) {
        Enumeration<String> headerNames = localReq.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            remoteReq.getHeaders().add(headerName, localReq.getHeader(headerName));
        }
    }
}
