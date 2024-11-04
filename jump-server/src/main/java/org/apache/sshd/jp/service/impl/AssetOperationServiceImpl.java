package org.apache.sshd.jp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.sshd.jp.mapper.AssetOperationMapper;
import org.apache.sshd.jp.model.entity.AssetOperation;
import org.apache.sshd.jp.service.def.AssetOperationService;
import org.springframework.stereotype.Service;

@Service
public class AssetOperationServiceImpl extends ServiceImpl<AssetOperationMapper, AssetOperation> implements AssetOperationService {
}
