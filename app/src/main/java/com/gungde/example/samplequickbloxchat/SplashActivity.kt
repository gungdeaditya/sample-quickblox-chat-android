package com.gungde.example.samplequickbloxchat

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gungde.example.samplequickbloxchat.utils.chat.ChatHelper
import com.quickblox.auth.session.QBSessionManager
import com.quickblox.core.QBEntityCallback
import com.quickblox.core.exception.QBResponseException
import com.quickblox.sample.core.ui.activity.CoreSplashActivity
import com.quickblox.sample.core.ui.dialog.ProgressDialogFragment
import com.quickblox.sample.core.utils.SharedPrefsHelper
import com.quickblox.users.model.QBUser

class SplashActivity : CoreSplashActivity() {

    private val TAG = SplashActivity::class.java.simpleName
    private val SPLASH_DELAY = 1500
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        if (checkConfigsWithSnackebarError()) {
            proceedToTheNextActivityWithDelay()
        }
    }

    override fun proceedToTheNextActivity() {
        if (checkSignIn()) {
            restoreChatSession()
        } else {
            LoginActivity().start(this)
            finish()
        }
    }

    override fun sampleConfigIsCorrect(): Boolean {
        var result = super.sampleConfigIsCorrect()
        result = result && App.getSampleConfigs() != null
        return result
    }

    private fun restoreChatSession() {
        if (ChatHelper.getInstance().isLogged) {
            MainActivity().start(this)
            finish()
        } else {
            val currentUser = getUserFromSession()
            loginToChat(currentUser)
        }
    }

    private fun getUserFromSession(): QBUser {
        val user = SharedPrefsHelper.getInstance().qbUser
        user!!.id = QBSessionManager.getInstance().sessionParameters.userId
        return user
    }

    override fun checkSignIn(): Boolean {
        return SharedPrefsHelper.getInstance().hasQbUser()
    }

    private fun loginToChat(user: QBUser) {
        ProgressDialogFragment.show(supportFragmentManager, R.string.dlg_restoring_chat_session)

        ChatHelper.getInstance().loginToChat(user, object : QBEntityCallback<Void> {
            override fun onSuccess(result: Void?, bundle: Bundle) {
                Log.v(TAG, "Chat login onSuccess()")
                ProgressDialogFragment.hide(supportFragmentManager)
                MainActivity().start(this@SplashActivity)
                finish()
            }

            override fun onError(e: QBResponseException) {
                ProgressDialogFragment.hide(supportFragmentManager)
                Log.w(TAG, "Chat login onError(): " + e)
                showSnackbarError(findViewById(R.id.layout_root), R.string.error_recreate_session, e
                ) { loginToChat(user) }
            }
        })
    }
}
