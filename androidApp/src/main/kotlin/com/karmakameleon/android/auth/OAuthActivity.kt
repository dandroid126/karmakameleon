package com.karmakameleon.android.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.karmakameleon.android.MainActivity
import com.karmakameleon.shared.data.api.AuthManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class OAuthActivity : ComponentActivity() {
    
    private val authManager: AuthManager by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        if (uri != null && uri.scheme == "karmakameleon" && uri.host == "oauth") {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            val error = uri.getQueryParameter("error")
            
            if (error != null) {
                navigateToMain()
                return
            }
            
            if (code != null) {
                lifecycleScope.launch {
                    try {
                        authManager.exchangeCodeForToken(code)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    navigateToMain()
                }
            } else {
                navigateToMain()
            }
        } else {
            navigateToMain()
        }
    }
    
    private fun navigateToMain() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(mainIntent)
        finish()
    }
}
