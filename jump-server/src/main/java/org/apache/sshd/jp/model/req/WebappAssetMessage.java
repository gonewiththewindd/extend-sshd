package org.apache.sshd.jp.model.req;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Data
@Accessors(chain = true)
public class WebappAssetMessage extends AssetMessage {

    private HttpServletRequest request;
    private HttpServletResponse response;

}
