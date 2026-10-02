package com.luxwallet.app.data

import com.luxwallet.app.core.database.dao.AssetDao
import com.luxwallet.app.core.database.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

class AssetRepository(private val assetDao: AssetDao) {
    fun observeAll(): Flow<List<AssetEntity>> = assetDao.observeAll()
    fun observeTotalValue(): Flow<Long> = assetDao.observeTotalValue()
    suspend fun upsert(asset: AssetEntity): Long =
        if (asset.id == 0L) assetDao.insert(asset) else { assetDao.update(asset); asset.id }
    suspend fun delete(asset: AssetEntity) = assetDao.delete(asset)
    suspend fun restore(id: Long) = assetDao.setArchived(id, false)
    fun observeArchived() = assetDao.observeArchived()
}
