package com.gungde.example.samplequickbloxchat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import com.gungde.example.samplequickbloxchat.adapter.UsersAdapter
import com.gungde.example.samplequickbloxchat.utils.chat.ChatHelper
import com.quickblox.core.QBEntityCallback
import com.quickblox.core.exception.QBResponseException
import com.quickblox.sample.core.ui.activity.CoreBaseActivity
import com.quickblox.sample.core.ui.dialog.ProgressDialogFragment
import com.quickblox.sample.core.utils.ErrorUtils
import com.quickblox.sample.core.utils.SharedPrefsHelper
import com.quickblox.users.QBUsers
import com.quickblox.users.model.QBUser

import kotlinx.android.synthetic.main.activity_login.*
import java.util.ArrayList

class LoginActivity : CoreBaseActivity() {

    fun start(context: Context) {
        val intent = Intent(context, LoginActivity::class.java)
        context.startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        list_login_users.setOnItemClickListener(OnUserLoginItemClickListener())
        buildUsersList()
    }

    private fun buildUsersList() {
        val tags = ArrayList<String>()

        tags.add(App.getSampleConfigs().users_tag)

        QBUsers.getUsers(null).performAsync(object : QBEntityCallback<ArrayList<QBUser>> {
            override fun onSuccess(result: ArrayList<QBUser>, params: Bundle) {
                val adapter = UsersAdapter(this@LoginActivity, result)
                list_login_users.setAdapter(adapter)
            }

            override fun onError(e: QBResponseException) {
                ErrorUtils.showSnackbar(list_login_users, R.string.login_cant_obtain_users, e,
                        R.string.dlg_retry) { buildUsersList() }
                Log.e("TAGG",e.toString())
            }
        })
    }

    private fun login(user: QBUser) {
        ProgressDialogFragment.show(supportFragmentManager, R.string.dlg_login)

        ChatHelper.getInstance().login(user, object : QBEntityCallback<Void> {
            override fun onSuccess(result: Void?, bundle: Bundle?) {
                SharedPrefsHelper.getInstance().saveQbUser(user)
//                DialogsActivity.start(this@LoginActivity)
                MainActivity().start(this@LoginActivity)
                finish()

                ProgressDialogFragment.hide(supportFragmentManager)
            }

            override fun onError(e: QBResponseException) {
                ProgressDialogFragment.hide(supportFragmentManager)
                ErrorUtils.showSnackbar(list_login_users, R.string.login_chat_login_error, e,
                        R.string.dlg_retry) { login(user) }
            }
        })
    }

    private inner class OnUserLoginItemClickListener : AdapterView.OnItemClickListener {

        override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {

            val user = parent.getItemAtPosition(position) as QBUser
            // We use hardcoded password for all users for test purposes
            // Of course you shouldn't do that in your app
            user.password = App.getSampleConfigs().users_password
            login(user)
        }

    }
}
