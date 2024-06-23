package code.blurone.cowardless.nms.common

interface ServerNpc {
    companion object {
        val byName: MutableMap<String, ServerNpc> = mutableMapOf()
    }

    val name: String
    var remainingTicks: Long

    fun flagForRemoval()

    fun remove(logMessage: String)
}