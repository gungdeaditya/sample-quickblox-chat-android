package com.gungde.example.samplequickbloxchat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.support.design.widget.Snackbar
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.gungde.example.samplequickbloxchat.adapter.AttachmentPreviewAdapter
import com.gungde.example.samplequickbloxchat.adapter.ChatAdapter
import com.gungde.example.samplequickbloxchat.utils.chat.ChatHelper
import com.gungde.example.samplequickbloxchat.utils.qb.*
import com.quickblox.chat.QBChatService
import com.quickblox.chat.model.QBAttachment
import com.quickblox.chat.model.QBChatDialog
import com.quickblox.chat.model.QBChatMessage
import com.quickblox.chat.model.QBDialogType
import com.quickblox.core.QBEntityCallback
import com.quickblox.core.exception.QBResponseException
import com.quickblox.sample.core.ui.dialog.ProgressDialogFragment
import com.quickblox.sample.core.utils.Toaster
import com.quickblox.sample.core.utils.imagepick.ImagePickHelper
import com.quickblox.sample.core.utils.imagepick.OnImagePickedListener
import com.quickblox.users.model.QBUser
import org.jivesoftware.smack.ConnectionListener
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import java.io.File
import java.util.*

import kotlinx.android.synthetic.main.activity_chat.*

class ChatActivity : BaseActivity(), OnImagePickedListener  {

    private val TAG = ChatActivity::class.java.simpleName
    private val REQUEST_CODE_ATTACHMENT = 721
    private val REQUEST_CODE_SELECT_PEOPLE = 752

    private val PROPERTY_SAVE_TO_HISTORY = "save_to_history"

    val EXTRA_DIALOG_ID = "dialogId"

    private var snackbar: Snackbar? = null

    private var chatAdapter: ChatAdapter? = null
    private var attachmentPreviewAdapter: AttachmentPreviewAdapter? = null
    private var chatConnectionListener: ConnectionListener? = null

    private var qbChatDialog: QBChatDialog? = null
    private var unShownMessages: ArrayList<QBChatMessage>? = null
    private var skipPagination = 0
    private var chatMessageListener: ChatMessageListener? = null

