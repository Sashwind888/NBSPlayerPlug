package sashwind.mc.plugin.nbsplayer.scheduler;

public interface WrappedTask {
    /**
     * 取消任务
     */
    void cancel();

    /**
     * 检查任务是否已取消
     */
    boolean isCancelled();
}
