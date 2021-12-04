package io.github.nic562.screen.recorder.base

interface SomethingWithBackPressed {
    /**
     * 响应返回事件的处理
     * @return 完成调用后是否消费掉本次事件，默认是消费掉
     */
    fun onBackPressed(): Boolean {
        return true
    }
}