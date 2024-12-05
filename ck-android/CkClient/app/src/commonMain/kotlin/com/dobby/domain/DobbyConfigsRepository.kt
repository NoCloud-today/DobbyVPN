package com.dobby.domain

interface DobbyConfigsRepository {

    // region cloak
    fun getCloakConfig(): String

    fun setCloakConfig(newConfig: String)

    fun getIsCloakEnabled(): Boolean

    fun setIsCloakEnabled(isCloakEnabled: Boolean)
    // endregion

    // region outline
    fun getOutlineKey(): String

    fun setOutlineKey(newOutlineKey: String)

    fun getIsOutlineEnabled(): Boolean

    fun setIsOutlineEnabled(isOutlineEnabled: Boolean)
    // endregion
}
