package org.apache.sshd.jp.model.req;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AssetMessage {

    protected String assetId;
    protected String message;

}