    fun startForResult(activity: Activity, code: Int, dialogId: QBChatDialog) {
        val intent = Intent(activity, ChatActivity::class.java)
        intent.putExtra(EXTRA_DIALOG_ID, dialogId)
        activity.startActivityForResult(intent, code)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        Log.v(TAG, "onCreate ChatActivity on Thread ID = " + Thread.currentThread().id)

        qbChatDialog = intent.getSerializableExtra(EXTRA_DIALOG_ID) as QBChatDialog

        Log.v(TAG, "deserialized dialog = " + qbChatDialog)
        qbChatDialog?.initForChat(QBChatService.getInstance())

        chatMessageListener = ChatMessageListener()

        qbChatDialog?.addMessageListener(chatMessageListener)

        initChatConnectionListener()

        initViews()
        initChat()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        if (qbChatDialog != null) {
            outState.putString(EXTRA_DIALOG_ID, qbChatDialog?.getDialogId())
        }
        super.onSaveInstanceState(outState, outPersistentState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (qbChatDialog == null) {
            qbChatDialog = QbDialogHolder.getInstance().getChatDialogById(savedInstanceState.getString(EXTRA_DIALOG_ID))
        }
    }

    override fun onResume() {
        super.onResume()
        ChatHelper.getInstance().addConnectionListener(chatConnectionListener)
    }

    override fun onPause() {
        super.onPause()
        ChatHelper.getInstance().removeConnectionListener(chatConnectionListener)
    }

    override fun onBackPressed() {
        releaseChat()
        sendDialogId()

        super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_chat, menu)

        val menuItemLeave = menu.findItem(R.id.menu_chat_action_leave)
        val menuItemAdd = menu.findItem(R.id.menu_chat_action_add)
        val menuItemDelete = menu.findItem(R.id.menu_chat_action_delete)
        if (qbChatDialog?.getType() == QBDialogType.PRIVATE) {
            menuItemLeave.isVisible = false
            menuItemAdd.isVisible = false
        } else {
            menuItemDelete.isVisible = false
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.menu_chat_action_info -> {
                ChatInfoActivity().start(this, qbChatDialog!!)
                return true
            }

            R.id.menu_chat_action_add -> {
                SelectUserActivity().startForResult(this, REQUEST_CODE_SELECT_PEOPLE, qbChatDialog!!)
                return true
            }

            R.id.menu_chat_action_leave -> {
                leaveGroupChat()
                return true
            }

            R.id.menu_chat_action_delete -> {
                deleteChat()
                return true
            }

            android.R.id.home -> {
                onBackPressed()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun sendDialogId() {
        val result = Intent()
        result.putExtra(EXTRA_DIALOG_ID, qbChatDialog?.getDialogId())
        setResult(RESULT_OK, result)
    }

    private fun leaveGroupChat() {
        ProgressDialogFragment.show(supportFragmentManager)
        ChatHelper.getInstance().exitFromDialog(qbChatDialog, object : QBEntityCallback<QBChatDialog> {
            override fun onSuccess(qbDialog: QBChatDialog, bundle: Bundle) {
                ProgressDialogFragment.hide(supportFragmentManager)
                QbDialogHolder.getInstance().deleteDialog(qbDialog)
                finish()
            }

            override fun onError(e: QBResponseException) {
                ProgressDialogFragment.hide(supportFragmentManager)
                showErrorSnackbar(R.string.error_leave_chat, e) { leaveGroupChat() }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_SELECT_PEOPLE) {
                val selectedUsers = data.getSerializableExtra(
                        SelectUserActivity().EXTRA_QB_USERS) as ArrayList<QBUser>
                updateDialog(selectedUsers)
            }
        }
    }

    override fun onImagePicked(requestCode: Int, file: File) {
        when (requestCode) {
            REQUEST_CODE_ATTACHMENT -> attachmentPreviewAdapter?.add(file)
        }
    }

    override fun onImagePickError(requestCode: Int, e: Exception) {
        showErrorSnackbar(0, e, null)
    }

    override fun onImagePickClosed(requestCode: Int) {
        // ignore
    }

    override fun getSnackbarAnchorView(): View {
        return findViewById(R.id.list_chat_messages)
    }

    fun onSendChatClick(view: View) {
        val totalAttachmentsCount = attachmentPreviewAdapter!!.getCount()
        val uploadedAttachments = attachmentPreviewAdapter!!.getUploadedAttachments()
        if (!uploadedAttachments.isEmpty()) {
            if (uploadedAttachments.size == totalAttachmentsCount) {
                for (attachment in uploadedAttachments) {
                    sendChatMessage(null, attachment)
                }
            } else {
                Toaster.shortToast(R.string.chat_wait_for_attachments_to_upload)
            }
        }

        val text = edit_chat_message.getText().toString().trim({ it <= ' ' })
        if (!TextUtils.isEmpty(text)) {
            sendChatMessage(text, null)
        }
    }

    fun onAttachmentsClick(view: View) {
        ImagePickHelper().pickAnImage(this, REQUEST_CODE_ATTACHMENT)
    }

    fun showMessage(message: QBChatMessage) {
        if (chatAdapter != null) {
            chatAdapter?.add(message)
            scrollMessageListDown()
        } else {
            if (unShownMessages == null) {
                unShownMessages = ArrayList()
            }
            unShownMessages?.add(message)
        }
    }

    private fun initViews() {
        actionBar.setDisplayHomeAsUpEnabled(true)
        attachmentPreviewAdapter = AttachmentPreviewAdapter(this,
                object : AttachmentPreviewAdapter.OnAttachmentCountChangedListener {
                    override fun onAttachmentCountChanged(count: Int) {
                        layout_attachment_preview_container.setVisibility(if (count == 0) View.GONE else View.VISIBLE)
                    }
                },
                object : AttachmentPreviewAdapter.OnAttachmentUploadErrorListener {
                    override fun onAttachmentUploadError(e: QBResponseException) {
                        showErrorSnackbar(0, e) { v -> onAttachmentsClick(v) }
                    }
                })
        adapter_view_attachment_preview.setAdapter(attachmentPreviewAdapter)
    }

    private fun sendChatMessage(text: String?, attachment: QBAttachment?) {
        val chatMessage = QBChatMessage()
        if (attachment != null) {
            chatMessage.addAttachment(attachment)
        } else {
            chatMessage.body = text
        }
        chatMessage.setProperty(PROPERTY_SAVE_TO_HISTORY, "1")
        chatMessage.dateSent = System.currentTimeMillis() / 1000
        chatMessage.isMarkable = true

        if (QBDialogType.PRIVATE != qbChatDialog?.getType() && !qbChatDialog!!.isJoined()) {
            Toaster.shortToast("You're still joining a group chat, please wait a bit")
            return
        }

        try {
            qbChatDialog?.sendMessage(chatMessage)

            if (QBDialogType.PRIVATE == qbChatDialog?.getType()) {
                showMessage(chatMessage)
            }

            if (attachment != null) {
                attachmentPreviewAdapter?.remove(attachment)
            } else {
                edit_chat_message.setText("")
            }
        } catch (e: SmackException.NotConnectedException) {
            Log.w(TAG, e)
            Toaster.shortToast("Can't send a message, You are not connected to chat")
        }

    }

    private fun initChat() {
        when (qbChatDialog?.getType()) {
            QBDialogType.GROUP, QBDialogType.PUBLIC_GROUP -> joinGroupChat()

            QBDialogType.PRIVATE -> loadDialogUsers()

            else -> {
                Toaster.shortToast(String.format("%s %s", getString(R.string.chat_unsupported_type), qbChatDialog!!.getType().name))
                finish()
            }
        }
    }

    private fun joinGroupChat() {
        progress_chat.setVisibility(View.VISIBLE)
        ChatHelper.getInstance().join(qbChatDialog, object : QBEntityCallback<Void> {
            override fun onSuccess(result: Void, b: Bundle) {
                if (snackbar != null) {
                    snackbar?.dismiss()
                }
                loadDialogUsers()
            }

            override fun onError(e: QBResponseException) {
                progress_chat.setVisibility(View.GONE)
                snackbar = showErrorSnackbar(R.string.connection_error, e, null)
            }
        })
    }

    private fun leaveGroupDialog() {
        try {
            ChatHelper.getInstance().leaveChatDialog(qbChatDialog)
        } catch (e: XMPPException) {
            Log.w(TAG, e)
        } catch (e: SmackException.NotConnectedException) {
            Log.w(TAG, e)
        }

    }

    private fun releaseChat() {
        qbChatDialog?.removeMessageListrener(chatMessageListener)
        if (QBDialogType.PRIVATE != qbChatDialog?.getType()) {
            leaveGroupDialog()
        }
    }

    private fun updateDialog(selectedUsers: ArrayList<QBUser>) {
        ChatHelper.getInstance().updateDialogUsers(qbChatDialog, selectedUsers,
                object : QBEntityCallback<QBChatDialog> {
                    override fun onSuccess(dialog: QBChatDialog, args: Bundle) {
                        qbChatDialog = dialog
                        loadDialogUsers()
                    }

                    override fun onError(e: QBResponseException) {
                        showErrorSnackbar(R.string.chat_info_add_people_error, e
                        ) { updateDialog(selectedUsers) }
                    }
                }
        )
    }

    private fun loadDialogUsers() {
        ChatHelper.getInstance().getUsersFromDialog(qbChatDialog, object : QBEntityCallback<ArrayList<QBUser>> {
            override fun onSuccess(users: ArrayList<QBUser>?, bundle: Bundle?) {
                setChatNameToActionBar()
                loadChatHistory()
            }

            override fun onError(e: QBResponseException) {
                showErrorSnackbar(R.string.chat_load_users_error, e
                ) { loadDialogUsers() }
            }
        })
    }

    private fun setChatNameToActionBar() {
        val chatName = QbDialogUtils.getDialogName(qbChatDialog)
        val ab = supportActionBar
        if (ab != null) {
            ab.setTitle(chatName)
            ab.setDisplayHomeAsUpEnabled(true)
            ab.setHomeButtonEnabled(true)
        }
    }

    private fun loadChatHistory() {
        ChatHelper.getInstance().loadChatHistory(qbChatDialog, skipPagination, object : QBEntityCallback<ArrayList<QBChatMessage>> {
            override fun onSuccess(messages: ArrayList<QBChatMessage>, args: Bundle) {
                // The newest messages should be in the end of list,
                // so we need to reverse list to show messages in the right order
                Collections.reverse(messages)
                if (chatAdapter == null) {
                    chatAdapter = ChatAdapter(this@ChatActivity, qbChatDialog, messages)
                    chatAdapter?.setPaginationHistoryListener(object : PaginationHistoryListener {
                        override fun downloadMore() {
                            loadChatHistory()
                        }
                    })
                    chatAdapter?.setOnItemInfoExpandedListener(object : ChatAdapter.OnItemInfoExpandedListener {
                        override fun onItemInfoExpanded(position: Int) {
                            if (isLastItem(position)) {
                                // HACK need to allow info textview visibility change so posting it via handler
                                runOnUiThread { list_chat_messages.setSelection(position) }
                            } else {
                                list_chat_messages.smoothScrollToPosition(position)
                            }
                        }

                        private fun isLastItem(position: Int): Boolean {
                            return position == chatAdapter?.getCount()!! - 1
                        }
                    })
                    if (unShownMessages != null && !unShownMessages?.isEmpty()!!) {
                        val chatList = chatAdapter?.getList()
                        for (message in unShownMessages!!) {
                            if (!chatList!!.contains(message)) {
                                chatAdapter?.add(message)
                            }
                        }
                    }
                    list_chat_messages.setAdapter(chatAdapter)
                    list_chat_messages.setAreHeadersSticky(false)
                    list_chat_messages.setDivider(null)
                } else {
                    chatAdapter?.addList(messages)
                    list_chat_messages.setSelection(messages.size)
                }
                progress_chat.setVisibility(View.GONE)
            }

            override fun onError(e: QBResponseException) {
                progress_chat.setVisibility(View.GONE)
                skipPagination -= ChatHelper.CHAT_HISTORY_ITEMS_PER_PAGE
                snackbar = showErrorSnackbar(R.string.connection_error, e, null)
            }
        })
        skipPagination += ChatHelper.CHAT_HISTORY_ITEMS_PER_PAGE
    }

    private fun scrollMessageListDown() {
        list_chat_messages.setSelection(list_chat_messages.getCount() - 1)
    }

    private fun deleteChat() {
        ChatHelper.getInstance().deleteDialog(qbChatDialog, object : QBEntityCallback<Void> {
            override fun onSuccess(aVoid: Void, bundle: Bundle) {
                setResult(RESULT_OK)
                finish()
            }

            override fun onError(e: QBResponseException) {
                showErrorSnackbar(R.string.dialogs_deletion_error, e
                ) { deleteChat() }
            }
        })
    }

    private fun initChatConnectionListener() {
        chatConnectionListener = object : VerboseQbChatConnectionListener(snackbarAnchorView) {
            override fun reconnectionSuccessful() {
                super.reconnectionSuccessful()
                skipPagination = 0
                when (qbChatDialog?.getType()) {
                    QBDialogType.GROUP -> {
                        chatAdapter = null
                        // Join active room if we're in Group Chat
                        runOnUiThread { joinGroupChat() }
                    }
                }
            }
        }
    }

    inner class ChatMessageListener : QbChatDialogMessageListenerImp() {
        override fun processMessage(s: String, qbChatMessage: QBChatMessage, integer: Int?) {
            showMessage(qbChatMessage)
        }
    }
}
