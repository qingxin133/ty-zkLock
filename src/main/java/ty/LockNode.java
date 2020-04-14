package ty;

public class LockNode {
    private String lockId;
    private String path;
    private boolean active;

    public LockNode(String lockId, String path, boolean active) {
        this.lockId = lockId;
        this.path = path;
        this.active = active;
    }

    public LockNode() {
    }

    public String getLockId() {
        return lockId;
    }

    public void setLockId(String lockId) {
        this.lockId = lockId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
