package com.blueskykong.tm.core.service.impl;

import com.blueskykong.tm.common.entity.TransactionMsg;
import com.blueskykong.tm.common.enums.OperationEnum;
import com.blueskykong.tm.common.holder.LogUtil;
import com.blueskykong.tm.core.interceptor.TxTransactionInterceptor;
import com.blueskykong.tm.core.service.ExternalNettyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class ExternalNettyServiceImpl implements ExternalNettyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalNettyServiceImpl.class);

    private TxTransactionInterceptor txTransactionInterceptor;

    public ExternalNettyServiceImpl(TxTransactionInterceptor txTransactionInterceptor) {
        this.txTransactionInterceptor = txTransactionInterceptor;
    }

    @Override
//    @ReliableTransaction
    public Boolean preSend(List<TransactionMsg> preCommitMsgs) {
        Object[] args = new Object[]{preCommitMsgs};
        LogUtil.info(LOGGER, () -> "发送preCommit消息");
        txTransactionInterceptor.interceptor(args, OperationEnum.TX_NEW);
        return true;
    }

    @Override
//    @ReliableTransaction(OperationEnum.TX_COMPLETE)
    public void postSend(Boolean success, Object message) {
        Object[] args = new Object[]{success, message};
        LogUtil.info(LOGGER, "发送confirm消息, {} ", () -> success);
        txTransactionInterceptor.interceptor(args, OperationEnum.TX_COMPLETE);
    }

    @Override
//    @ReliableTransaction(OperationEnum.TX_CONSUMED)
    public void consumedSend(TransactionMsg msg, Boolean success) {
        Object[] args = new Object[]{msg, success};

        LogUtil.info(LOGGER, "发送Consume消息，groupId {} and subTaskId {}，消费结果为：{}", () -> msg.getGroupId(),
                () -> msg.getSubTaskId(), () -> success);

        txTransactionInterceptor.interceptor(args, OperationEnum.TX_CONSUMED);
    }
}
