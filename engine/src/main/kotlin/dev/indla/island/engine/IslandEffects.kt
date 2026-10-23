package dev.indla.island.engine

/**
 * Side effects the engine asks for. Implemented in the listener module, which holds the real
 * `Notification` objects (PendingIntents, RemoteInputs) behind each key.
 */
interface IslandEffects {
    fun openItem(key: String)
    fun invokeAction(key: String, actionIndex: Int)
    fun reply(key: String, actionIndex: Int, text: String)
    fun dismissNotification(key: String)
    fun muteApp(packageName: String)

    object None : IslandEffects {
        override fun openItem(key: String) = Unit
        override fun invokeAction(key: String, actionIndex: Int) = Unit
        override fun reply(key: String, actionIndex: Int, text: String) = Unit
        override fun dismissNotification(key: String) = Unit
        override fun muteApp(packageName: String) = Unit
    }
}
