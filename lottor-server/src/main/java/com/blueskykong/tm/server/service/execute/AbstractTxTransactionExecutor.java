package com.blueskykong.tm.server.service.execute;

import com.blueskykong.tm.common.enums.TransactionStatusEnum;
import com.blueskykong.tm.common.holder.LogUtil;
import com.blueskykong.tm.common.netty.bean.TxTransactionItem;
import com.blueskykong.tm.server.config.Address;
import com.blueskykong.tm.server.service.TxManagerService;
import com.blueskykong.tm.server.service.TxTransactionExecutor;
import com.blueskykong.tm.server.socket.SocketManager;
import io.netty.channel.Channel;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


public abstract class AbstractTxTransactionExecutor implements TxTransactionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTxTransactionExecutor.class);


    /**
     * 当出现异常等情况的时候,进行回滚操作。
     *
     * @param txGroupId          事务组id
     * @param txTransactionItems 回滚事务项
     * @param elseItems          其他事务项（当netty长连接不在同一个txManager情况下）
     */
    protected abstract void doRollBack(String txGroupId, List<TxTransactionItem> txTransactionItems, List<TxTransactionItem> elseItems);

    /**
     * 当事务组完成时候，通知各业务模块，进行提交事务的操作。
     *
     * @param txGroupId          事务组id
     * @param txTransactionItems 提交事务项
     * @param elseItems          其他事务项（当netty长连接不在同一个txManager情况下）
     */
    protected abstract void doCommit(String txGroupId, List<TxTransactionItem> txTransactionItems, List<TxTransactionItem> elseItems);


    private TxManagerService txManagerService;

    protected void setTxManagerService(TxManagerService txManagerService) {
        this.txManagerService = txManagerService;
    }


    /**
     * 回滚整个事务组
     *
     * @param txGroupId 事务组id
     */
    @Override
    public void rollBack(String txGroupId) {
        try {
            txManagerService.updateTxTransactionItemStatus(txGroupId, txGroupId, TransactionStatusEnum.ROLLBACK.getCode(), null);
            final List<TxTransactionItem> txTransactionItems = txManagerService.listByTxGroupId(txGroupId);
            if (CollectionUtils.isNotEmpty(txTransactionItems)) {
                final Map<Boolean, List<TxTransactionItem>> listMap = filterData(txTransactionItems);
                if (Objects.isNull(listMap)) {
                    LogUtil.info(LOGGER, "事务组id:{},提交失败！数据不完整", () -> txGroupId);
                    return;
                }
                final List<TxTransactionItem> currentItem = listMap.get(Boolean.TRUE);
                final List<TxTransactionItem> elseItems = listMap.get(Boolean.FALSE);
                doRollBack(txGroupId, currentItem, elseItems);
            }
        } finally {
            //txManagerService.removeRedisByTxGroupId(txGroupId);
        }
    }


    /**
     * 事务预提交
     *
     * @param txGroupId 事务组id
     * @return true 成功 false 失败
     */
    @Override
    public Boolean preCommit(String txGroupId) {
        txManagerService.updateTxTransactionItemStatus(txGroupId, txGroupId, TransactionStatusEnum.COMMIT.getCode(), null);
        final List<TxTransactionItem> txTransactionItems = txManagerService.listByTxGroupId(txGroupId);

        final Map<Boolean, List<TxTransactionItem>> listMap = filterData(txTransactionItems);

        if (Objects.isNull(listMap)) {
            LogUtil.info(LOGGER, "事务组id:{},提交失败！数据不完整", () -> txGroupId);
            return false;
        }

        final List<TxTransactionItem> currentItem = listMap.get(Boolean.TRUE);

        final List<TxTransactionItem> elseItems = listMap.get(Boolean.FALSE);

        //检查channel 是否都激活，渠道状态不是回滚的
        final Boolean ok = checkChannel(currentItem);


        if (!ok) {
            doRollBack(txGroupId, currentItem, elseItems);
        } else {
            doCommit(txGroupId, currentItem, elseItems);
        }
        return true;
    }


    private Boolean checkChannel(List<TxTransactionItem> txTransactionItems) {
        if (CollectionUtils.isNotEmpty(txTransactionItems)) {
            final List<TxTransactionItem> collect = txTransactionItems.stream().filter(item -> {
                Channel channel = SocketManager.getInstance().getChannelByModelName(item.getModelName());
                return Objects.nonNull(channel) && (channel.isActive() || item.getStatus() != TransactionStatusEnum.ROLLBACK.getCode());
            }).collect(Collectors.toList());
            return txTransactionItems.size() == collect.size();
        }
        return true;

    }

    private Map<Boolean, List<TxTransactionItem>> filterData(List<TxTransactionItem> txTransactionItems) {
        //过滤掉发起方的数据，发起方已经进行提交，不需要再通信进行
        final List<TxTransactionItem> collect = txTransactionItems.stream()
//                .filter(item -> item.getRole() == TransactionRoleEnum.ACTOR.getCode())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(collect)) {
            return null;
        }
        return collect.stream().collect
                (Collectors.partitioningBy(item ->
                        Objects.equals(Address.getInstance().getDomain(), item.getTmDomain())));
    }

}
