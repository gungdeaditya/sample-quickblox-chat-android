package com.gungde.example.samplequickbloxchat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.gungde.example.samplequickbloxchat.adapter.UsersAdapter
import com.gungde.example.samplequickbloxchat.utils.qb.QbUsersHolder
import com.quickblox.chat.model.QBChatDialog

import kotlinx.android.synthetic.main.activity_chat_info.*

class ChatInfoActivity : BaseActivity() {

    private val EXTRA_DIALOG = "dialog"
    private var qbDialog: QBChatDialog? = null

    fun start(context: Context, qbDialog: QBChatDialog) {
        val intent = Intent(context, ChatInfoActivity::class.java)
        intent.putExtra(EXTRA_DIALOG, qbDialog)
        context.startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_info)

        qbDialog = intent.getSerializableExtra(EXTRA_DIALOG) as QBChatDialog

        actionBar.setDisplayHomeAsUpEnabled(true)

        buildUserList()
    }

    override fun getSnackbarAnchorView(): View {
        return list_chat_info_users
    }

    private fun buildUserList() {
        val userIds = qbDialog?.getOccupants()
        val users = QbUsersHolder.getInstance().getUsersByIds(userIds)

        val adapter = UsersAdapter(this, users)
        list_chat_info_users.setAdapter(adapter)
    }
}
