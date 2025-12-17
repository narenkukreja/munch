package com.munch.reddit.feature.detail.recycler

import com.munch.reddit.domain.model.RedditPost
import com.munch.reddit.feature.detail.PostDetailViewModel

sealed interface PostDetailRow {
    val key: String

    data class Header(
        val post: RedditPost,
        val isBodyExpanded: Boolean
    ) : PostDetailRow {
        override val key: String = "header_${post.id}"
    }

    data class SortBar(
        val selectedSort: String,
        val sortOptions: List<String>
    ) : PostDetailRow {
        override val key: String = "sort_bar"
    }

    data object RefreshingComments : PostDetailRow {
        override val key: String = "refreshing_comments"
    }

    data object EmptyComments : PostDetailRow {
        override val key: String = "empty_comments"
    }

    data class CommentNode(val item: PostDetailViewModel.CommentListItem.CommentNode) : PostDetailRow {
        override val key: String = item.key
    }

    data class LoadMoreReplies(val item: PostDetailViewModel.CommentListItem.LoadMoreRepliesNode) : PostDetailRow {
        override val key: String = item.key
    }

    data class RemoteReplies(val item: PostDetailViewModel.CommentListItem.RemoteRepliesNode) : PostDetailRow {
        override val key: String = item.key
    }

    data class CollapsedHistory(val item: PostDetailViewModel.CommentListItem.CollapsedHistoryDivider) : PostDetailRow {
        override val key: String = item.key
    }

    data class LoadMoreComments(
        val isAppending: Boolean,
        val pendingRemoteReplyCount: Int
    ) : PostDetailRow {
        override val key: String = "load_more_comments"
    }

    data object EndSpacer : PostDetailRow {
        override val key: String = "end_spacer"
    }
}

