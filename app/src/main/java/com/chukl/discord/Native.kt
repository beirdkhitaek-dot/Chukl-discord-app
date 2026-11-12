package com.chukl.discord

/** Calls into the C++ bridge (libchukl_discord.so) that wraps the Discord Social SDK. */
object Native {
    const val APP_ID = 1569968585475100722L

    init {
        System.loadLibrary("discord_partner_sdk")
        System.loadLibrary("chukl_discord")
    }

    @JvmStatic external fun init(appId: Long, logDir: String)
    @JvmStatic external fun tick()
    @JvmStatic external fun startLogin()
    @JvmStatic external fun loginWithRefresh(refreshToken: String)
    @JvmStatic external fun setPlaying(on: Boolean)
    @JvmStatic external fun getState(): String
    @JvmStatic external fun takeTokens(): String
    @JvmStatic external fun signOut()

    /** Returns the connection state: SIGNED_OUT, WAITING_LOGIN, CONNECTING, READY or ERROR. */
    fun state(): String = getState().substringBefore('|')

    fun message(): String = getState().substringAfter('|', "")
}
