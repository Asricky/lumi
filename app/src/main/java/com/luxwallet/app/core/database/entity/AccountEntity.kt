package com.luxwallet.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.luxwallet.app.core.model.AccountKind
import com.luxwallet.app.core.model.AccountProvider
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AccountKind,
    val provider: AccountProvider,
    val ownerName: String? = null,
    val currentEstimatedBalance: Long = 0,
    val openingBalance: Long = 0,
    val openingBalanceDate: Long,
    val isOwnedByUser: Boolean = true,
    val includeInNetWorth: Boolean = true,
    val isActive: Boolean = true,
    val lastReconciledAt: Long? = null,
    val notes: String? = null
)
