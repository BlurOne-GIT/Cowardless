package code.blurone.cowardless.nms.common

interface NonPlayableCoward {
    companion object {
        val byName: MutableMap<String, NonPlayableCoward> = mutableMapOf()
    }

    val name: String
    var remainingTicks: Long

    fun flagForRemoval()

    fun remove(logMessage: String)
}