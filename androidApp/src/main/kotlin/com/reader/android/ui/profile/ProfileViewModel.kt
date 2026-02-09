package com.reader.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileUiState(
    val account: Account? = null,
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val savedPosts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingPosts: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val selectedTab: ProfileTab = ProfileTab.POSTS,
    val postsAfter: String? = null,
    val savedAfter: String? = null,
    val authUrl: String? = null
)

enum class ProfileTab {
    POSTS, SAVED, ABOUT
}

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
                if (isLoggedIn) {
                    loadCurrentUser()
                }
            }
        }
        
        viewModelScope.launch {
            userRepository.currentAccount.collect { account ->
                _uiState.update { it.copy(account = account) }
                if (account != null) {
                    loadUserPosts(account.name)
                }
            }
        }
    }

    fun loadCurrentUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = userRepository.loadCurrentUser()
            result.fold(
                onSuccess = { account ->
                    _uiState.update { it.copy(account = account, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    fun loadUser(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = userRepository.getUser(username)
            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(user = user, isLoading = false) }
                    loadUserPosts(username)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    private fun loadUserPosts(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPosts = true) }
            val result = userRepository.getUserPosts(username)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            posts = listing.items,
                            postsAfter = listing.after,
                            isLoadingPosts = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingPosts = false) }
                }
            )
        }
    }

    fun loadSavedPosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPosts = true) }
            val result = userRepository.getSavedPosts()
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            savedPosts = listing.items,
                            savedAfter = listing.after,
                            isLoadingPosts = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingPosts = false) }
                }
            )
        }
    }

    fun setSelectedTab(tab: ProfileTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == ProfileTab.SAVED && _uiState.value.savedPosts.isEmpty()) {
            loadSavedPosts()
        }
    }

    fun initiateLogin() {
        val state = generateState()
        val authUrl = userRepository.getAuthorizationUrl(state)
        _uiState.update { it.copy(authUrl = authUrl) }
    }

    fun clearAuthUrl() {
        _uiState.update { it.copy(authUrl = null) }
    }

    fun logout() {
        userRepository.logout()
        _uiState.update {
            ProfileUiState(isLoggedIn = false)
        }
    }

    fun vote(post: Post, direction: Int) {
        viewModelScope.launch {
            val result = postRepository.vote(post, direction)
            result.onSuccess { updatedPost ->
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.map { if (it.id == post.id) updatedPost else it },
                        savedPosts = state.savedPosts.map { if (it.id == post.id) updatedPost else it }
                    )
                }
            }
        }
    }

    fun save(post: Post) {
        viewModelScope.launch {
            val result = postRepository.save(post)
            result.onSuccess { updatedPost ->
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.map { if (it.id == post.id) updatedPost else it },
                        savedPosts = if (updatedPost.isSaved) {
                            state.savedPosts.map { if (it.id == post.id) updatedPost else it }
                        } else {
                            state.savedPosts.filter { it.id != post.id }
                        }
                    )
                }
            }
        }
    }

    private fun generateState(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
