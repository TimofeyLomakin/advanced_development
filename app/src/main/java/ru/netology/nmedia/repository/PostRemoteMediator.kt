package ru.netology.nmedia.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import retrofit2.HttpException
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dao.PostRemoteKeyDao
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.entity.PostRemoteKeyEntity
import ru.netology.nmedia.error.ApiError

@OptIn(ExperimentalPagingApi::class)
class PostRemoteMediator(
    private val apiService: ApiService,
    private val postDao: PostDao,
    private val postRemoteKeyDao: PostRemoteKeyDao,
    private val appDb: AppDb
) : RemoteMediator<Int, PostEntity>() {

    override suspend fun load(loadType: LoadType, state: PagingState<Int, PostEntity>): MediatorResult {
        return try {
            when (loadType) {
                LoadType.PREPEND -> {
                    MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.REFRESH -> {
                    val lastId = postDao.getLatestPost()?.id
                    val response = if (lastId == null) {
                        apiService.getLatest(state.config.pageSize)
                    } else {
                        apiService.getAfter(lastId, state.config.pageSize)
                    }

                    if (!response.isSuccessful) throw ApiError(response.code(), response.message())
                    val body = response.body() ?: throw ApiError(response.code(), response.message())

                    appDb.withTransaction {
                        if (body.isNotEmpty()) {
                            val newFirstId = body.maxByOrNull { it.id }?.id
                            val existingKeys = postRemoteKeyDao.getKeys()
                            val newKeys = mutableListOf<PostRemoteKeyEntity>().apply {
                                if (existingKeys.none { it.type == PostRemoteKeyEntity.KeyType.AFTER }) {
                                    add(PostRemoteKeyEntity(PostRemoteKeyEntity.KeyType.AFTER, newFirstId!!))
                                }
                                if (existingKeys.none { it.type == PostRemoteKeyEntity.KeyType.BEFORE }) {
                                    add(PostRemoteKeyEntity(PostRemoteKeyEntity.KeyType.BEFORE, body.minByOrNull { it.id }?.id!!))
                                }
                            }
                            postRemoteKeyDao.insert(newKeys)
                            postDao.insert(body.map(PostEntity::fromDto))
                        }
                    }
                    MediatorResult.Success(endOfPaginationReached = body.isEmpty())
                }
                LoadType.APPEND -> {
                    val earliestId = postRemoteKeyDao.getKey(PostRemoteKeyEntity.KeyType.BEFORE)?.key
                        ?: return MediatorResult.Success(false)
                    val response = apiService.getBefore(earliestId, state.config.pageSize)
                    if (!response.isSuccessful) throw ApiError(response.code(), response.message())
                    val body = response.body() ?: throw ApiError(response.code(), response.message())

                    appDb.withTransaction {
                        if (body.isNotEmpty()) {
                            postRemoteKeyDao.insert(
                                PostRemoteKeyEntity(PostRemoteKeyEntity.KeyType.BEFORE, body.minByOrNull { it.id }?.id!!)
                            )
                            postDao.insert(body.map(PostEntity::fromDto))
                        }
                    }
                    MediatorResult.Success(endOfPaginationReached = body.isEmpty())
                }
            }
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}