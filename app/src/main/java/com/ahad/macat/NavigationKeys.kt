package com.ahad.macat

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Feed : NavKey

@Serializable data object Grid : NavKey

/** [itemId] != null means editing an existing item. */
@Serializable data class AddItem(val itemId: Long? = null) : NavKey

@Serializable data object BulkAdd : NavKey
