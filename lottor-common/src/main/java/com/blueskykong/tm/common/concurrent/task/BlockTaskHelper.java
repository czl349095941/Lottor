package com.blueskykong.tm.common.concurrent.task;

import com.blueskykong.tm.common.exception.TransactionRuntimeException;
import com.blueskykong.tm.common.holder.IdWorkerUtils;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import org.apache.commons.lang.StringUtils;

import java.util.concurrent.ExecutionException;

public class BlockTaskHelper {

    private static final int MAX_COUNT = 10000;

    private static final BlockTaskHelper BLOCK_TASK_HELPER = new BlockTaskHelper();

    private BlockTaskHelper() {

    }

    private static final LoadingCache<String, BlockTask> LOADING_CACHE = CacheBuilder.newBuilder()
            .maximumWeight(MAX_COUNT)
            .weigher((Weigher<String, BlockTask>) (string, BlockTask) -> getSize())
            .build(new CacheLoader<String, BlockTask>() {
                @Override
                public BlockTask load(String key) throws Exception {
                    return createTask(key);
                }
            });


    public static BlockTaskHelper getInstance() {
        return BLOCK_TASK_HELPER;
    }

    private static int getSize() {
        if (LOADING_CACHE == null) {
            return 0;
        }
        return (int) LOADING_CACHE.size();
    }


    private static BlockTask createTask(String key) {
        BlockTask task = new BlockTask();
        task.setKey(key);
        return task;
    }


    /**
     * 获取task
     *
     * @param key 需要获取的key
     */
    public BlockTask getTask(String key) {
        try {
            return LOADING_CACHE.get(key);
        } catch (ExecutionException e) {
            throw new TransactionRuntimeException(e.getCause());
        }
    }


    public void removeByKey(String key) {
        if (StringUtils.isNotEmpty(key)) {
            LOADING_CACHE.invalidate(key);
        }
    }

    public static void main(String[] args) throws ExecutionException {
        final String taskKey = IdWorkerUtils.getInstance().createTaskKey();
        final BlockTask task = BlockTaskHelper.getInstance().getTask(taskKey);
        System.out.println(task.getKey());

        BlockTaskHelper.getInstance().removeByKey(taskKey);


        System.out.println(LOADING_CACHE.size());

        System.out.println(LOADING_CACHE.size());

    }

}
