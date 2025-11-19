package code.blurone.cowardless

interface Coward {
    companion object {
        val byName: MutableMap<String, Coward> = mutableMapOf()
    }

    val name: String
    var remainingTicks: Long

    fun remove(logMessage: String, async: Boolean)
}