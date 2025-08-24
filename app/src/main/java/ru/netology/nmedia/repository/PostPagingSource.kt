package ru.netology.nmedia.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import retrofit2.HttpException
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dto.Post
import java.io.IOException

class PostPagingSource(
    private val apiService: ApiService
) : PagingSource<Long, Post>() {
    override fun getRefreshKey(state: PagingState<Long, Post>): Long? = null

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Post> {
        try {
            val result = when (params) {
                is LoadParams.Append<*> -> {
                    apiService.getLatest(params.loadSize)
                }

                is LoadParams.Prepend<*> -> {
                    apiService.getBefore(id = params.key, count = params.loadSize)
                }

                is LoadParams.Refresh<*> -> return LoadResult.Page(
                    data = emptyList(), nextKey = null, prevKey = params.key
                )
            }
            if (!result.isSuccessful) {
                throw HttpException(result)
            }

            val data = result.body().orEmpty()
            return LoadResult.Page(data = data, prevKey = params.key, data.lastOrNull()?.id)
        } catch (e: IOException) {
            return LoadResult.Error(e)
        }
    }
}