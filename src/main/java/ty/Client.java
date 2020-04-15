package ty;

import org.I0Itec.zkclient.ZkClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Client {

    private static ZookeeperLock zl = new ZookeeperLock();
    private static final String R_NODE = "/ZK_LOCKA";

    public static void main(String[] args) throws IOException, InterruptedException {
        Client.doMain();
    }

    /**
     *
     */
    public static void doEpNodeSimple() {
        ZkClient zkClient = null;
        try {
            String zkServers = "192.168.0.120:2181,192.168.0.120:2182,192.168.0.120:2183";
            int connectionTimeOut = 20000;
            int sessionTimeOut = 30000;
            zkClient = new ZkClient(zkServers, sessionTimeOut, connectionTimeOut);
            if (!zkClient.exists(R_NODE)) {
                zkClient.createPersistent(R_NODE);
            }
            String path = zkClient.createEphemeralSequential(R_NODE + "/", "lock");
            System.out.println(path);
            zkClient.close();
            System.out.println("end");
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            if (zkClient != null) {
                zkClient.close();
            }
        }
    }

    /**
     * 获取锁测试
     */
    public static void testOne() {
        ZookeeperLock zl = new ZookeeperLock();
        LockNode nana = zl.getLock(R_NODE, 365 * 24 * 3600 * 1000);
        System.out.println("获取到nana锁");
    }

    /**
     * 主类测试
     * 1000个线程抢锁，有序写入文件
     * @throws InterruptedException
     * @throws IOException
     */
    public static void doMain() throws InterruptedException, IOException {

        File file = new File("d:/test.txt");
        if (!file.exists()) {
            file.createNewFile();
        }
        ExecutorService es = Executors.newCachedThreadPool();
        for (int i = 0; i < 1000; i++) {
            es.submit(() -> {
                LockNode lockNode = zl.getLock(R_NODE, 60 * 1000); //获取锁，没有获取到就自旋60秒
                try {
                    //如果获取到锁，执行业务操作
                    String firstLine = Files.lines(file.toPath()).findFirst().orElse("0");
                    int count = Integer.parseInt(firstLine);
                    count++;
                    Files.write(file.toPath(), String.valueOf(count).getBytes());
                    System.out.println(Thread.currentThread().getName()+"写入数据：" + count);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    //执行完本服务的业务，释放锁
                    zl.unLock(lockNode);
                }
            });
        }
        es.shutdown();
        es.awaitTermination(10, TimeUnit.SECONDS);
        String firstLine = Files.lines(file.toPath()).findFirst().orElse("0");
        System.out.println(firstLine);
    }

}
