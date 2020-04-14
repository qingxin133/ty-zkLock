package ty;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.lang3.StringUtils;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ZK分布式锁操作类
 */
public class ZookeeperLock {

    //zk地址和端口号
    private final String zkServers = "192.168.0.120:2181";
    //连接超时时限
    private final int connectionTimeOut = 20000;
    //session超时时限
    private final int sessionTimeOut = 30000;
    //分割符
    private final static String SLASH = "/";
    //锁内容
    private final static String LOCK_DATA = "h";
    //zk实例
    private ZkClient zkClient;

    ZookeeperLock() {
        zkClient = new ZkClient(zkServers, sessionTimeOut, connectionTimeOut);
    }

    /**
     * 获取锁
     * @param lockId
     * @param timeout
     * @return
     */
    public LockNode lock(String lockId, long timeout) {
        LockNode lockNode = createLockNode(lockId);
        lockNode = tryActiveLock(lockNode);
        if (!lockNode.isActive()) {
            try {
                synchronized (lockNode) {
                    lockNode.wait(timeout); //tryActiveLock
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return lockNode;
    }

    /**
     * 激活锁
     *
     * @param lockNode
     * @return
     */
    private LockNode tryActiveLock(LockNode lockNode) {
        if (StringUtils.isBlank(lockNode.getLockId()))
            throw new RuntimeException("锁路径不能为空");
        String firstPath = "";
        //判断是否获得锁
        List<String> list = zkClient.getChildren(lockNode.getLockId())
                .stream()
                .sorted()
                .map(p -> lockNode.getLockId() + ZookeeperLock.SLASH + p)
                .collect(Collectors.toList());
        if (list != null && list.size()>0)
            firstPath = list.get(0);
        //如果自己是第一个，把自己激活
        if (firstPath.equals(lockNode.getPath())) {
            lockNode.setActive(true);
        } else {
            //自己不是第一个，监听你前面的，以等待锁的释放
            String beforeNode = list.get(list.indexOf(lockNode.getPath()) - 1);
            zkClient.subscribeDataChanges(beforeNode, new IZkDataListener() {
                //监听你的前一个节点的删除事件
                //如果当前线程超时，导至节点被删除，将影响锁的逻辑
                @Override
                public void handleDataDeleted(String dataPath) throws Exception {
                    System.out.println("删除节点：" + dataPath);
                    //重新激活锁，再获得锁，如果可以获得锁，再释放，如果没有权限继续添加监听，此时的监听会往上移一个
                    LockNode lock = tryActiveLock(lockNode);
                    synchronized (lockNode) {
                        if (lock.isActive()) {
                            lockNode.notify();
                        }
                    }
                    //// 取消监听beforeNode节点
                    zkClient.unsubscribeDataChanges(beforeNode, this);
                }
                @Override
                public void handleDataChange(String s, Object o) throws Exception {}
            });
        }
        //添加上一个节点变更监听
        //再次尝试激活锁
        return lockNode;
    }

    /**
     * 创建临时有序节点
     * @param lockId 锁的父路径
     * @return 返回组装好的实体类
     */
    private LockNode createLockNode(String lockId) {
            if (!zkClient.exists(lockId)) {
                zkClient.createPersistent(lockId);
            }
            String path = zkClient.createEphemeralSequential(lockId + ZookeeperLock.SLASH, ZookeeperLock.LOCK_DATA);
            LockNode lockNode = new LockNode();
            lockNode.setActive(false);
            lockNode.setLockId(lockId);
            lockNode.setPath(path);
            return lockNode;
    }

    /**
     * 释放锁
     * 如果获得锁就删除掉
     * @param lockNode
     */
    public void unLock(LockNode lockNode) {
        if (lockNode.isActive()) {
            zkClient.delete(lockNode.getPath());
        }
    }


}
